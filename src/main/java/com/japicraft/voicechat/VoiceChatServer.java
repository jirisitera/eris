package com.japicraft.voicechat;

import com.japicraft.Eris;
import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;
import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class VoiceChatServer extends Thread {
    public static final byte MAGIC_BYTE = (byte) 0xFF;
    public static final int MAX_OPUS_PAYLOAD_SIZE = 1275;
    public static final int MAX_VOICE_CHAT_PACKET_SIZE = 2048;
    public static final int KEEP_ALIVE_INTERVAL_MS = 1000;
    public static final long KEEP_ALIVE_TIMEOUT_MS = KEEP_ALIVE_INTERVAL_MS * 10L;
    public static final long MIC_PACKET_TTL_MS = 500L;
    public static final long DEFAULT_PACKET_TTL_MS = 10_000L;

    private static final byte PACKET_MIC = 0x1;
    private static final byte PACKET_GROUP_SOUND = 0x3; // Static (non-positional) sound packet
    private static final byte PACKET_AUTHENTICATE = 0x5;
    private static final byte PACKET_AUTHENTICATE_ACK = 0x6;
    private static final byte PACKET_PING = 0x7;
    private static final byte PACKET_KEEP_ALIVE = 0x8;
    private static final byte PACKET_CONNECTION_CHECK = 0x9;
    private static final byte PACKET_CONNECTION_CHECK_ACK = 0xA;

    private final int port;
    private final String bindAddress;
    private final VoicechatSocket socket;
    private final BlockingQueue<RawUdpPacket> packetQueue = new ArrayBlockingQueue<>(1024);
    private final Map<UUID, ClientConnection> connections = new ConcurrentHashMap<>();
    private final Map<UUID, ClientConnection> unCheckedConnections = new ConcurrentHashMap<>();
    private final Map<UUID, VoiceChatSecret> secrets = new ConcurrentHashMap<>();
    private final ProcessThread processThread;

    public VoiceChatServer(int port, String bindAddress) {
        this.port = port;
        this.bindAddress = bindAddress;
        this.socket = new VoiceChatSocketImpl();
        this.processThread = new ProcessThread();
        setDaemon(true);
        setName("VoiceChatServerThread");
    }

    private static byte[] buildGroupSoundPacket(UUID senderUuid, byte[] opusData, long sequenceNumber) {
        VoiceChatBuffer soundBuf = new VoiceChatBuffer();
        soundBuf.writeByte(PACKET_GROUP_SOUND);
        soundBuf.writeUUID(senderUuid); // channelId
        soundBuf.writeUUID(senderUuid); // sender
        soundBuf.writeByteArray(opusData);
        soundBuf.writeLong(sequenceNumber);
        soundBuf.writeByte(0); // flags (no category)
        return soundBuf.toByteArray();
    }

    private static ClientConnection findConnection(Map<UUID, ClientConnection> map, UUID playerId, SocketAddress address) {
        ClientConnection connection = map.get(playerId);
        return connection != null && connection.address().equals(address) ? connection : null;
    }

    @Override
    public void run() {
        try {
            socket.open(port, bindAddress);
            processThread.start();
            Eris.LOGGER.atInfo().log(Component.text("Simple Voice Chat UDP server started on port " + socket.getLocalPort() + "."));

            while (!socket.isClosed()) {
                try {
                    RawUdpPacket packet = socket.read();
                    if (!packetQueue.offer(packet)) {
                        // Prefer fresh audio when processing falls behind.
                        packetQueue.poll();
                        packetQueue.offer(packet);
                    }
                } catch (Exception e) {
                    if (socket.isClosed() || (e instanceof SocketException && e.getCause() instanceof AsynchronousCloseException)) {
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Eris.LOGGER.atError().setCause(e).log(Component.text("Failed to run Simple Voice Chat UDP server."));
        } finally {
            processThread.close();
            socket.close();
            packetQueue.clear();
        }
    }

    public int getPort() {
        int localPort = socket.getLocalPort();
        return localPort > 0 ? localPort : port;
    }

    public VoiceChatSecret generateNewSecret(UUID playerUuid) {
        VoiceChatSecret secret = VoiceChatSecret.generate();
        secrets.put(playerUuid, secret);
        return secret;
    }

    public void disconnectClient(UUID playerUuid) {
        connections.remove(playerUuid);
        unCheckedConnections.remove(playerUuid);
        secrets.remove(playerUuid);
    }

    public void flushSender(Player sender) {
        Instance instance = sender.getInstance();
        if (instance == null) {
            return;
        }
        UUID senderUuid = sender.getUuid();
        byte[] flushPacket = buildGroupSoundPacket(senderUuid, new byte[0], 0L);
        for (Player receiver : instance.getPlayers()) {
            if (receiver.getUuid().equals(senderUuid)) {
                continue;
            }
            ClientConnection conn = connections.get(receiver.getUuid());
            VoiceChatSecret secret = secrets.get(receiver.getUuid());
            if (conn != null && secret != null) {
                sendEncrypted(flushPacket, conn, secret);
            }
        }
    }

    public void flushReceiver(Player receiver) {
        Instance instance = receiver.getInstance();
        if (instance == null) {
            return;
        }
        ClientConnection conn = connections.get(receiver.getUuid());
        VoiceChatSecret secret = secrets.get(receiver.getUuid());
        if (conn == null || secret == null) {
            return;
        }
        for (Player other : instance.getPlayers()) {
            if (other.getUuid().equals(receiver.getUuid())) {
                continue;
            }
            sendEncrypted(buildGroupSoundPacket(other.getUuid(), new byte[0], 0L), conn, secret);
        }
    }

    public void close() {
        processThread.close();
        socket.close();
        packetQueue.clear();
        connections.clear();
        unCheckedConnections.clear();
        secrets.clear();
    }

    private void processRawPacket(RawUdpPacket rawPacket) {
        byte[] data = rawPacket.getData();
        if (data.length < 1 + 16 || System.currentTimeMillis() - rawPacket.getTimestamp() > DEFAULT_PACKET_TTL_MS) {
            return;
        }
        VoiceChatBuffer outer = new VoiceChatBuffer(data);
        if (outer.readByte() != MAGIC_BYTE) {
            return;
        }
        UUID playerId = outer.readUUID();
        VoiceChatSecret secret = secrets.get(playerId);
        if (secret == null) {
            return;
        }

        byte[] encryptedPayload;
        byte[] decrypted;
        try {
            encryptedPayload = outer.readByteArray(MAX_VOICE_CHAT_PACKET_SIZE);
            decrypted = secret.decrypt(encryptedPayload);
        } catch (Exception e) {
            return;
        }

        if (decrypted.length < 1) {
            return;
        }

        VoiceChatBuffer inner = new VoiceChatBuffer(decrypted);
        byte packetType = inner.readByte();
        long age = System.currentTimeMillis() - rawPacket.getTimestamp();
        long ttl = packetType == PACKET_MIC ? MIC_PACKET_TTL_MS : DEFAULT_PACKET_TTL_MS;
        if (age > ttl) {
            return;
        }

        SocketAddress senderAddress = rawPacket.getSocketAddress();

        switch (packetType) {
            case PACKET_AUTHENTICATE -> {
                UUID authPlayerUuid = inner.readUUID();
                VoiceChatSecret authSecret = VoiceChatSecret.fromBytes(inner);
                if (authPlayerUuid.equals(playerId) && secret.equals(authSecret)) {
                    ClientConnection connection = unCheckedConnections.get(authPlayerUuid);
                    if (connection == null) {
                        connection = connections.get(authPlayerUuid);
                    }
                    if (connection == null || !connection.address().equals(senderAddress)) {
                        connection = new ClientConnection(authPlayerUuid, senderAddress);
                        unCheckedConnections.put(authPlayerUuid, connection);
                    }
                    sendEmptyPacket(PACKET_AUTHENTICATE_ACK, connection, secret);
                }
            }
            case PACKET_CONNECTION_CHECK -> {
                ClientConnection unchecked = findConnection(unCheckedConnections, playerId, senderAddress);
                if (unchecked == null) {
                    ClientConnection existing = findConnection(connections, playerId, senderAddress);
                    if (existing != null) {
                        VoiceChatSecret existingSecret = secrets.get(existing.playerUuid());
                        if (existingSecret != null) {
                            sendEmptyPacket(PACKET_CONNECTION_CHECK_ACK, existing, existingSecret);
                        }
                    }
                    return;
                }
                unchecked.setLastKeepAliveResponse(System.currentTimeMillis());
                connections.put(unchecked.playerUuid(), unchecked);
                unCheckedConnections.remove(unchecked.playerUuid());
                sendEmptyPacket(PACKET_CONNECTION_CHECK_ACK, unchecked, secret);
                VoiceChatManager.onPlayerVoiceConnected(unchecked.playerUuid());
            }
            case PACKET_MIC -> {
                ClientConnection conn = findConnection(connections, playerId, senderAddress);
                if (conn == null) {
                    return;
                }
                byte[] opusData = inner.readByteArray(MAX_OPUS_PAYLOAD_SIZE);
                long sequenceNumber = inner.readLong();
                inner.readBoolean(); // whispering (ignored since voice chat is instance-wide static audio)
                onMicPacket(conn.playerUuid(), opusData, sequenceNumber);
            }
            case PACKET_KEEP_ALIVE -> {
                ClientConnection conn = findConnection(connections, playerId, senderAddress);
                if (conn != null) {
                    conn.setLastKeepAliveResponse(System.currentTimeMillis());
                }
            }
            case PACKET_PING -> {
                ClientConnection conn = findConnection(connections, playerId, senderAddress);
                if (conn != null) {
                    UUID pingId = inner.readUUID();
                    long pingTime = inner.readLong();
                    VoiceChatBuffer resp = new VoiceChatBuffer();
                    resp.writeByte(PACKET_PING);
                    resp.writeUUID(pingId);
                    resp.writeLong(pingTime);
                    sendEncrypted(resp.toByteArray(), conn, secret);
                }
            }
            default -> {
            }
        }
    }

    private void onMicPacket(UUID senderUuid, byte[] opusData, long sequenceNumber) {
        if (opusData.length > MAX_OPUS_PAYLOAD_SIZE) {
            return;
        }
        Player sender = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(senderUuid);
        if (sender == null || VoiceChatManager.isMuted(sender)) {
            return;
        }
        VoiceChatPlayerState senderState = VoiceChatManager.getState(senderUuid);
        if (senderState == null || senderState.disabled() || senderState.disconnected()) {
            return;
        }
        Instance instance = sender.getInstance();
        if (instance == null) {
            return;
        }

        // Build GroupSoundPacket (0x3) which Simple Voice Chat clients play as static (non-proximity) audio
        byte[] plaintextPacket = buildGroupSoundPacket(senderUuid, opusData, sequenceNumber);

        for (Player receiver : instance.getPlayers()) {
            UUID receiverUuid = receiver.getUuid();
            if (receiverUuid.equals(senderUuid)) {
                continue;
            }
            if (VoiceChatManager.isDeafened(receiver)) {
                continue;
            }
            VoiceChatPlayerState receiverState = VoiceChatManager.getState(receiverUuid);
            if (receiverState == null || receiverState.disabled() || receiverState.disconnected()) {
                continue;
            }
            ClientConnection receiverConn = connections.get(receiverUuid);
            VoiceChatSecret receiverSecret = secrets.get(receiverUuid);
            if (receiverConn == null || receiverSecret == null) {
                continue;
            }
            sendEncrypted(plaintextPacket, receiverConn, receiverSecret);
        }
    }

    private void sendKeepAlives() {
        long now = System.currentTimeMillis();
        connections.values().removeIf(conn -> {
            if (now - conn.lastKeepAliveResponse() >= KEEP_ALIVE_TIMEOUT_MS) {
                UUID uuid = conn.playerUuid();
                secrets.remove(uuid);
                VoiceChatManager.onPlayerVoiceDisconnected(uuid);
                Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
                if (player != null) {
                    VoiceChatManager.initializePlayerConnection(player);
                }
                return true;
            }
            return false;
        });

        for (ClientConnection conn : connections.values()) {
            VoiceChatSecret secret = secrets.get(conn.playerUuid());
            if (secret != null) {
                sendEmptyPacket(PACKET_KEEP_ALIVE, conn, secret);
            }
        }
    }

    private void sendEmptyPacket(byte packetType, ClientConnection connection, VoiceChatSecret secret) {
        sendEncrypted(new byte[]{packetType}, connection, secret);
    }

    private void sendEncrypted(byte[] plaintextPacket, ClientConnection connection, VoiceChatSecret secret) {
        try {
            byte[] encrypted = secret.encrypt(plaintextPacket);
            VoiceChatBuffer outer = new VoiceChatBuffer();
            outer.writeByte(MAGIC_BYTE);
            outer.writeByteArray(encrypted);
            socket.send(outer.toByteArray(), connection.address());
        } catch (Exception ignored) {
        }
    }

    private static final class ClientConnection {
        private final UUID playerUuid;
        private final SocketAddress address;
        private volatile long lastKeepAliveResponse;

        ClientConnection(UUID playerUuid, SocketAddress address) {
            this.playerUuid = playerUuid;
            this.address = address;
            this.lastKeepAliveResponse = System.currentTimeMillis();
        }

        UUID playerUuid() {
            return playerUuid;
        }

        SocketAddress address() {
            return address;
        }

        long lastKeepAliveResponse() {
            return lastKeepAliveResponse;
        }

        void setLastKeepAliveResponse(long lastKeepAliveResponse) {
            this.lastKeepAliveResponse = lastKeepAliveResponse;
        }
    }

    private final class ProcessThread extends Thread {
        private volatile boolean running = true;
        private long lastKeepAlive = 0L;

        ProcessThread() {
            setDaemon(true);
            setName("VoiceChatPacketProcessingThread");
        }

        @Override
        public void run() {
            while (running) {
                try {
                    long now = System.currentTimeMillis();
                    if (now - lastKeepAlive > KEEP_ALIVE_INTERVAL_MS) {
                        sendKeepAlives();
                        lastKeepAlive = now;
                    }
                    long wait = Math.max(1L, KEEP_ALIVE_INTERVAL_MS - (System.currentTimeMillis() - lastKeepAlive));
                    RawUdpPacket rawPacket = packetQueue.poll(wait, TimeUnit.MILLISECONDS);
                    if (rawPacket != null) {
                        processRawPacket(rawPacket);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        void close() {
            running = false;
            interrupt();
        }
    }
}
