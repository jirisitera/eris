package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.BetterModelPackServer;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import kr.toxicity.model.api.profile.ModelProfile;
import kr.toxicity.model.api.profile.ModelProfileInfo;
import kr.toxicity.model.api.profile.ModelProfileSkin;
import kr.toxicity.model.api.profile.ModelProfileSupplier;
import kr.toxicity.model.api.util.LogUtil;
import kr.toxicity.model.manager.ProfileManagerImpl;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class MinestomProfileSupplier implements ModelProfileSupplier {
    // Minecraft 26.3 DefaultPlayerSkin order: all slim variants, then all wide variants.
    private static final String[] DEFAULT_SKINS = {
        "alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"
    };
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Cache<ModelProfileInfo, CompletableFuture<ModelProfile>> cache = Caffeine.newBuilder().expireAfterWrite(5L, TimeUnit.MINUTES).build();

    MinestomProfileSupplier() {
    }

    private static @NotNull UUID undashed(@NotNull String id) {
        return UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    @Override
    public @NotNull ModelProfile.Uncompleted supply(@NotNull ModelProfileInfo info) {
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(info.id());
        if (online != null && online.getSkin() != null) {
            return BasePlayerImpl.profileOf(online).asUncompleted();
        }
        return new ModelProfile.Uncompleted() {
            @Override
            public @NotNull ModelProfileInfo info() {
                return info;
            }

            @Override
            public @NotNull CompletableFuture<ModelProfile> complete() {
                return cache.get(info, key -> lookUp(key).exceptionally(e -> {
                    LogUtil.handleException("Unable to get " + info.name() + "'s skin data.", e);
                    return this.fallback();
                }));
            }

            @Override
            public @NotNull ModelProfile fallback() {
                return defaultProfile(info);
            }
        };
    }

    private @NotNull CompletableFuture<ModelProfile> lookUp(@NotNull ModelProfileInfo info) {
        CompletableFuture<String> id = info.name() != null
            ? this.get("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + info.name())
            .thenApply(json -> json.isJsonNull() ? null : json.getAsJsonObject().get("id").getAsString())
            : CompletableFuture.completedFuture(info.id().toString().replace("-", ""));

        return id.thenCompose(uuid -> uuid == null ? CompletableFuture.completedFuture(com.google.gson.JsonNull.INSTANCE)
            : this.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).thenApply(json -> {
            if (json.isJsonNull()) return defaultProfile(info);
            JsonObject profile = json.getAsJsonObject();
            UUID accountId = MinestomProfileSupplier.undashed(profile.get("id").getAsString());
            ModelProfileSkin skin = defaultProfile(new ModelProfileInfo(accountId, info.name())).skin();
            if (profile.has("properties")) {
                for (JsonElement property : profile.getAsJsonArray("properties")) {
                    JsonObject object = property.getAsJsonObject();
                    if (!"textures".equals(object.get("name").getAsString())) continue;
                    ModelProfileSkin supplied = ProfileManagerImpl.INSTANCE.skin(object.get("value").getAsString());
                    if (supplied.skin() != null) skin = supplied;
                }
            }
            // Keep the server identity: offline-mode UUIDs differ from Mojang account UUIDs.
            return ModelProfile.of(info, skin);
        });
    }

    static @NotNull ModelProfile defaultProfile(@NotNull ModelProfileInfo info) {
        int index = Math.floorMod(info.id().hashCode(), DEFAULT_SKINS.length * 2);
        boolean slim = index < DEFAULT_SKINS.length;
        String path = (slim ? "slim/" : "wide/") + DEFAULT_SKINS[index % DEFAULT_SKINS.length] + ".png";
        String url = BetterModelPackServer.getDefaultSkinUri(path).toString();
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + url
            + "\",\"metadata\":{\"model\":\"" + (slim ? "slim" : "default") + "\"}}}}";
        String raw = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        return ModelProfile.of(info, new ModelProfileSkin(URI.create(url), null, slim, raw));
    }

    private @NotNull CompletableFuture<JsonElement> get(@NotNull String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build();
        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream body = response.body()) {
                if (response.statusCode() == 204 || response.statusCode() == 404) {
                    return com.google.gson.JsonNull.INSTANCE;
                }
                JsonElement jsonElement;
                try (InputStreamReader reader = new InputStreamReader(body, StandardCharsets.UTF_8)) {
                    if (response.statusCode() != 200) {
                        throw new IllegalStateException("HTTP " + response.statusCode() + " from " + url);
                    }
                    jsonElement = JsonParser.parseReader(reader);
                }
                return jsonElement;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
