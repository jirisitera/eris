package com.japicraft.events;

import com.japicraft.camera.Avatar;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.MapProbe;
import com.japicraft.camera.ClientView;
import com.japicraft.game.Director;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.instance.InstanceContainer;

public final class PlayerUpdateManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerTickEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.update(player);
            if (CameraAnchor.isMainCamera(player)) Avatar.update(player);

            Director director = player.getTag(Director.TAG);
            if (director != null && player.getInstance() instanceof InstanceContainer container) {
                director.tick(player, container);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerPacketEvent.class, event -> {
            Player player = event.getPlayer();
            // only react to the rotation the client actually sent, the server-side view gets overwritten by the camera vehicle
            if (!ClientView.record(player, event.getPacket())) {
                return;
            }
            CameraAnchor.updateTarget(player);
            if (CameraAnchor.getCameraPosition(player) != null) {
                MapProbe.updateCursor(player);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerChangeHeldSlotEvent.class, event -> {
            CameraAnchor.setCamera(event.getPlayer(), event.getNewSlot() + 1);
        });
    }
}
