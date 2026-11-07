package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.BombView;
import com.japicraft.environment.GameStageManager;
import com.japicraft.camera.CursorProbe;
import com.japicraft.emote.EmoteManager;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.service.ActionBarService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoadedEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;


public final class PlayerSetupManager {
    public static void register() {
        // join event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();

            player.setGameMode(GameMode.ADVENTURE);
            player.setInvisible(true);

            BombView.detach(player);
            EmoteWheel.detach(player);
            CameraAnchor.attach(player);
            BombView.attach(player);
            CursorProbe.forceUpdate(player);
            ActionBarService.schedule(player);
            GameStageManager.join(player);

            player.scheduler().scheduleNextTick(() -> {
                CameraAnchor.syncBodyMounts(player);
                EmoteManager.play(player, "steve", "idle");
                CursorProbe.forceUpdate(player);
            });
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoadedEvent.class, event -> CursorProbe.forceUpdate(event.getPlayer()));

        // leave event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();

            GameStageManager.leave(player);
            BombView.detach(player);
            CameraAnchor.detach(player);
            CursorProbe.detach(player);
            EmoteWheel.detach(player);

            EmoteManager.stop(player);
        });
    }
}
