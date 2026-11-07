package glyph.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import glyph.CanvasTooLargeException;
import glyph.GlyphRenderer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Routing, load shedding, caching and error mapping. One handler, five routes. */
final class Api implements HttpHandler {
    /** Bump when rasterizer or layout output changes, so ETags from older builds stop matching. */
    private static final String RENDER_VERSION = "r1";
    private static final Pattern REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final ServerConfig cfg;
    private final FontRegistry fonts;
    private final RenderCache cache;
    private final Metrics metrics;
    private final Semaphore permits;
    private volatile boolean draining;

    Api(ServerConfig cfg, FontRegistry fonts, RenderCache cache, Metrics metrics) {
        this.cfg = cfg;
        this.fonts = fonts;
        this.cache = cache;
        this.metrics = metrics;
        this.permits = new Semaphore(cfg.maxConcurrentRenders());
    }

    void setDraining(boolean d) {
        draining = d;
    }

    @Override
    public void handle(HttpExchange ex) {
        long t0 = System.nanoTime();
        String rid = requestId(ex);
        ex.getResponseHeaders().set("X-Request-Id", rid);
        String route = route(ex.getRequestURI().getPath());
        int status = 500;
        long size = 0;
        String note = "";
        try {
            try {
                if ("other".equals(route)) throw new ApiException(404, "not_found", "no such route");
                if (!"GET".equals(ex.getRequestMethod())) {
                    throw new ApiException(405, "method_not_allowed", "use GET", Map.of("Allow", "GET"));
                }
                Result r = switch (route) {
                    case "render" -> render(ex);
                    case "fonts" -> json(200, fontsJson());
                    case "health" -> json(200, "{\"status\":\"ok\"}");
                    case "ready" -> draining
                            ? json(503, "{\"status\":\"draining\"}")
                            : json(200, "{\"status\":\"ready\",\"fonts\":" + fonts.size() + "}");
                    default -> new Result(200, "text/plain; version=0.0.4; charset=utf-8",
                            metrics.expose().getBytes(StandardCharsets.UTF_8), Map.of());
                };
                status = r.status;
                size = r.body.length;
                note = r.note;
                send(ex, r);
            } catch (ApiException e) {
                status = e.status;
                note = e.code;
                Result r = new Result(e.status, "application/json", errorJson(e.code, e.getMessage(), rid), e.headers);
                size = r.body.length;
                send(ex, r);
            }
        } catch (Throwable t) {
            // Never leak internals; the id ties the client's report to this log line.
            status = 500;
            note = "internal";
            System.err.println("request " + rid + " failed: " + t);
            t.printStackTrace();
            try {
                send(ex, new Result(500, "application/json",
                        errorJson("internal", "internal error", rid), Map.of()));
            } catch (Throwable ignored) {
                // client is gone
            }
        } finally {
            double secs = (System.nanoTime() - t0) / 1e9;
            metrics.request(route, status, secs);
            if (!"health".equals(route) && !"ready".equals(route) && !"metrics".equals(route)) {
                // Request text is deliberately not logged: it is caller data.
                System.out.printf("ts=%s id=%s route=%s status=%d bytes=%d dur_ms=%.1f note=%s%n",
                        java.time.Instant.now(), rid, route, status, size, secs * 1000, note.isEmpty() ? "-" : note);
            }
            ex.close();
        }
    }

    private Result render(HttpExchange ex) throws IOException {
        RenderParams p = RenderParams.parse(ex.getRequestURI().getRawQuery(), fonts);
        String etag = etag(p);
        Map<String, String> hdr = Map.of("ETag", etag, "Cache-Control", "public, max-age=3600");
        if (etagMatches(ex.getRequestHeaders().getFirst("If-None-Match"), etag)) {
            return new Result(304, null, new byte[0], hdr, "not_modified");
        }
        byte[] png = cache.get(p);
        String note = "hit";
        if (png == null) {
            metrics.cacheMisses.increment();
            png = renderGated(p);
            cache.put(p, png);
            note = "miss";
        } else {
            metrics.cacheHits.increment();
        }
        return new Result(200, "image/png", png, hdr, note);
    }

