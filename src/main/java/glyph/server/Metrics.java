package glyph.server;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/** Minimal Prometheus text exposition. Labels are drawn from bounded sets only (route, status). */
final class Metrics {
    private static final double[] BUCKETS = {0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5, 5};

    private static final class Histogram {
        final LongAdder[] buckets = new LongAdder[BUCKETS.length];
        final LongAdder count = new LongAdder();
        final DoubleAdder sum = new DoubleAdder();
        Histogram() { for (int i = 0; i < buckets.length; i++) buckets[i] = new LongAdder(); }
        void observe(double s) {
            for (int i = 0; i < BUCKETS.length; i++) if (s <= BUCKETS[i]) buckets[i].increment();
            count.increment();
            sum.add(s);
        }
    }

    private final Map<String, LongAdder> requests = new ConcurrentHashMap<>();
    private final Map<String, Histogram> latency = new ConcurrentHashMap<>();
    private final Histogram renderTime = new Histogram();
    final LongAdder cacheHits = new LongAdder();
    final LongAdder cacheMisses = new LongAdder();
    final LongAdder shed = new LongAdder();
    final AtomicInteger rendersInFlight = new AtomicInteger();
    private final Supplier<Long> cacheBytes;
    private final Supplier<Integer> cacheEntries;

    Metrics(Supplier<Long> cacheBytes, Supplier<Integer> cacheEntries) {
        this.cacheBytes = cacheBytes;
        this.cacheEntries = cacheEntries;
    }

    void request(String route, int status, double seconds) {
        requests.computeIfAbsent(route + "|" + status, k -> new LongAdder()).increment();
        latency.computeIfAbsent(route, k -> new Histogram()).observe(seconds);
    }

    void render(double seconds) {
        renderTime.observe(seconds);
    }

    String expose() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("# TYPE glyph_http_requests_total counter\n");
        for (var e : new TreeMap<>(requests).entrySet()) {
            String[] p = e.getKey().split("\\|");
            sb.append("glyph_http_requests_total{route=\"").append(p[0]).append("\",status=\"").append(p[1])
                    .append("\"} ").append(e.getValue().sum()).append('\n');
        }
        sb.append("# TYPE glyph_http_request_duration_seconds histogram\n");
        for (var e : new TreeMap<>(latency).entrySet()) {
            hist(sb, "glyph_http_request_duration_seconds", "route=\"" + e.getKey() + "\",", e.getValue());
        }
        sb.append("# TYPE glyph_render_duration_seconds histogram\n");
        hist(sb, "glyph_render_duration_seconds", "", renderTime);
        counter(sb, "glyph_cache_hits_total", cacheHits.sum());
        counter(sb, "glyph_cache_misses_total", cacheMisses.sum());
        counter(sb, "glyph_render_shed_total", shed.sum());
        gauge(sb, "glyph_renders_in_flight", rendersInFlight.get());
        gauge(sb, "glyph_cache_bytes", cacheBytes.get());
        gauge(sb, "glyph_cache_entries", cacheEntries.get());
        Runtime rt = Runtime.getRuntime();
        gauge(sb, "glyph_jvm_heap_used_bytes", rt.totalMemory() - rt.freeMemory());
        return sb.toString();
    }

    private static void hist(StringBuilder sb, String name, String labels, Histogram h) {
        for (int i = 0; i < BUCKETS.length; i++) {
            sb.append(name).append("_bucket{").append(labels).append("le=\"").append(BUCKETS[i]).append("\"} ")
                    .append(h.buckets[i].sum()).append('\n');
        }
        String bare = labels.isEmpty() ? "" : "{" + labels.substring(0, labels.length() - 1) + "}";
        sb.append(name).append("_bucket{").append(labels).append("le=\"+Inf\"} ").append(h.count.sum()).append('\n');
        sb.append(name).append("_sum").append(bare).append(' ').append(h.sum.sum()).append('\n');
        sb.append(name).append("_count").append(bare).append(' ').append(h.count.sum()).append('\n');
    }

    private static void counter(StringBuilder sb, String n, long v) {
        sb.append("# TYPE ").append(n).append(" counter\n").append(n).append(' ').append(v).append('\n');
    }

    private static void gauge(StringBuilder sb, String n, long v) {
        sb.append("# TYPE ").append(n).append(" gauge\n").append(n).append(' ').append(v).append('\n');
    }
}
