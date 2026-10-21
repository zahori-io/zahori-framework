package io.zahori.framework.driver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Endpoint W3C real: contesta la creacion de sesion como el grid, con la espera y el error que se le pida. */
final class FakeGrid implements AutoCloseable {

    record Reply(long delayMillis, int status, String body) {
    }

    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Queue<Reply> sessionReplies = new ConcurrentLinkedQueue<>();
    private final List<String> sessionRequestBodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger sessions = new AtomicInteger();
    private volatile long commandDelayMillis;

    FakeGrid() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
        server.start();
    }

    static Reply queued(long delayMillis) {
        return new Reply(delayMillis, 200, null);
    }

    static Reply rejected(String message) {
        return new Reply(0, 500, "{\"value\":{\"error\":\"session not created\",\"message\":\"" + message + "\",\"stacktrace\":\"\"}}");
    }

    FakeGrid thenReply(Reply reply) {
        sessionReplies.add(reply);
        return this;
    }

    FakeGrid withCommandDelay(long millis) {
        commandDelayMillis = millis;
        return this;
    }

    URL url() throws IOException {
        return new URL("http://127.0.0.1:" + server.getAddress().getPort() + "/wd/hub");
    }

    int newSessionRequests() {
        return sessionRequestBodies.size();
    }

    List<String> sessionRequestBodies() {
        return sessionRequestBodies;
    }

    boolean awaitSessionsCreated(int expected, Duration within) throws InterruptedException {
        long deadline = System.nanoTime() + within.toNanos();
        while (sessions.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        return sessions.get() >= expected;
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        try (exchange) {
            if ("POST".equals(method) && path.endsWith("/session")) {
                sessionRequestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                Reply reply = sessionReplies.poll();
                Reply effective = reply == null ? queued(0) : reply;
                pause(effective.delayMillis());
                String body = effective.body() != null ? effective.body()
                        : "{\"value\":{\"sessionId\":\"session-" + sessions.incrementAndGet() + "\",\"capabilities\":{\"browserName\":\"chrome\"}}}";
                write(exchange, effective.status(), body);
            } else if ("DELETE".equals(method)) {
                write(exchange, 200, "{\"value\":null}");
            } else {
                pause(commandDelayMillis);
                write(exchange, 200, "{\"value\":\"about:blank\"}");
            }
        } catch (IOException abandoned) {
            // El cliente ya colgo: el grid real seguiria con la sesion
        }
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void write(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
