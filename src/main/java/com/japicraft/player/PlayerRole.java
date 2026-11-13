package com.japicraft.player;

import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;

public enum PlayerRole {
    BOMB_DEFUSER("Defuzér bomby (slepý)"),
    MANUAL_EXPERT("Expert s manuálem (hluchý)"),
    DEFUSAL_HELPER("Komunikátor (němý)");

    public static final Tag<PlayerRole> TAG = Tag.Transient("instanceRole");
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
