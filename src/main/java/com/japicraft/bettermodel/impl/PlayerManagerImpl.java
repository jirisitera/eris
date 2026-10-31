package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomPlayer;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.toxicity.model.api.manager.PlayerManager;
import kr.toxicity.model.api.nms.PlayerChannelHandler;
import kr.toxicity.model.api.pack.PackZipper;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.util.LogUtil;
import kr.toxicity.model.manager.GlobalManager;
import kr.toxicity.model.manager.ReloadPipeline;
import kr.toxicity.model.manager.SkinManagerImpl;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.trait.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("resource")
final class PlayerManagerImpl implements PlayerManager, GlobalManager {
    private final Map<UUID, PlayerChannelHandlerImpl> players = new ConcurrentHashMap<>();
    private final EventNode<PlayerEvent> events = EventNode.type("bettermodel-players", EventFilter.PLAYER);

    PlayerManagerImpl() {
    }

    @NotNull EventNode<PlayerEvent> events() {
        return this.events;
    }

    @Override
    public void start() {
        this.events.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }
            try {
                PlayerChannelHandlerImpl handler = this.register(event.getPlayer());
                handler.spawnViewed();
            } catch (RuntimeException e) {
                LogUtil.handleException("Unable to load " + event.getPlayer().getUsername() + "'s data.", e);
            }
        });
        this.events.addListener(PlayerDisconnectEvent.class, event -> {
            PlayerChannelHandlerImpl handler = this.players.remove(event.getPlayer().getUuid());
            if (handler != null) {
                handler.close();
                SkinManagerImpl.INSTANCE.removeCache(handler.base().profile());
            }
        });
        this.events.addListener(PlayerPacketOutEvent.class, event -> {
            PlayerChannelHandlerImpl handler = this.players.get(event.getPlayer().getUuid());
            if (handler != null) {
                handler.outgoing(event);
            }
        });
        this.events.addListener(PlayerPacketEvent.class, event -> {
            PlayerChannelHandlerImpl handler = this.players.get(event.getPlayer().getUuid());
            if (handler != null) {
                handler.incoming(event);
            }
        });
    }

    @Override
    public void end() {
        this.players.values().forEach(handler -> {
            handler.close();
            SkinManagerImpl.INSTANCE.removeCache(handler.base().profile());
        });
        this.players.clear();
    }

    @Override
    public void reload(@NotNull ReloadPipeline pipeline, @NotNull PackZipper zipper) {
    }

    private @NotNull PlayerChannelHandlerImpl register(@NotNull Player player) {
        PlayerChannelHandlerImpl handler = this.players.computeIfAbsent(player.getUuid(), _ -> new PlayerChannelHandlerImpl(player));
        SkinManagerImpl.INSTANCE.complete(handler.base().profile().asUncompleted());
        return handler;
    }

    @Override
    public @Nullable PlayerChannelHandler player(@NotNull UUID uuid) {
        return this.players.get(uuid);
    }

    @Override
    public @NotNull PlayerChannelHandler player(@NotNull PlatformPlayer player) {
        return this.register(((MinestomPlayer) player).source());
    }
}
