package com.example.retrydemo;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny upstream built on the JDK's own HttpServer (no extra dependency).
 * Answers {@code failureStatus} for the first N calls, then 200 "stock-42",
 * and counts every call it receives, so tests can assert how many attempts really happened.
 */
public class FlakyServer implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int failuresBeforeSuccess;
    private volatile int failureStatus = 503;

    public FlakyServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            int n = hits.incrementAndGet();
            int status = n <= failuresBeforeSuccess ? failureStatus : 200;
            byte[] body = (status == 200 ? "stock-42" : "error").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        server.start();
    }

    /** Forget previous calls and configure the next scenario. */
    public void reset(int failuresBeforeSuccess, int failureStatus) {
        this.hits.set(0);
        this.failuresBeforeSuccess = failuresBeforeSuccess;
        this.failureStatus = failureStatus;
    }

    public int hits() {
        return hits.get();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
