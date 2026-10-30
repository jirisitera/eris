package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;

public final class PlayerInputManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerHandAnimationEvent.class, event -> {
            Player player = event.getPlayer();
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerInputEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.updateInput(player);
        });

        // the cursor probe has to stay in the off hand
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSwapItemEvent.class, event -> event.setCancelled(true));
    }
}
