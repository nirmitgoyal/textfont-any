package glyph.server;

import glyph.RenderOptions;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Validated render request. Record equality makes it the cache key. */
record RenderParams(String font, String text, int size, int padding, int ink, int bg) {
    static final int MAX_TEXT_CODE_POINTS = 1000;
    static final int MAX_PADDING = 256;
    private static final Set<String> KNOWN = Set.of("font", "text", "size", "padding", "ink", "bg");

    RenderOptions options() {
        return new RenderOptions(size, padding, ink, bg);
    }

    static RenderParams parse(String rawQuery, FontRegistry fonts) {
        Map<String, String> q = parseQuery(rawQuery);
        for (String k : q.keySet()) {
            if (!KNOWN.contains(k)) throw bad("unknown_param", "unknown parameter: " + k);
        }
        String font = require(q, "font");
        if (fonts.get(font) == null) {
            throw new ApiException(404, "unknown_font", "no such font: " + font + " (see /v1/fonts)");
        }
        String text = require(q, "text");
        int cps = text.codePointCount(0, text.length());
        if (cps > MAX_TEXT_CODE_POINTS) throw bad("invalid_text", "text exceeds " + MAX_TEXT_CODE_POINTS + " code points");
        if (text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) throw bad("invalid_text", "single line only");
        int size = intParam(q, "size", 64, 4, 512);
        int padding = intParam(q, "padding", 8, 0, MAX_PADDING);
        int ink = colorParam(q, "ink", 0xFF000000);
        int bg = colorParam(q, "bg", 0xFFFFFFFF);
        return new RenderParams(font, text, size, padding, ink, bg);
    }

    private static String require(Map<String, String> q, String k) {
        String v = q.get(k);
        if (v == null || v.isEmpty()) throw bad("missing_param", "missing required parameter: " + k);
        return v;
    }

    private static int intParam(Map<String, String> q, String k, int def, int min, int max) {
        String v = q.get(k);
        if (v == null) return def;
        try {
            int n = Integer.parseInt(v);
            if (n >= min && n <= max) return n;
        } catch (NumberFormatException ignored) {
            // fall through
        }
        throw bad("invalid_param", k + " must be an integer in [" + min + ", " + max + "]");
    }

    /** RRGGBB or RRGGBBAA hex, no '#'. */
    private static int colorParam(Map<String, String> q, String k, int def) {
        String v = q.get(k);
        if (v == null) return def;
        if (!v.matches("[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) {
            throw bad("invalid_param", k + " must be RRGGBB or RRGGBBAA hex");
        }
        int rgb = Integer.parseInt(v.substring(0, 6), 16);
        int a = v.length() == 8 ? Integer.parseInt(v.substring(6), 16) : 0xFF;
        return (a << 24) | rgb;
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null || raw.isEmpty()) return out;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = decode(eq < 0 ? pair : pair.substring(0, eq));
            String v = eq < 0 ? "" : decode(pair.substring(eq + 1));
            if (out.put(k, v) != null) throw bad("duplicate_param", "parameter given twice: " + k);
        }
        return out;
    }

    /** Strict percent-decoding: malformed escapes or invalid UTF-8 are errors, never U+FFFD. */
    static String decode(String s) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '+') {
                b.write(' ');
            } else if (c == '%') {
                if (i + 2 >= s.length()) {
                    throw bad("invalid_encoding", "truncated percent escape");
                }
                int hi = Character.digit(s.charAt(i + 1), 16), lo = Character.digit(s.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) throw bad("invalid_encoding", "malformed percent escape");
                b.write(hi * 16 + lo);
                i += 2;
            } else if (c < 0x80) {
                b.write(c);
            } else {
                throw bad("invalid_encoding", "non-ASCII byte in query; percent-encode as UTF-8");
            }
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(b.toByteArray())).toString();
        } catch (CharacterCodingException e) {
            throw bad("invalid_encoding", "query is not valid UTF-8");
        }
    }

    private static ApiException bad(String code, String msg) {
        return new ApiException(400, code, msg);
    }
}
