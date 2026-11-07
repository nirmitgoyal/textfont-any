package glyph.server;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ServerTest {
    static Server server;
    static String base;
    static final HttpClient http = HttpClient.newHttpClient();

    static ServerConfig cfg(int renders, long waitMs) {
        return new ServerConfig("127.0.0.1", 0, Path.of("fonts"), renders, waitMs, 8L << 20, 4_000_000);
    }

    @BeforeAll static void up() throws Exception {
        server = Server.start(cfg(4, 2000));
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterAll static void down() { server.close(); }

    static HttpResponse<byte[]> get(String b, String pathAndQuery, String... headers) throws Exception {
        HttpRequest.Builder r = HttpRequest.newBuilder(URI.create(b + pathAndQuery));
        for (int i = 0; i < headers.length; i += 2) r.header(headers[i], headers[i + 1]);
        return http.send(r.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }

    static String body(HttpResponse<byte[]> r) { return new String(r.body(), StandardCharsets.UTF_8); }

    @Test void rendersPngForEveryFont() throws Exception {
        for (String f : List.of("DejaVuSans", "DejaVuSerif", "DejaVuSansMono")) {
            var r = get(base, "/v1/render?font=" + f + "&text=" + enc("Hello, glyph! é ✓"));
            assertEquals(200, r.statusCode(), body(r));
            assertEquals("image/png", r.headers().firstValue("Content-Type").orElseThrow());
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
            assertNotNull(img);
            assertTrue(img.getWidth() > 50);
        }
    }

    @Test void colorsAndSizeAreHonoured() throws Exception {
        var r = get(base, "/v1/render?font=DejaVuSans&text=I&size=100&padding=0&ink=ff0000&bg=00000000");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(r.body()));
        boolean red = false, clear = false;
        for (int y = 0; y < img.getHeight(); y++)
            for (int x = 0; x < img.getWidth(); x++) {
                int p = img.getRGB(x, y);
                if (p == 0xFFFF0000) red = true;
                if (p == 0) clear = true;
            }
        assertTrue(red && clear);
    }

    @Test void etagGives304WithoutBody() throws Exception {
        String q = "/v1/render?font=DejaVuSans&text=etag";
        var first = get(base, q);
        String tag = first.headers().firstValue("ETag").orElseThrow();
        var second = get(base, q, "If-None-Match", tag);
        assertEquals(304, second.statusCode());
        assertEquals(0, second.body().length);
        assertEquals(200, get(base, q, "If-None-Match", "\"other\"").statusCode());
        var other = get(base, "/v1/render?font=DejaVuSans&text=etag2");
        assertNotEquals(tag, other.headers().firstValue("ETag").orElseThrow());
    }

    @Test void repeatRequestIsServedFromCacheWithIdenticalBytes() throws Exception {
        String q = "/v1/render?font=DejaVuSerif&text=cache-me-" + System.nanoTime();
        var a = get(base, q);
        var b = get(base, q);
        assertArrayEquals(a.body(), b.body());
        assertTrue(body(get(base, "/metrics")).contains("glyph_cache_hits_total"));
        assertFalse(body(get(base, "/metrics")).contains("glyph_cache_hits_total 0\n"));
    }

    @Test void badInputIs4xxJsonWithStableCodes() throws Exception {
        Map<String, String[]> cases = Map.ofEntries(
                Map.entry("/v1/render?font=DejaVuSans", new String[] {"400", "missing_param"}),
                Map.entry("/v1/render?text=x", new String[] {"400", "missing_param"}),
                Map.entry("/v1/render?font=Nope&text=x", new String[] {"404", "unknown_font"}),
                Map.entry("/v1/render?font=..%2Fpom&text=x", new String[] {"404", "unknown_font"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&size=3", new String[] {"400", "invalid_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&size=abc", new String[] {"400", "invalid_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&ink=red", new String[] {"400", "invalid_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&padding=-1", new String[] {"400", "invalid_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&colour=1", new String[] {"400", "unknown_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=x&text=y", new String[] {"400", "duplicate_param"}),
                Map.entry("/v1/render?font=DejaVuSans&text=%FF", new String[] {"400", "invalid_encoding"}),
                Map.entry("/v1/render?font=DejaVuSans&text=a%0Ab", new String[] {"400", "invalid_text"}),
                Map.entry("/v1/render?font=DejaVuSans&text=" + "a".repeat(1001), new String[] {"400", "invalid_text"}),
                Map.entry("/v1/render?font=DejaVuSans&size=512&padding=256&text=" + "W".repeat(40),
                        new String[] {"400", "canvas_too_large"}),
                Map.entry("/nope", new String[] {"404", "not_found"}));
        for (var e : cases.entrySet()) {
            var r = get(base, e.getKey());
            String b = body(r);
            assertEquals(Integer.parseInt(e.getValue()[0]), r.statusCode(), e.getKey() + " " + b);
            assertTrue(b.contains("\"code\":\"" + e.getValue()[1] + "\""), e.getKey() + " " + b);
            assertEquals("application/json", r.headers().firstValue("Content-Type").orElseThrow());
            assertEquals("no-store", r.headers().firstValue("Cache-Control").orElseThrow());
            assertTrue(r.headers().firstValue("X-Request-Id").isPresent());
        }
    }

    @Test void malformedPercentEscapeIsRejectedNotDecodedLeniently() throws Exception {
        for (String bad : List.of("%4", "%", "%zz", "a%")) {
            var e = assertThrows(ApiException.class, () -> RenderParams.decode(bad), bad);
            assertEquals("invalid_encoding", e.code);
        }
        // A raw socket can send what HttpClient refuses to; the server must answer 400, not hang or 500.
        try (var sock = new java.net.Socket("127.0.0.1", server.port())) {
            sock.setSoTimeout(3000);
            sock.getOutputStream().write(
                    "GET /v1/render?font=DejaVuSans&text=%4 HTTP/1.1\r\nHost: x\r\nConnection: close\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
            String line = new java.io.BufferedReader(new java.io.InputStreamReader(sock.getInputStream())).readLine();
            assertTrue(line.contains(" 400 "), line);
        }
    }

    @Test void nonGetIs405() throws Exception {
        var req = HttpRequest.newBuilder(URI.create(base + "/v1/render?font=DejaVuSans&text=x"))
                .POST(HttpRequest.BodyPublishers.ofString("x")).build();
        var r = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(405, r.statusCode());
        assertEquals("GET", r.headers().firstValue("Allow").orElseThrow());
    }

    @Test void requestIdIsEchoedOrReplaced() throws Exception {
        assertEquals("abc-123", get(base, "/healthz", "X-Request-Id", "abc-123")
                .headers().firstValue("X-Request-Id").orElseThrow());
        String gen = get(base, "/healthz", "X-Request-Id", "bad id\"")
                .headers().firstValue("X-Request-Id").orElseThrow();
        assertEquals(36, gen.length());
    }

    @Test void opsEndpoints() throws Exception {
        assertEquals(200, get(base, "/healthz").statusCode());
        assertTrue(body(get(base, "/readyz")).contains("ready"));
        assertTrue(body(get(base, "/v1/fonts")).contains("\"DejaVuSansMono\""));
        String m = body(get(base, "/metrics"));
        assertTrue(m.contains("glyph_http_requests_total{route=\"render\",status=\"200\"}"));
        assertTrue(m.contains("glyph_render_duration_seconds_count"));
    }

    @Test void shedsLoadWith503AndRetryAfterInsteadOfQueueingForever() throws Exception {
        try (Server s = Server.start(cfg(1, 0))) {
            String b = "http://127.0.0.1:" + s.port();
            List<CompletableFuture<HttpResponse<byte[]>>> fs = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                var req = HttpRequest.newBuilder(URI.create(b + "/v1/render?font=DejaVuSans&size=512&text="
                        + enc("WWWWWW" + i))).build();
                fs.add(http.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()));
            }
            int ok = 0, shed = 0;
            for (var f : fs) {
                var r = f.get();
                if (r.statusCode() == 200) ok++;
                else {
                    assertEquals(503, r.statusCode());
                    assertEquals("1", r.headers().firstValue("Retry-After").orElseThrow());
                    shed++;
                }
            }
            assertTrue(ok >= 1, "some must succeed");
            assertTrue(shed >= 1, "some must be shed");
        }
    }

    @Test void startupFailsFastOnEmptyFontDir() {
        var bad = new ServerConfig("127.0.0.1", 0, Path.of("src"), 1, 0, 0, 1000);
        assertThrows(java.io.IOException.class, () -> Server.start(bad));
    }
}
