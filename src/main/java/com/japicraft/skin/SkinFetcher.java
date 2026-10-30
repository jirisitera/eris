package com.japicraft.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.japicraft.Eris;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerSocketConnection;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class SkinFetcher {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    private final Map<UUID, SkinData> skinCache = new ConcurrentHashMap<>();
    private final Map<String, byte[]> urlDataCache = new ConcurrentHashMap<>();

    public static @Nullable String extractSkinUrl(PlayerSkin skin) {
        if (skin == null || skin.textures().isBlank()) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(skin.textures());
            JsonObject root = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.has("textures")) {
                JsonObject textures = root.getAsJsonObject("textures");
                if (textures.has("SKIN")) {
                    JsonObject skinObj = textures.getAsJsonObject("SKIN");
                    if (skinObj.has("url")) {
                        return skinObj.get("url").getAsString();
                    }
                }
            }
        } catch (Exception e) {
            Eris.LOGGER.warn("Failed to parse skin texture JSON: {}", e.getMessage());
        }
        return null;
    }

    public static boolean isSlim(PlayerSkin skin) {
        if (skin == null || skin.textures().isBlank()) {
            return false;
        }
        byte[] decoded = Base64.getDecoder().decode(skin.textures());
        JsonObject root = JsonParser.parseString(new String(decoded)).getAsJsonObject();
        if (root.has("textures")) {
            JsonObject textures = root.getAsJsonObject("textures");
            if (textures.has("SKIN")) {
                JsonObject skinData = textures.getAsJsonObject("SKIN");
                if (skinData.has("metadata")) {
                    JsonObject metadata = skinData.getAsJsonObject("metadata");
                    if (metadata.has("model")) {
                        return "slim".equalsIgnoreCase(metadata.get("model").getAsString());
                    }
                }
            }
        }
        return false;
    }

    public static byte[] downloadSkinBytes(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .timeout(Duration.ofSeconds(10))
            .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP status " + response.statusCode());
        }
        return response.body();
    }

    public static byte[] getFallbackSkin() {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB), "png", stream);
            return stream.toByteArray();
        } catch (IOException ignored) {
            return new byte[0];
        }
    }

    public @Nullable PlayerSkin resolvePlayerSkin(Player player) {
        if (player.getSkin() != null) {
            return player.getSkin();
        }
        if (player.getPlayerConnection() instanceof PlayerSocketConnection socketConnection) {
            GameProfile profile = socketConnection.gameProfile();
            if (profile != null) {
                for (GameProfile.Property property : profile.properties()) {
                    if ("textures".equals(property.name()) && property.signature() != null) {
                        PlayerSkin skin = new PlayerSkin(property.value(), property.signature());
                        player.setSkin(skin);
                        return skin;
                    }
                }
            }
        }
        // fallback: query Mojang session server
        PlayerSkin skin = PlayerSkin.fromUuid(player.getUuid().toString());
        if (skin == null) {
            skin = PlayerSkin.fromUsername(player.getUsername());
        }
        if (skin != null) {
            player.setSkin(skin);
        }
        return skin;
    }

    public SkinData fetchSkin(Player player) {
        SkinData cached = skinCache.get(player.getUuid());
        if (cached != null) {
            return cached;
        }

        PlayerSkin skin = resolvePlayerSkin(player);
        if (skin == null) {
            SkinData fallback = new SkinData(getFallbackSkin(), false, null);
            skinCache.put(player.getUuid(), fallback);
            return fallback;
        }

        String textureUrl = extractSkinUrl(skin);
        boolean slim = isSlim(skin);

        if (textureUrl == null || textureUrl.isBlank()) {
            SkinData fallback = new SkinData(getFallbackSkin(), slim, null);
            skinCache.put(player.getUuid(), fallback);
            return fallback;
        }

        byte[] pngBytes = urlDataCache.computeIfAbsent(textureUrl, url -> {
            try {
                return downloadSkinBytes(url);
            } catch (Exception e) {
                Eris.LOGGER.warn("Failed to download skin for {} from {}: {}", player.getUsername(), url, e.getMessage());
                return getFallbackSkin();
            }
        });

        SkinData result = new SkinData(pngBytes, slim, textureUrl);
        skinCache.put(player.getUuid(), result);
        return result;
    }

    public byte[] fetchSkinPng(Player player) {
        return fetchSkin(player).pngBytes();
    }

    public void invalidate(UUID playerUuid) {
        skinCache.remove(playerUuid);
    }

    public record SkinData(byte[] pngBytes, boolean slim, @Nullable String textureUrl) {
    }
}
