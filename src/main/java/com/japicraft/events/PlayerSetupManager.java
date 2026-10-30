package com.japicraft.events;

import com.japicraft.camera.Avatar;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.CursorProbe;
import com.japicraft.server.PostEffect;
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
            Avatar.attach(player);
            CursorProbe.update(player);
        });
        // leave event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();

            CameraAnchor.detach(player);
            Avatar.detach(player);
            CursorProbe.detach(player);
        });
    }
}
