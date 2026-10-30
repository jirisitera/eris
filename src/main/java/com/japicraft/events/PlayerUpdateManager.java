package com.japicraft.events;

import com.japicraft.camera.Avatar;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.ClientView;
import com.japicraft.camera.CursorProbe;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerTickEvent;

public final class PlayerUpdateManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerTickEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.update(player);
            Avatar.update(player);
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerPacketEvent.class, event -> {
            Player player = event.getPlayer();
            // only react to the rotation the client actually sent, the server-side view gets overwritten by the camera vehicle
            if (!ClientView.record(player, event.getPacket())) {
                return;
            }
            CameraAnchor.updateTarget(player);

            CursorProbe.update(player);
            CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
            player.sendActionBar(Component.text(String.format("Sector %s (x=%.3f, y=%.3f)", CursorProbe.sector(player), cursor.x(), cursor.y())));
        });
    }
}
