package com.japicraft.events;

import com.japicraft.camera.BombView;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.emote.EmoteManager;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.GameStageManager;
import com.japicraft.server.Notification;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;

public final class PlayerInputManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerHandAnimationEvent.class, event -> {
            Player player = event.getPlayer();
            if (!GameStageManager.isGame(player)) return;
            if (BombView.isActive(player)) {
                if (event.getHand() == PlayerHand.MAIN) BombView.press(player);
                return;
            }
            int slice = EmoteWheel.hoveredSlice(player);
            if (EmoteWheel.isOpen(player) && slice >= 0) {
                Notification.info(player, "Selected emote " + slice + ".");
                switch (slice) {
                    case 0 -> EmoteManager.play(player, "steve", "wave");
                    case 1 -> EmoteManager.play(player, "steve", "roll");
                }
                EmoteWheel.close(player);
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerInputEvent.class, event -> {
            Player player = event.getPlayer();
            if (!GameStageManager.isGame(player)) return;
            if (event.hasPressedBackwardKey()) {
                CameraAnchor.exitLean(player);
            } else if (event.hasPressedForwardKey() && !event.isHoldingBackwardKey()) {
                CameraAnchor.enterLean(player);
            }
            CameraAnchor.updateInput(player);
        });

        // Keep the cursor probe in the offhand. F readies in the lobby and opens emotes in game.
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSwapItemEvent.class, event -> {
            event.setCancelled(true);
            if (GameStageManager.isGame(event.getPlayer())) {
                EmoteWheel.toggle(event.getPlayer());
            } else {
                GameStageManager.toggleReady(event.getPlayer());
            }
        });
    }
}
