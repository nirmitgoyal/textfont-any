package glyph.server;

import java.nio.file.Path;
import java.util.Map;

/** All knobs come from environment variables so there is nothing to edit between runs. */
record ServerConfig(String host, int port, Path fontDir, int maxConcurrentRenders, long queueWaitMs,
                    long cacheBytes, long maxPixels) {

    static ServerConfig fromEnv(Map<String, String> env) {
        int cores = Runtime.getRuntime().availableProcessors();
        return new ServerConfig(
                env.getOrDefault("HOST", "127.0.0.1"),
                intVar(env, "PORT", 8080, 0, 65535),
                Path.of(env.getOrDefault("FONT_DIR", "fonts")),
                intVar(env, "MAX_CONCURRENT_RENDERS", cores, 1, 1024),
                intVar(env, "QUEUE_WAIT_MS", 500, 0, 60_000),
                intVar(env, "CACHE_MB", 64, 0, 4096) * 1024L * 1024L,
                intVar(env, "MAX_PIXELS", 4_000_000, 1, 64_000_000));
    }

    private static int intVar(Map<String, String> env, String name, int def, int min, int max) {
        String v = env.get(name);
        if (v == null || v.isBlank()) return def;
        try {
            int n = Integer.parseInt(v.trim());
            if (n < min || n > max) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer in [" + min + ", " + max + "]: " + v);
        }
    }
}
