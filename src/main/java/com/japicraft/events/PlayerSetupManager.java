package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.BombView;
import com.japicraft.camera.CursorProbe;
import com.japicraft.emote.EmoteManager;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.PlayerRole;
import com.japicraft.voicechat.VoiceChatManager;
import com.japicraft.server.PostEffect;
import com.japicraft.service.ActionBarService;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerLoadedEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;

import java.util.List;

public final class PlayerSetupManager {
    public static void register() {
        // join event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();

            player.setGameMode(GameMode.ADVENTURE);
            player.setInvisible(true);

            PlayerRole role = PlayerRole.get(player);
            // Decode the timer, style the scene, apply role vision, then draw controls last.
            player.sendPacket(new PostEffectsPacket(role == PlayerRole.BOMB_DEFUSER
                ? List.of(PostEffect.TIMER, PostEffect.BLINDNESS, PostEffect.CURSOR)
                : List.of(PostEffect.TIMER, PostEffect.HUD, PostEffect.CURSOR)));
            if (role != null) {
                VoiceChatManager.setMuted(player, role == PlayerRole.MANUAL_EXPERT);
                VoiceChatManager.setDeafened(player, role == PlayerRole.DEFUSAL_HELPER);
            }

            BombView.detach(player);
            EmoteWheel.detach(player);
            CameraAnchor.attach(player);
            BombView.attach(player);
            CursorProbe.forceUpdate(player);
            ActionBarService.schedule(player);
            BombView.showControls(player);
            player.scheduler().scheduleNextTick(() -> CameraAnchor.syncBodyMounts(player));

            player.scheduler().scheduleNextTick(() -> {
                EmoteManager.play(player, "steve", "idle");
                // Model/inventory setup can overwrite the initial slot update. Resend
                // after setup even when the mouse hasn't changed (and the probe is cached).
                CursorProbe.forceUpdate(player);
            });
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerLoadedEvent.class, event -> CursorProbe.forceUpdate(event.getPlayer()));

        // leave event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();

            BombView.detach(player);
            CameraAnchor.detach(player);
            CursorProbe.detach(player);
            EmoteWheel.detach(player);

            EmoteManager.stop(player);
        });
    }
}
