package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.pack.PackZipper;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.Tracker;
import kr.toxicity.model.api.tracker.TrackerExtraAnimation;
import kr.toxicity.model.manager.GlobalManager;
import kr.toxicity.model.manager.ReloadPipeline;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.entity.*;
import net.minestom.server.event.player.PlayerDeathEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerMoveEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

final class EntityManager implements GlobalManager {
    private final EventNode<Event> events = EventNode.all("bettermodel-entities");

    EntityManager() {
    }

    private static boolean isVisibilityEffect(@NotNull PotionEffect effect) {
        return effect == PotionEffect.GLOWING || effect == PotionEffect.INVISIBILITY;
    }

    private static void died(@NotNull Entity entity) {
        eachTracker(entity, tracker -> tracker.animate(TrackerExtraAnimation.DEATH));
        if (entity instanceof Player) {
            EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUuid());
            if (registry != null) {
                registry.despawn();
            }
        }
    }

    private static void eachTracker(@NotNull Entity entity, @NotNull Consumer<EntityTracker> action) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUuid());
        if (registry != null) {
            registry.trackers().forEach(action);
        }
    }

    @NotNull EventNode<Event> events() {
        return this.events;
    }

    @Override
    public void reload(@NotNull ReloadPipeline pipeline, @NotNull PackZipper zipper) {
        EntityTrackerRegistry.registries(EntityTrackerRegistry::reload);
    }

    @Override
    public void end() {
        EntityTrackerRegistry.registries(registry -> {
            registry.save();
            registry.close(Tracker.CloseReason.PLUGIN_DISABLE);
        });
    }

    @Override
    public void start() {
        this.events.setPriority(Integer.MAX_VALUE);

        // State events (invisibility & jump animation)
        this.events.addListener(EntityPotionAddEvent.class, event -> {
            if (isVisibilityEffect(event.getPotion().effect())) {
                eachTracker(event.getEntity(), EntityTracker::updateBaseEntity);
            }
        });
        this.events.addListener(EntityPotionRemoveEvent.class, event -> {
            if (isVisibilityEffect(event.getPotion().effect())) {
                eachTracker(event.getEntity(), EntityTracker::updateBaseEntity);
            }
        });
        this.events.addListener(PlayerMoveEvent.class, event -> {
            Player player = event.getPlayer();
            if (player.isOnGround() && !event.isOnGround() && event.getNewPosition().y() > player.getPosition().y()) {
                eachTracker(player, tracker -> tracker.animate(TrackerExtraAnimation.JUMP));
            }
        });

        // Lifecycle events
        this.events.addListener(EntitySpawnEvent.class, event -> {
            EntityTrackerRegistry registry = BetterModel.registryOrNull(MinestomAdapter.adapt(event.getEntity()));
            if (registry != null) {
                registry.refresh();
            }
        });
        this.events.addListener(EntityDespawnEvent.class, event -> {
            BaseEntityImpl.forget(event.getEntity().getUuid());
            EntityTrackerRegistry registry = BetterModel.registryOrNull(event.getEntity().getUuid());
            if (registry != null) {
                registry.despawn();
            }
        });
        this.events.addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                return;
            }
            EntityTrackerRegistry registry = BetterModel.registryOrNull(event.getPlayer().getUuid());
            if (registry != null) {
                registry.despawn();
                registry.refresh();
            }
        });
        this.events.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            EntityTrackerRegistry registry = BetterModel.registryOrNull(player.getUuid());
            if (registry != null) {
                registry.close();
            }
            PlatformPlayer platform = MinestomAdapter.adapt(player);
            BetterModel.platform().scheduler().asyncTask(() -> EntityTrackerRegistry.registries(each -> each.remove(platform)));
        });

        // Combat events (damage & death animations)
        this.events.addListener(EntityDamageEvent.class, event -> {
            if (event.isCancelled()) {
                return;
            }
            LivingEntity victim = event.getEntity();
            BaseEntityImpl.markHurt(victim.getUuid());
            eachTracker(victim, tracker -> {
                tracker.animate(TrackerExtraAnimation.DAMAGE);
                tracker.damageTint();
            });
        });
        this.events.addListener(EntityDeathEvent.class, event -> died(event.getEntity()));
        this.events.addListener(PlayerDeathEvent.class, event -> died(event.getPlayer()));
    }
}
