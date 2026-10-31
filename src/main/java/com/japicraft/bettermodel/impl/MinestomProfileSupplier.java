package com.japicraft.bettermodel.impl;

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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class MinestomProfileSupplier implements ModelProfileSupplier {
    private final HttpClient http = HttpClient.newHttpClient();
    private final Cache<ModelProfileInfo, ModelProfile> cache = Caffeine.newBuilder().expireAfterAccess(5L, TimeUnit.MINUTES).build();

    MinestomProfileSupplier() {
    }

    private static @NotNull UUID undashed(@NotNull String id) {
        return UUID.fromString(id.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5"));
    }

    @Override
    public @NotNull ModelProfile.Uncompleted supply(@NotNull ModelProfileInfo info) {
        Player online = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(info.id());
        if (online != null) {
            return BasePlayerImpl.profileOf(online).asUncompleted();
        }
        return new ModelProfile.Uncompleted() {
            @Override
            public @NotNull ModelProfileInfo info() {
                return info;
            }

            @Override
            public @NotNull CompletableFuture<ModelProfile> complete() {
                ModelProfile cached = cache.getIfPresent(info);
                if (cached != null) {
                    return CompletableFuture.completedFuture(cached);
                }
                return lookUp(info).thenApply(profile -> {
                    cache.put(info, profile);
                    return profile;
                }).exceptionally(e -> {
                    LogUtil.handleException("Unable to get " + info.name() + "'s skin data.", e);
                    return this.fallback();
                });
            }
        };
    }

    private @NotNull CompletableFuture<ModelProfile> lookUp(@NotNull ModelProfileInfo info) {
        CompletableFuture<String> id = info.name() != null
            ? this.get("https://api.minecraftservices.com/minecraft/profile/lookup/name/" + info.name())
            .thenApply(json -> json.getAsJsonObject().get("id").getAsString())
            : CompletableFuture.completedFuture(info.id().toString().replace("-", ""));

        return id.thenCompose(uuid -> this.get("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).thenApply(json -> {
            JsonObject profile = json.getAsJsonObject();
            ModelProfileInfo resolved = new ModelProfileInfo(MinestomProfileSupplier.undashed(profile.get("id").getAsString()), profile.get("name").getAsString());
            ModelProfileSkin skin = ModelProfileSkin.EMPTY;
            if (profile.has("properties")) {
                for (JsonElement property : profile.getAsJsonArray("properties")) {
                    JsonObject object = property.getAsJsonObject();
                    if (!"textures".equals(object.get("name").getAsString())) continue;
                    skin = ProfileManagerImpl.INSTANCE.skin(object.get("value").getAsString());
                }
            }
            return ModelProfile.of(resolved, skin);
        });
    }

    private @NotNull CompletableFuture<JsonElement> get(@NotNull String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return this.http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).thenApply(response -> {
            try (InputStream body = response.body()) {
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
