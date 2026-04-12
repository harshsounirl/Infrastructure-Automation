import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.concurrent.Executors;

/**
 * DemoApp – lightweight HTTP service used for Ansible deployment demos.
 *
 * Endpoints:
 *   GET /        → HTML welcome page
 *   GET /health  → JSON {"status":"UP","timestamp":"..."}
 *   GET /info    → JSON with JVM and environment info
 */
public class DemoApp {

    private static final int PORT = Integer.parseInt(
            System.getProperty("server.port", System.getenv().getOrDefault("SERVER_PORT", "8080"))
    );
    private static final String PROFILE = System.getenv().getOrDefault("SPRING_PROFILES_ACTIVE", "dev");
    private static final long START_TIME = System.currentTimeMillis();

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/",       new RootHandler());
        server.createContext("/health", new HealthHandler());
        server.createContext("/info",   new InfoHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.printf("[DemoApp] Started on port %d (profile=%s)%n", PORT, PROFILE);
    }

    // ── Root handler ────────────────────────────────────────────────────────
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            String html = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head><meta charset="UTF-8"><title>Demo App</title>
                    <style>
                      body{font-family:sans-serif;background:#1a1a2e;color:#eee;
                           display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
                      .card{background:#16213e;border-radius:12px;padding:40px 60px;text-align:center}
                      h1{color:#e94560} a{color:#e94560}
                    </style></head>
                    <body>
                      <div class="card">
                        <h1>Demo App – Java 17</h1>
                        <p>Profile: <strong>%s</strong></p>
                        <p><a href="/health">/health</a> | <a href="/info">/info</a></p>
                      </div>
                    </body></html>
                    """.formatted(PROFILE);
            sendResponse(exchange, 200, "text/html", html);
        }
    }

    // ── Health handler ───────────────────────────────────────────────────────
    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            long uptimeSeconds = (System.currentTimeMillis() - START_TIME) / 1000;
            String json = """
                    {"status":"UP","timestamp":"%s","uptimeSeconds":%d,"profile":"%s"}
                    """.formatted(Instant.now(), uptimeSeconds, PROFILE).strip();
            sendResponse(exchange, 200, "application/json", json);
        }
    }

    // ── Info handler ─────────────────────────────────────────────────────────
    static class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            Runtime rt = Runtime.getRuntime();
            long freeMb  = rt.freeMemory()  / 1_048_576;
            long totalMb = rt.totalMemory() / 1_048_576;
            long maxMb   = rt.maxMemory()   / 1_048_576;
            String json = """
                    {
                      "app":     "demo-app",
                      "profile": "%s",
                      "java": {
                        "version": "%s",
                        "vendor":  "%s"
                      },
                      "memory": {
                        "freeMB":  %d,
                        "usedMB":  %d,
                        "maxMB":   %d
                      },
                      "os": {
                        "name":    "%s",
                        "version": "%s",
                        "arch":    "%s"
                      }
                    }
                    """.formatted(
                    PROFILE,
                    System.getProperty("java.version"),
                    System.getProperty("java.vendor"),
                    freeMb, totalMb - freeMb, maxMb,
                    System.getProperty("os.name"),
                    System.getProperty("os.version"),
                    System.getProperty("os.arch")
            );
            sendResponse(exchange, 200, "application/json", json.strip());
        }
    }

    // ── Helper ───────────────────────────────────────────────────────────────
    private static void sendResponse(HttpExchange ex, int code, String contentType, String body)
            throws IOException {
        byte[] bytes = body.getBytes();
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.getResponseHeaders().set("X-Powered-By", "DemoApp/Java17");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
