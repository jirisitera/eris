package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.ClientView;
import com.japicraft.camera.CursorProbe;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerTickEvent;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

public final class PlayerUpdateManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerTickEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.update(player);
            CursorProbe.update(player);
        });

        MinecraftServer.getGlobalEventHandler().addListener(InventoryPreClickEvent.class, event -> {
            if (event.getSlot() == PlayerInventoryUtils.OFFHAND_SLOT) {
                event.setCancelled(true);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerPacketEvent.class, event -> {
            Player player = event.getPlayer();
            // only react to the rotation the client actually sent, the server-side view gets overwritten by the camera vehicle
            if (!ClientView.record(player, event.getPacket())) {
                return;
            }
            CameraAnchor.updateTarget(player);
            CursorProbe.update(player);
        });
    }
}
