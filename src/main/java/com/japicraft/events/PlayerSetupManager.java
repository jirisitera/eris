package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.CursorProbe;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.server.PostEffect;
import com.japicraft.emote.EmoteManager;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;

import java.util.List;

public final class PlayerSetupManager {
    public static void register() {
        // join event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();

            player.sendPacket(new PostEffectsPacket(List.of(PostEffect.HUD)));
            player.setInvisible(true);

            CameraAnchor.attach(player);
            CursorProbe.update(player);

            player.scheduler().scheduleNextTick(() -> EmoteManager.play(player, "steve", "idle"));
        });
        // leave event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();

            CameraAnchor.detach(player);
            CursorProbe.detach(player);
            EmoteWheel.detach(player);

            EmoteManager.stop(player);
        });
    }
}
