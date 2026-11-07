package com.japicraft.environment;

import com.japicraft.Eris;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;

import java.util.*;
import java.util.function.Supplier;

public class InstanceManager {
    public static final Pos WORLD_ORIGIN = new Pos(0.0, 100.0, 0.0);
    public static final Pos DEFUSER_SPAWN = WORLD_ORIGIN.add(5.5, 1.0, 3.25);
    public static final Pos HELPER_SPAWN = WORLD_ORIGIN.add(7.75, 1.0, 4.5).withYaw(90.0F);
    public static final Pos EXPERT_SPAWN = WORLD_ORIGIN.add(5.5, 1.0, 7.75).withYaw(180.0F);
    public static Pos spawnFor(PlayerRole role) {
        return switch (role) {
            case BOMB_DEFUSER -> DEFUSER_SPAWN;
            case MANUAL_EXPERT -> EXPERT_SPAWN;
            case DEFUSAL_HELPER -> HELPER_SPAWN;
        };
    }

    public static Pos spawnFor(Player player) {
        PlayerRole role = PlayerRole.get(player);
        return role == null ? player.getRespawnPoint() : spawnFor(role);
    }
    private static final int MAX_INSTANCE_PLAYERS = 3;
    private final Map<UUID, Instance> playerToInstance = new HashMap<>();
    private final Map<UUID, PlayerRole> playerRoles = new HashMap<>();
    private final Map<Instance, Set<UUID>> instanceToPlayer = new LinkedHashMap<>();
    private final Map<UUID, Player> connectedPlayers = new HashMap<>();

    private Instance getNewInstance() {
        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);

        StructureLoader.loadBlocks(Eris.class.getClassLoader(), "depot.nbt", instance);
        StructureLoader.loadEntities(instance);

        return instance;
    }

    public synchronized void removeInstance(Instance instance) {
        Set<UUID> players = instanceToPlayer.get(instance);
        if (players == null || players.stream().anyMatch(connectedPlayers::containsKey)) {
            return;
        }
        instanceToPlayer.remove(instance);
        for (UUID uuid : players) {
            playerToInstance.remove(uuid);
            playerRoles.remove(uuid);
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> MinecraftServer.getInstanceManager().unregisterInstance(instance));
    }

    public synchronized Instance getPlayerInstance(Player player) {
        UUID playerId = player.getUuid();
        Instance existingInstance = playerToInstance.get(playerId);
        if (existingInstance != null) {
            Player existingPlayer = connectedPlayers.get(playerId);
            if (existingPlayer != null && existingPlayer != player) {
                throw new IllegalStateException("Player UUID already owns a connected seat: " + playerId);
            }
            connectedPlayers.put(playerId, player);
            player.setTag(PlayerRole.TAG, playerRoles.get(playerId));
            player.setRespawnPoint(spawnFor(playerRoles.get(playerId)));
            return existingInstance;
        }

        Instance instance = instanceToPlayer.entrySet().stream()
            .filter(entry -> entry.getValue().size() < MAX_INSTANCE_PLAYERS)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseGet(this::getNewInstance);

        playerToInstance.put(playerId, instance);
        connectedPlayers.put(playerId, player);

        Set<UUID> uuids = instanceToPlayer.getOrDefault(instance, new HashSet<>());

        Set<PlayerRole> usedRoles = EnumSet.noneOf(PlayerRole.class);
        uuids.forEach(id -> usedRoles.add(playerRoles.get(id)));
        PlayerRole role = Arrays.stream(PlayerRole.values())
            .filter(candidate -> !usedRoles.contains(candidate))
            .findFirst().orElseThrow();
        playerRoles.put(playerId, role);
        player.setTag(PlayerRole.TAG, role);
        player.setRespawnPoint(spawnFor(role));

        uuids.add(playerId);
        instanceToPlayer.put(instance, uuids);

        return instance;
    }

    public synchronized void removePlayer(Player player) {
        UUID uuid = player.getUuid();
        // A rejected/stale connection must never release another connection's seat.
        if (!connectedPlayers.remove(uuid, player)) {
            return;
        }
        Instance instance = playerToInstance.get(uuid);
        if (instance == null) {
            return;
        }
        removeInstance(instance);
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(getPlayerInstance(event.getPlayer())));
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> removePlayer(event.getPlayer()));
    }
}
