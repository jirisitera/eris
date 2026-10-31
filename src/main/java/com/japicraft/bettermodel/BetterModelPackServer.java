package com.japicraft.bettermodel;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import kr.toxicity.model.api.pack.PackByte;
import kr.toxicity.model.api.pack.PackResult;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class BetterModelPackServer {
    private static final Logger log = LoggerFactory.getLogger("bettermodel-pack");

    private static final String PORT_PROPERTY = "bettermodel.pack.port";
    private static final String BIND_PROPERTY = "bettermodel.pack.bindAddress";
    private static final String HOST_PROPERTY = "bettermodel.pack.host";
    private static final String REQUIRED_PROPERTY = "bettermodel.pack.required";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_BIND = "0.0.0.0";

    private static @Nullable HttpServer server;
    private static volatile byte @Nullable [] zipBytes;
    private static volatile @Nullable String sha1Hash;
    private static volatile @Nullable UUID packId;
    private static boolean listenerRegistered;

    private BetterModelPackServer() {
    }

    public static synchronized void start() {
        if (server != null) {
            return;
        }

        int port = Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT);
        String bindAddress = System.getProperty(BIND_PROPERTY, DEFAULT_BIND);

        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            http.createContext("/bettermodel.zip", new PackHandler());
            http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            http.start();
            server = http;
            log.info("BetterModel pack server listening on {}:{}", bindAddress, port);
        } catch (IOException e) {
            log.error("Failed to start BetterModel pack server on {}:{}", bindAddress, port, e);
            return;
        }

        if (!listenerRegistered) {
            listenerRegistered = true;
            MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
                sendPack(event.getPlayer());
            });
        }
    }

    public static synchronized void stop() {
        HttpServer http = server;
        if (http == null) {
            return;
        }
        server = null;
        http.stop(0);
        log.info("BetterModel pack server stopped.");
    }

    public static synchronized void update(@NotNull PackResult pack) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                zos.setLevel(Deflater.BEST_COMPRESSION);
                Set<String> entries = new HashSet<>();
                boolean hasMcmeta = false;

                for (PackByte packByte : (Iterable<PackByte>) pack.stream()::iterator) {
                    String path = packByte.path().path().replace('\\', '/');
                    if (path.startsWith("/")) {
                        path = path.substring(1);
                    }
                    if ("pack.mcmeta".equals(path)) {
                        hasMcmeta = true;
                    }
                    if (entries.add(path)) {
                        ZipEntry entry = new ZipEntry(path);
                        zos.putNextEntry(entry);
                        zos.write(packByte.bytes());
                        zos.closeEntry();
                    }
                }

                if (!hasMcmeta && pack.meta() != null) {
                    ZipEntry entry = new ZipEntry("pack.mcmeta");
                    zos.putNextEntry(entry);
                    zos.write(pack.meta().toJson().toString().getBytes(StandardCharsets.UTF_8));
                    zos.closeEntry();
                    entries.add("pack.mcmeta");
                }
            }

            byte[] bytes = baos.toByteArray();
            String hash = calculateSha1(bytes);
            UUID id = UUID.nameUUIDFromBytes(hash.getBytes(StandardCharsets.UTF_8));

            zipBytes = bytes;
            sha1Hash = hash;
            packId = id;

            log.info("BetterModel resource pack compiled ({} bytes, sha1: {})", bytes.length, hash);

            for (Player player : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
                sendPack(player);
            }
        } catch (IOException e) {
            log.error("Failed to compile BetterModel resource pack zip", e);
        }
    }

    public static void sendPack(@NotNull Player player) {
        byte[] currentPack = zipBytes;
        String currentHash = sha1Hash;
        UUID currentId = packId;

        if (currentPack == null || currentHash == null || currentId == null) {
            log.warn("Cannot send BetterModel resource pack to {}: pack is not ready yet.", player.getUsername());
            return;
        }

        URI uri = getUriFor(player);
        boolean required = Boolean.parseBoolean(System.getProperty(REQUIRED_PROPERTY, "true"));
        ResourcePackInfo info = ResourcePackInfo.resourcePackInfo(currentId, uri, currentHash);
        ResourcePackRequest request = ResourcePackRequest.resourcePackRequest()
            .prompt(Component.text("This server requires custom models for entities and items."))
            .packs(info)
            .required(required)
            .build();

        player.sendResourcePacks(request);
    }

    public static @Nullable ResourcePackInfo getPackInfo(@NotNull Player player) {
        String currentHash = sha1Hash;
        UUID currentId = packId;
        if (currentHash == null || currentId == null) {
            return null;
        }
        return ResourcePackInfo.resourcePackInfo(currentId, getUriFor(player), currentHash);
    }

    public static @NotNull URI getUriFor(@NotNull Player player) {
        String host = System.getProperty(HOST_PROPERTY, "");
        if (host.isBlank()) {
            String clientAddress = player.getPlayerConnection().getServerAddress();
            if (clientAddress != null && !clientAddress.isBlank()) {
                host = clientAddress;
            } else {
                host = "localhost";
            }
        }
        int port = getPort();
        return URI.create("http://" + host + ":" + port + "/bettermodel.zip");
    }

    public static int getPort() {
        HttpServer http = server;
        if (http != null) {
            return http.getAddress().getPort();
        }
        return Integer.getInteger(PORT_PROPERTY, DEFAULT_PORT);
    }

    public static byte @Nullable [] getZipBytes() {
        return zipBytes;
    }

    public static @Nullable String getSha1Hash() {
        return sha1Hash;
    }

    public static @Nullable UUID getPackId() {
        return packId;
    }

    private static @NotNull String calculateSha1(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-1 algorithm not available", e);
        }
    }

    private static class PackHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }

            byte[] currentPack = zipBytes;
            String currentHash = sha1Hash;

            if (currentPack == null || currentHash == null) {
                byte[] msg = "Resource pack not ready yet.".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                exchange.sendResponseHeaders(503, msg.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(msg);
                }
                exchange.close();
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "application/zip");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(currentPack.length));
            exchange.getResponseHeaders().set("ETag", "\"" + currentHash + "\"");

            if ("HEAD".equalsIgnoreCase(method)) {
                exchange.sendResponseHeaders(200, -1);
                exchange.close();
                return;
            }

            exchange.sendResponseHeaders(200, currentPack.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(currentPack);
            }
            exchange.close();
        }
    }
}
