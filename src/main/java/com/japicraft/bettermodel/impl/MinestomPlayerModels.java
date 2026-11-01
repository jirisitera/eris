package com.japicraft.bettermodel.impl;

import kr.toxicity.model.api.bone.BoneRenderContext;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.data.renderer.RenderPipeline;
import kr.toxicity.model.api.data.renderer.RenderSource;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.entity.BasePlayer;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.profile.ModelProfileInfo;
import kr.toxicity.model.api.skin.SkinData;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import kr.toxicity.model.manager.SkinManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Builds player models with vanilla skin parts while their actual skin loads. */
public final class MinestomPlayerModels {
    private static volatile SkinData[] defaults = new SkinData[0];

    private MinestomPlayerModels() {
    }

    static void loadDefaults() {
        var futures = new CompletableFuture<?>[18];
        var loaded = new SkinData[18];
        for (int index = 0; index < loaded.length; index++) {
            int selected = index;
            // Separate cache identities keep placeholder skins out of real player cache entries.
            var info = new ModelProfileInfo(new UUID(0, index), "VanillaDefault" + index);
            var profile = MinestomProfileSupplier.defaultProfile(info);
            SkinManagerImpl.INSTANCE.removeCache(profile);
            futures[index] = SkinManagerImpl.INSTANCE.complete(profile.asUncompleted()).thenAccept(skin -> {
                if (skin == null || skin == SkinManagerImpl.INSTANCE.fallback()) {
                    throw new IllegalStateException("Unable to load vanilla default skin " + selected);
                }
                loaded[selected] = skin;
            });
        }
        CompletableFuture.allOf(futures).join();
        defaults = loaded;
    }

    public static @NotNull EntityTracker getOrCreate(
        @NotNull ModelRenderer renderer,
        @NotNull PlatformEntity entity,
        @NotNull TrackerModifier modifier,
        @NotNull Consumer<EntityTracker> preUpdate
    ) {
        BaseEntity base = BaseEntity.of(entity);
        if (!(base instanceof BasePlayer player)) {
            return renderer.getOrCreate(base, modifier, preUpdate);
        }
        var source = RenderSource.of(player);
        return source.getOrCreate(renderer.name(), current -> {
            var context = initialContext((RenderSource.Player) current);
            return new RenderPipeline(renderer, current, renderer.rendererGroups().values().stream()
                .map(group -> group.create(context)).toArray(RenderedBone[]::new));
        }, modifier, preUpdate);
    }

    static @NotNull BoneRenderContext initialContext(@NotNull RenderSource.Player source) {
        var skins = defaults;
        if (skins.length != 18) {
            throw new IllegalStateException("Vanilla default skins have not been loaded");
        }
        var skin = skins[Math.floorMod(source.profile().info().id().hashCode(), skins.length)];
        return new BoneRenderContext(source, skin);
    }
}
