package com.japicraft.voicechat;

import com.japicraft.Eris;
import com.japicraft.service.ActionBarService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VoiceChatManager {
    public static final String MOD_ID = "voicechat";
    public static final int COMPATIBILITY_VERSION = 20;
    public static final int DEFAULT_PORT = 24454;

    private static final String CHANNEL_REQUEST_SECRET = MOD_ID + ":request_secret";
    private static final String CHANNEL_SECRET = MOD_ID + ":secret";
    private static final String CHANNEL_UPDATE_STATE = MOD_ID + ":update_state";
    private static final String CHANNEL_STATE = MOD_ID + ":state";
    private static final String CHANNEL_STATES = MOD_ID + ":states";
    private static final String CHANNEL_REMOVE_STATE = MOD_ID + ":remove_state";

    private static final Component MUTED_ACTION_BAR = Component.text("You are muted", NamedTextColor.RED);
    private static final Component DEAFENED_ACTION_BAR = Component.text("You are deafened", NamedTextColor.RED);

    private static final Map<UUID, VoiceChatPlayerState> STATES = new ConcurrentHashMap<>();
    private static final Set<UUID> MUTED_PLAYERS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> DEAFENED_PLAYERS = ConcurrentHashMap.newKeySet();

    private static VoiceChatServer server;

    private VoiceChatManager() {
    }

    public static void register() {
        register(Integer.getInteger("voicechat.port", DEFAULT_PORT), System.getProperty("voicechat.bindAddress", "0.0.0.0"));
    }

    public static synchronized void register(int port, String bindAddress) {
        if (server != null) {
            throw new IllegalStateException("VoiceChatManager is already registered.");
        }

        server = new VoiceChatServer(port, bindAddress);
        server.start();

        MinecraftServer.getSchedulerManager().buildShutdownTask(VoiceChatManager::shutdown);

        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();

        events.addListener(PlayerSpawnEvent.class, event -> {
            if (!event.isFirstSpawn()) {
                return;
            }
            Player player = event.getPlayer();
            VoiceChatPlayerState state = STATES.computeIfAbsent(
                player.getUuid(),
                uuid -> new VoiceChatPlayerState(uuid, player.getUsername(), false, true)
            );
            broadcastState(state);
        });

        events.addListener(PlayerDisconnectEvent.class, event -> {
            Player player = event.getPlayer();
            UUID uuid = player.getUuid();
            if (server != null) {
                server.disconnectClient(uuid);
            }
            STATES.remove(uuid);
            MUTED_PLAYERS.remove(uuid);
            DEAFENED_PLAYERS.remove(uuid);
            broadcastRemoveState(uuid);
        });

        events.addListener(PlayerPluginMessageEvent.class, event -> {
            Player player = event.getPlayer();
            String channel = event.getIdentifier();
            byte[] message = event.getMessage();

            switch (channel) {
                case CHANNEL_REQUEST_SECRET -> handleRequestSecret(player, message);
                case CHANNEL_UPDATE_STATE -> handleUpdateState(player, message);
                default -> {
                }
            }
        });
    }

    public static synchronized void shutdown() {
        if (server != null) {
            server.close();
            server = null;
        }
        STATES.clear();
        MUTED_PLAYERS.clear();
        DEAFENED_PLAYERS.clear();
    }

    public static boolean isMuted(Player player) {
        return isMuted(player.getUuid());
    }

    public static boolean isMuted(UUID playerUuid) {
        return MUTED_PLAYERS.contains(playerUuid);
    }

    public static void setMuted(Player player, boolean muted) {
        UUID uuid = player.getUuid();
        if (muted) {
            if (MUTED_PLAYERS.add(uuid) && server != null) {
                server.flushSender(player);
            }
            ActionBarService.setText(player, MUTED_ACTION_BAR);
        } else {
            MUTED_PLAYERS.remove(uuid);
            ActionBarService.setText(player, Component.empty());
        }
    }

    public static boolean toggleMuted(Player player) {
        boolean nowMuted = !isMuted(player);
        setMuted(player, nowMuted);
        return nowMuted;
    }

    public static boolean isDeafened(Player player) {
        return isDeafened(player.getUuid());
    }

    public static boolean isDeafened(UUID playerUuid) {
        return DEAFENED_PLAYERS.contains(playerUuid);
    }

    public static void setDeafened(Player player, boolean deafened) {
        UUID uuid = player.getUuid();
        if (deafened) {
            if (DEAFENED_PLAYERS.add(uuid) && server != null) {
                server.flushReceiver(player);
            }
            ActionBarService.setText(player, DEAFENED_ACTION_BAR);
        } else {
            DEAFENED_PLAYERS.remove(uuid);
            ActionBarService.setText(player, Component.empty());
        }
    }

    public static boolean toggleDeafened(Player player) {
        boolean nowDeafened = !isDeafened(player);
        setDeafened(player, nowDeafened);
        return nowDeafened;
    }

    public static VoiceChatPlayerState getState(UUID playerUuid) {
        return STATES.get(playerUuid);
    }

    static void initializePlayerConnection(Player player) {
        if (server == null) {
            return;
        }
        UUID uuid = player.getUuid();
        STATES.computeIfAbsent(uuid, id -> new VoiceChatPlayerState(id, player.getUsername(), false, true));

        sendAllStates(player);

        VoiceChatSecret secret = server.generateNewSecret(uuid);
        String voiceHost = System.getProperty("voicechat.host", "");

        VoiceChatBuffer buf = new VoiceChatBuffer();
        secret.toBytes(buf);
        buf.writeInt(server.getPort());
        buf.writeUUID(uuid);
        buf.writeByte(0); // Codec.VOIP
        buf.writeInt(VoiceChatServer.MAX_OPUS_PAYLOAD_SIZE);
        buf.writeDouble(48.0D);
        buf.writeInt(VoiceChatServer.KEEP_ALIVE_INTERVAL_MS);
        buf.writeBoolean(false); // groupsEnabled
        buf.writeUtf(voiceHost);
        buf.writeBoolean(true); // allowRecording

        player.sendPluginMessage(CHANNEL_SECRET, buf.toByteArray());
    }

    static void onPlayerVoiceConnected(UUID playerUuid) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(playerUuid);
        String name = player != null ? player.getUsername() : playerUuid.toString();
        VoiceChatPlayerState state = STATES.computeIfAbsent(
            playerUuid,
            id -> new VoiceChatPlayerState(id, name, false, false)
        );
        state.setDisconnected(false);
        broadcastState(state);
        Eris.LOGGER.atInfo().log(Component.text("Player " + name + " connected to Simple Voice Chat."));
    }

    static void onPlayerVoiceDisconnected(UUID playerUuid) {
        VoiceChatPlayerState state = STATES.get(playerUuid);
        if (state != null) {
            state.setDisconnected(true);
            broadcastState(state);
        }
    }

    private static void handleRequestSecret(Player player, byte[] message) {
        try {
            VoiceChatBuffer buf = new VoiceChatBuffer(message);
            int clientVersion = buf.readInt();
            if (clientVersion != COMPATIBILITY_VERSION) {
                Eris.LOGGER.atWarn().log(Component.text(
                    "Player " + player.getUsername() + " requested Simple Voice Chat with compatibility version "
                        + clientVersion + " (expected " + COMPATIBILITY_VERSION + ")."
                ));
            }
            initializePlayerConnection(player);
        } catch (Exception e) {
            Eris.LOGGER.atWarn().setCause(e).log(Component.text("Failed to read voicechat:request_secret from " + player.getUsername()));
        }
    }

    private static void handleUpdateState(Player player, byte[] message) {
        try {
            VoiceChatBuffer buf = new VoiceChatBuffer(message);
            boolean disabled = buf.readBoolean();
            VoiceChatPlayerState state = STATES.computeIfAbsent(
                player.getUuid(),
                id -> new VoiceChatPlayerState(id, player.getUsername(), disabled, true)
            );
            state.setDisabled(disabled);
            broadcastState(state);
        } catch (Exception e) {
            Eris.LOGGER.atWarn().setCause(e).log(Component.text("Failed to read voicechat:update_state from " + player.getUsername()));
        }
    }

    private static void sendAllStates(Player receiver) {
        Collection<VoiceChatPlayerState> allStates = STATES.values();
        VoiceChatBuffer buf = new VoiceChatBuffer();
        buf.writeInt(allStates.size());
        for (VoiceChatPlayerState state : allStates) {
            state.toBytes(buf);
        }
        receiver.sendPluginMessage(CHANNEL_STATES, buf.toByteArray());
    }

    private static void broadcastState(VoiceChatPlayerState state) {
        VoiceChatBuffer buf = new VoiceChatBuffer();
        state.toBytes(buf);
        byte[] payload = buf.toByteArray();
        for (Player receiver : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            receiver.sendPluginMessage(CHANNEL_STATE, payload);
        }
    }

    private static void broadcastRemoveState(UUID removedUuid) {
        VoiceChatBuffer buf = new VoiceChatBuffer();
        buf.writeUUID(removedUuid);
        byte[] payload = buf.toByteArray();
        for (Player receiver : MinecraftServer.getConnectionManager().getOnlinePlayers()) {
            receiver.sendPluginMessage(CHANNEL_REMOVE_STATE, payload);
        }
    }
}
