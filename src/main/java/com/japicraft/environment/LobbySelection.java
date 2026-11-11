package com.japicraft.environment;

import java.util.*;

/**
 * Role choices survive disconnects; readiness never does. Guarded by GameStageManager.
 */
final class LobbySelection {
    private final Map<UUID, PlayerRole> roles = new HashMap<>();
    private final Set<UUID> ready = new HashSet<>();

    PlayerRole role(UUID player) {
        return roles.computeIfAbsent(player, ignored -> PlayerRole.BOMB_DEFUSER);
    }

    void select(UUID player, PlayerRole selected) {
        Objects.requireNonNull(selected);
        if (role(player) == selected) return;
        roles.put(player, selected);
        ready.remove(player);
    }

    void toggleReady(UUID player) {
        if (!ready.add(player)) ready.remove(player);
    }

    void unready(UUID player) {
        ready.remove(player);
    }

    void resetReady() {
        ready.clear();
    }

    boolean isReady(UUID player) {
        return ready.contains(player);
    }

    boolean uniqueRoles(Collection<UUID> players) {
        Set<PlayerRole> selected = EnumSet.noneOf(PlayerRole.class);
        return players.stream().allMatch(player -> selected.add(role(player)));
    }

    boolean canStart(Collection<UUID> players) {
        return players.size() == PlayerRole.values().length
            && ready.containsAll(players) && uniqueRoles(players);
    }
}
