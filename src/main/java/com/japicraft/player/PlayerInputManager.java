package com.japicraft.player;

import com.japicraft.camera.BombView;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.game.GameStageManager;
import com.japicraft.lobby.LobbyInterface;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.player.PlayerChatEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;

public final class PlayerInputManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerHandAnimationEvent.class, event -> {
            Player player = event.getPlayer();
            if (!GameStageManager.isGame(player)) {
                if (event.getHand() == PlayerHand.MAIN) LobbyInterface.click(player);
                return;
            }
            if (BombView.isActive(player)) {
                if (event.getHand() == PlayerHand.MAIN) BombView.press(player);
                return;
            }
            if (EmoteWheel.isOpen(player)) {
                EmoteWheel.perform(player, EmoteWheel.hoveredSlice(player));
            }
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerInputEvent.class, event -> {
            Player player = event.getPlayer();
            if (!GameStageManager.isGame(player)) {
                return;
            }
            if (event.hasPressedBackwardKey()) {
                CameraAnchor.exitLean(player);
            } else if (event.hasPressedForwardKey() && !event.isHoldingBackwardKey()) {
                CameraAnchor.enterLean(player);
            }
            CameraAnchor.updateInput(player);
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSwapItemEvent.class, event -> {
            event.setCancelled(true);
            // offhand probe retained; F for lobby readiness or in-game emotes
            if (GameStageManager.isGame(event.getPlayer())) {
                EmoteWheel.toggle(event.getPlayer());
            } else {
                GameStageManager.toggleReady(event.getPlayer());
            }
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerChatEvent.class, event -> {
            // cancel all chat messages
            event.setCancelled(true);
        });
    }
}
