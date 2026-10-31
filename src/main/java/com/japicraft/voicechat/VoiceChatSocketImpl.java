package com.japicraft.voicechat;

import de.maxhenkel.voicechat.api.RawUdpPacket;
import de.maxhenkel.voicechat.api.VoicechatSocket;

import java.net.*;
import java.util.Arrays;

public final class VoiceChatSocketImpl implements VoicechatSocket {
    private final byte[] buffer = new byte[4096];
    private DatagramSocket socket;

    @Override
    public void open(int port, String bindAddress) throws Exception {
        if (socket != null) {
            throw new IllegalStateException("Socket already opened");
        }
        InetAddress address = null;
        if (bindAddress != null && !bindAddress.isBlank() && !bindAddress.equals("*")) {
            address = InetAddress.getByName(bindAddress);
        }
        socket = address != null ? new DatagramSocket(port, address) : new DatagramSocket(port);
        socket.setTrafficClass(0x04); // IPTOS_RELIABILITY
    }

    @Override
    public RawUdpPacket read() throws Exception {
        if (socket == null) {
            throw new IllegalStateException("Socket not opened yet");
        }
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);
        long timestamp = System.currentTimeMillis();
        if (packet.getLength() >= buffer.length) {
            throw new ProtocolException("Packet from " + packet.getSocketAddress() + " is too large");
        }
        byte[] data = Arrays.copyOfRange(packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());
        SocketAddress socketAddress = packet.getSocketAddress();
        return new RawUdpPacket() {
            @Override
            public byte[] getData() {
                return data;
            }

            @Override
            public long getTimestamp() {
                return timestamp;
            }

            @Override
            public SocketAddress getSocketAddress() {
                return socketAddress;
            }
        };
    }

    @Override
    public void send(byte[] data, SocketAddress address) throws Exception {
        if (socket == null || socket.isClosed()) {
            return;
        }
        socket.send(new DatagramPacket(data, data.length, address));
    }

    @Override
    public int getLocalPort() {
        return socket != null ? socket.getLocalPort() : -1;
    }

    @Override
    public void close() {
        if (socket != null) {
            try {
                socket.close();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public boolean isClosed() {
        return socket == null || socket.isClosed();
    }
}
