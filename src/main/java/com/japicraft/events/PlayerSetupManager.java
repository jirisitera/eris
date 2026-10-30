package com.japicraft.events;

import com.japicraft.camera.Avatar;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.MapProbe;
import com.japicraft.game.Director;
import com.japicraft.game.Lobby;
import com.japicraft.environment.InstanceManager;
import com.japicraft.environment.StructureLoader;
import com.japicraft.server.PostEffect;
import com.japicraft.skin.SkinResourcePackServer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;

import java.util.List;

public final class PlayerSetupManager {
    public static void register() {
        // join event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            Player player = event.getPlayer();

            player.sendPacket(new PostEffectsPacket(List.of(PostEffect.HUD)));
            player.setInvisible(true);

            StructureLoader.assignBaseEntityData(player);

            CameraAnchor.attach(player);
            Avatar.attach(player);

            Lobby.enter(player);
        });
        // leave event
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            Director director = player.getTag(Director.TAG);
            if (director != null) {
                director.cleanup();
                player.removeTag(Director.TAG);
            }
            Lobby.leave(player);

            CameraAnchor.detach(player);
            Avatar.detach(player);
            MapProbe.detach(player);
        });
    }
}
