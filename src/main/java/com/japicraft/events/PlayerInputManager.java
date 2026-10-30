package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.EmoteWheel;
import com.japicraft.server.Notification;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;

public final class PlayerInputManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerHandAnimationEvent.class, event -> {
            Player player = event.getPlayer();
            int slice = EmoteWheel.hoveredSlice(player);
            if (EmoteWheel.isOpen(player) && slice >= 0) {
                Notification.info(player, "Selected emote " + slice + ".");
                EmoteWheel.close(player);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerInputEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.updateInput(player);
        });

        // the cursor probe has to stay in the off hand; pressing swap offhand (F) toggles the emote wheel
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSwapItemEvent.class, event -> {
            event.setCancelled(true);
            EmoteWheel.toggle(event.getPlayer());
        });
    }
}
