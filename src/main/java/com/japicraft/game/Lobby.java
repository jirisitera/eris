package com.japicraft.game;

import com.japicraft.camera.MapProbe;
import com.japicraft.environment.StructureLoader;
import com.japicraft.events.PlayerSetupManager;
import com.japicraft.server.Notification;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.tag.Tag;

public final class Lobby {
    private static final Tag<Boolean> TAG = Tag.Transient("inLobby");
    private static final Sound SOUND_ACCEPT = Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.MASTER, 0.6F, 1.0F);

    public static void enter(Player player) {
        player.setTag(TAG, true);
        StructureLoader.activateLobby(player);
    }

    public static boolean isInLobby(Player player) {
        return Boolean.TRUE.equals(player.getTag(TAG));
    }

    public static boolean handleClick(Player player) {
        if (!isInLobby(player)) {
            return false;
        }
        if (MapProbe.isLobbyButtonHovered(player)) {
            startGame(player);
        }
        return true;
    }

    private static void startGame(Player player) {
        player.removeTag(TAG);
        player.playSound(SOUND_ACCEPT);

        // main monitor is already on; switch it to the energy readout and boot the remaining ones
        MapProbe.setDots(player, MapProbe.LOBBY_MONITOR, MapProbe.energyDot(100, 1, 4));
        StructureLoader.activate(player, MapProbe.LOBBY_MONITOR);

        Director director = Director.createDefault();
        player.setTag(Director.TAG, director);
        if (player.getInstance() instanceof InstanceContainer container) {
            director.start(container);
            director.doors().toggleLeft(player, container);
            director.doors().toggleRight(player, container);
        }

        Notification.info(player, "Welcome to Eris!");
    }

    public static void leave(Player player) {
        player.removeTag(TAG);
    }
}
