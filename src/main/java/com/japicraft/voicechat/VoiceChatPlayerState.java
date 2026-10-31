package com.japicraft.voicechat;

import java.util.UUID;

public final class VoiceChatPlayerState {
    private final UUID uuid;
    private final String name;
    private volatile boolean disabled;
    private volatile boolean disconnected;

    public VoiceChatPlayerState(UUID uuid, String name, boolean disabled, boolean disconnected) {
        this.uuid = uuid;
        this.name = name;
        this.disabled = disabled;
        this.disconnected = disconnected;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public boolean disabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean disconnected() {
        return disconnected;
    }

    public void setDisconnected(boolean disconnected) {
        this.disconnected = disconnected;
    }

    public void toBytes(VoiceChatBuffer buf) {
        buf.writeBoolean(disabled);
        buf.writeBoolean(disconnected);
        buf.writeUUID(uuid);
        buf.writeUtf(name);
        buf.writeBoolean(false); // hasGroup = false
    }
}
