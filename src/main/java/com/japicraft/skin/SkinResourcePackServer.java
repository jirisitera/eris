package com.japicraft.skin;

import com.japicraft.Eris;
import com.japicraft.environment.InstanceManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.network.player.PlayerSocketConnection;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class SkinResourcePackServer {
    private static final String HOST = "0.0.0.0";
    private static final int PORT = 8085;
    private final SkinFetcher skinFetcher;
    private final Map<UUID, SkinResourcePack.PackData> hostedPacks = new ConcurrentHashMap<>();
    private HttpServer httpServer;

    public SkinResourcePackServer() {
        this.skinFetcher = new SkinFetcher();
        try {
            start();
        } catch (Exception e) {
            Eris.LOGGER.error("Failed to start skin resource pack server.", e);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));
    }

    public static UUID getPackId(UUID playerUuid) {
        return UUID.nameUUIDFromBytes(("eris-skin-" + playerUuid).getBytes());
    }

    public synchronized void start() throws IOException {
        if (httpServer != null) {
            return;
        }
        httpServer = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);
        httpServer.createContext("/pack/", this::handlePackRequest);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        httpServer.start();
        Eris.LOGGER.info("Resource pack HTTP server started on {}:{}.", HOST, PORT);
    }

    public synchronized void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
            Eris.LOGGER.info("Resource pack HTTP server stopped.");
        }
    }

    private void handlePackRequest(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String rawId = path.substring("/pack/".length());
        if (rawId.endsWith(".zip")) {
            rawId = rawId.substring(0, rawId.length() - ".zip".length());
        }

        UUID packId;
        try {
            packId = UUID.fromString(rawId);
        } catch (IllegalArgumentException e) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        SkinResourcePack.PackData packData = hostedPacks.get(packId);
        if (packData == null) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "application/zip");
        exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"pack.zip\"");
        exchange.getResponseHeaders().set("ETag", "\"" + packData.sha1Hex() + "\"");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");

        if ("HEAD".equalsIgnoreCase(method)) {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(200, packData.zipBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(packData.zipBytes());
        }
        exchange.close();
    }

    public void send(Player player) {
        try {
            UUID packId = getPackId(player.getUuid());
            SkinResourcePack.PackData packData = SkinResourcePack.create(packId, skinFetcher.fetchSkinPng(player));
            hostedPacks.put(packId, packData);

            player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(packId, URI.create("http://" + resolveHost(player) + ":" + PORT + "/pack/" + packId + ".zip"), packData.sha1Hex()))
                .required(true)
                .prompt(Component.empty())
                .build()
            );
        } catch (Exception e) {
            Eris.LOGGER.error("Failed to generate and send skin resource pack to {}.", player.getUsername(), e);
        }
    }
    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> send(event.getPlayer()));
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> removePack(event.getPlayer().getUuid()));
    }

    public String resolveHost(Player player) {
        if (player.getPlayerConnection() instanceof PlayerSocketConnection socketConnection) {
            String serverAddress = socketConnection.getServerAddress();
            if (serverAddress != null && !serverAddress.isBlank()) {
                if (serverAddress.endsWith(".")) {
                    serverAddress = serverAddress.substring(0, serverAddress.length() - 1);
                }
                return serverAddress;
            }
        }
        return "127.0.0.1";
    }

    public void removePack(UUID playerUuid) {
        hostedPacks.remove(getPackId(playerUuid));
        skinFetcher.invalidate(playerUuid);
    }
}
