package com.japicraft.environment;

import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;

public enum PlayerRole {
    BOMB_DEFUSER("Bomb defuser"),
    MANUAL_EXPERT("Manual expert"),
    DEFUSAL_HELPER("Defusal helper");

    static final Tag<PlayerRole> TAG = Tag.Transient("instanceRole");
    private final String displayName;

    PlayerRole(String displayName) {
        this.displayName = displayName;
    }

    public static PlayerRole get(Player player) {
        return player.getTag(TAG);
    }

    public String displayName() {
        return displayName;
    }
}