    /** Bounded concurrency: wait briefly for a slot, then shed with 503 instead of queueing without limit. */
    private byte[] renderGated(RenderParams p) throws IOException {
        try {
            if (!permits.tryAcquire(cfg.queueWaitMs(), TimeUnit.MILLISECONDS)) {
                metrics.shed.increment();
                throw new ApiException(503, "overloaded", "too many renders in progress; retry shortly",
                        Map.of("Retry-After", "1"));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(503, "overloaded", "interrupted", Map.of("Retry-After", "1"));
        }
        long t0 = System.nanoTime();
        metrics.rendersInFlight.incrementAndGet();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(16 * 1024);
            GlyphRenderer.renderToPng(fonts.get(p.font()).face(), p.text(), p.options(), cfg.maxPixels(), out);
            return out.toByteArray();
        } catch (CanvasTooLargeException e) {
            throw new ApiException(400, "canvas_too_large",
                    "rendered image would exceed " + cfg.maxPixels() + " pixels; reduce size, padding or text");
        } finally {
            metrics.rendersInFlight.decrementAndGet();
            permits.release();
            metrics.render((System.nanoTime() - t0) / 1e9);
        }
    }

    private String etag(RenderParams p) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String canon = RENDER_VERSION + '\0' + fonts.get(p.font()).digest() + '\0' + p.size() + '\0'
                    + p.padding() + '\0' + p.ink() + '\0' + p.bg() + '\0' + p.text();
            byte[] d = md.digest(canon.getBytes(StandardCharsets.UTF_8));
            return '"' + Base64.getUrlEncoder().withoutPadding().encodeToString(d).substring(0, 22) + '"';
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean etagMatches(String header, String etag) {
        if (header == null) return false;
        for (String t : header.split(",")) {
            t = t.trim();
            if (t.equals("*")) return true;
            if (t.startsWith("W/")) t = t.substring(2);
            if (t.equals(etag)) return true;
        }
        return false;
    }

    private String fontsJson() {
        StringBuilder sb = new StringBuilder("{\"fonts\":[");
        List<String> names = fonts.names();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(names.get(i)).append('"'); // names are [A-Za-z0-9._-] only
        }
        return sb.append("]}").toString();
    }

    private static byte[] errorJson(String code, String msg, String rid) {
        return ("{\"error\":{\"code\":\"" + esc(code) + "\",\"message\":\"" + esc(msg)
                + "\",\"request_id\":\"" + esc(rid) + "\"}}").getBytes(StandardCharsets.UTF_8);
    }

    private static String esc(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static String route(String path) {
        return switch (path) {
            case "/v1/render" -> "render";
            case "/v1/fonts" -> "fonts";
            case "/healthz" -> "health";
            case "/readyz" -> "ready";
            case "/metrics" -> "metrics";
            default -> "other";
        };
    }

    private static String requestId(HttpExchange ex) {
        String in = ex.getRequestHeaders().getFirst("X-Request-Id");
        return in != null && REQUEST_ID.matcher(in).matches() ? in : UUID.randomUUID().toString();
    }

    private record Result(int status, String type, byte[] body, Map<String, String> headers, String note) {
        Result(int status, String type, byte[] body, Map<String, String> headers) {
            this(status, type, body, headers, "");
        }
    }

    private static Result json(int status, String body) {
        return new Result(status, "application/json", body.getBytes(StandardCharsets.UTF_8), Map.of());
    }

    private static void send(HttpExchange ex, Result r) throws IOException {
        var h = ex.getResponseHeaders();
        if (r.type != null) h.set("Content-Type", r.type);
        r.headers.forEach(h::set);
        if (r.status >= 400 || !r.headers.containsKey("Cache-Control")) h.set("Cache-Control", "no-store");
        if (r.status == 304) {
            ex.sendResponseHeaders(304, -1);
            return;
        }
        ex.sendResponseHeaders(r.status, r.body.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(r.body);
        }
    }
}
