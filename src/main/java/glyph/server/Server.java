package glyph.server;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** HTTP front end for the renderer. Zero extra dependencies: JDK HttpServer on virtual threads. */
public final class Server implements AutoCloseable {
    private final HttpServer http;
    private final ExecutorService pool;
    private final Api api;

    private Server(HttpServer http, ExecutorService pool, Api api) {
        this.http = http;
        this.pool = pool;
        this.api = api;
    }

    public static void main(String[] args) throws Exception {
        ServerConfig cfg = ServerConfig.fromEnv(System.getenv());
        Server s = start(cfg);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("shutting down: draining in-flight requests");
            s.close();
        }));
        System.out.println("glyph listening on http://" + cfg.host() + ":" + s.port()
                + " fonts=" + cfg.fontDir() + " renders=" + cfg.maxConcurrentRenders()
                + " cache_mb=" + cfg.cacheBytes() / (1024 * 1024) + " max_pixels=" + cfg.maxPixels());
        Thread.currentThread().join();
    }

    static Server start(ServerConfig cfg) throws IOException {
        // Slow-client protection (seconds). JDK's server has no timeouts unless these are set.
        System.setProperty("sun.net.httpserver.maxReqTime", "10");
        System.setProperty("sun.net.httpserver.maxRspTime", "30");
        System.setProperty("sun.net.httpserver.idleInterval", "30");
        FontRegistry fonts = FontRegistry.load(cfg.fontDir());
        RenderCache cache = new RenderCache(cfg.cacheBytes());
        Metrics metrics = new Metrics(cache::bytes, cache::entries);
        Api api = new Api(cfg, fonts, cache, metrics);
        HttpServer http = HttpServer.create(new InetSocketAddress(cfg.host(), cfg.port()), 512);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        http.setExecutor(pool);
        http.createContext("/", api);
        http.start();
        return new Server(http, pool, api);
    }

    int port() {
        return http.getAddress().getPort();
    }

    /** Flip readiness first so load balancers stop sending, then let in-flight exchanges finish. */
    @Override
    public void close() {
        api.setDraining(true);
        http.stop(10);
        pool.shutdown();
    }
}
