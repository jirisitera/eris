package com.japicraft.server;

import com.japicraft.Eris;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.server.ServerListPingEvent;
import net.minestom.server.ping.Status;

import java.io.IOException;
import java.io.InputStream;

public class ServerListManager {
    private static final byte[] FAVICON = ServerListManager.loadIcon();
    private static final Component DESCRIPTION = Component.text("Eris Test Server").color(TextColor.fromHexString("#FF5533"));
    private static final Status.VersionInfo VERSION_INFO = new Status.VersionInfo("Eris", MinecraftServer.PROTOCOL_VERSION);

    private static byte[] loadIcon() {
        try (InputStream stream = Eris.class.getResourceAsStream("/icon.png")) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException e) {
            return null;
        }
    }

    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(ServerListPingEvent.class, event -> event.setStatus(Status.builder()
            .playerInfo(Status.PlayerInfo.builder()
                .maxPlayers(50)
                .onlinePlayers(MinecraftServer.getConnectionManager().getOnlinePlayerCount())
                .build()
            )
            .favicon(ServerListManager.FAVICON)
            .description(ServerListManager.DESCRIPTION)
            .versionInfo(ServerListManager.VERSION_INFO)
            .enforcesSecureChat(true)
            .build()
        ));
    }
}
