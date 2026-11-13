package com.japicraft.server;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalResourcePackTest {
    @Test
    void hashesDownloadedBytesAfterRedirects() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/latest", exchange -> {
            exchange.getResponseHeaders().set("Location", "/release");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/release", exchange -> {
            exchange.getResponseHeaders().set("Location", "/pack.zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/pack.zip", exchange -> {
            byte[] body = "abc".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();
        try {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/latest");
            assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", GlobalResourcePack.calculateHash(uri));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsHttpErrorsAndEmptyDownloads() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> {
            byte[] body = "Not Found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.createContext("/empty", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            assertNull(GlobalResourcePack.calculateHash(URI.create(base + "/missing")));
            assertNull(GlobalResourcePack.calculateHash(URI.create(base + "/empty")));
        } finally {
            server.stop(0);
        }
    }
}
