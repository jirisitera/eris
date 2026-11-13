package com.japicraft.lobby;

import com.japicraft.player.PlayerRole;

import java.util.*;

/**
 * role choices retained across disconnects, readiness reset; access guarded by GameStageManager
 */
public final class LobbySelection {
    private final Map<UUID, PlayerRole> roles = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>();

    public PlayerRole role(UUID player) {
        return roles.computeIfAbsent(player, ignored -> PlayerRole.BOMB_DEFUSER);
    }

    public void select(UUID player, PlayerRole selected) {
        Objects.requireNonNull(selected);
        if (role(player) == selected) return;
        roles.put(player, selected);
        ready.remove(player);
    }

    public void toggleReady(UUID player) {
        if (!ready.add(player)) ready.remove(player);
    }

    public void unready(UUID player) {
        ready.remove(player);
    }

    public void resetReady() {
        ready.clear();
    }

    public boolean isReady(UUID player) {
        return ready.contains(player);
    }

    public boolean uniqueRoles(Collection<UUID> players) {
        Set<PlayerRole> selected = EnumSet.noneOf(PlayerRole.class);
        return players.stream().allMatch(player -> selected.add(role(player)));
    }

    public boolean canStart(Collection<UUID> players) {
        return players.size() == PlayerRole.values().length
            && ready.containsAll(players) && uniqueRoles(players);
    }
}
