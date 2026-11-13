package com.japicraft.environment;

import com.japicraft.Eris;
import com.japicraft.game.GameStageManager;
import com.japicraft.player.PlayerRole;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.tag.Tag;

import java.util.*;

public class InstanceManager {
    public static final Pos WORLD_ORIGIN = new Pos(0.0, 100.0, 0.0);
    public static final Pos DEFUSER_SPAWN = WORLD_ORIGIN.add(5.5, 1.0, 2.85).withYaw(-20.0F);
    public static final Pos HELPER_SPAWN = WORLD_ORIGIN.add(8.15, 1.0, 4.5).withYaw(70.0F);
    public static final Pos EXPERT_SPAWN = WORLD_ORIGIN.add(5.5, 1.0, 8.15).withYaw(-160.0F);
    public static final Pos LOBBY_CENTER = new Pos(6.5, 200, 6.5);
    public static final Pos LOBBY_CAMERA = LOBBY_CENTER.withPitch(5);
    private static final Pos LOBBY_AVATAR_CENTER = LOBBY_CENTER.add(0, -1.65, 6).withYaw(180);
    private static final List<Pos> LOBBY_SPAWNS = List.of(LOBBY_AVATAR_CENTER.add(-2.5, 0, 0), LOBBY_AVATAR_CENTER, LOBBY_AVATAR_CENTER.add(2.5, 0, 0));
    private static final Tag<Integer> LOBBY_SEAT = Tag.Integer("lobbySeat").defaultValue(0);
    private static final int MAX_INSTANCE_PLAYERS = 3;
    private final Map<UUID, Seat> seats = new HashMap<>();
    private final Map<Instance, Set<UUID>> instanceToPlayer = new LinkedHashMap<>();
    private final Map<UUID, Player> connectedPlayers = new HashMap<>();

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

    public static Pos lobbyAvatarPosition(Player player) {
        return LOBBY_SPAWNS.get(player.getTag(LOBBY_SEAT));
    }

    private Instance getNewInstance() {
        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);

        StructureLoader.loadBlocks(Eris.class.getClassLoader(), "depot.nbt", instance);

        return instance;
    }

    public synchronized void removeInstance(Instance instance) {
        Set<UUID> players = instanceToPlayer.get(instance);
        if (players == null || players.stream().anyMatch(connectedPlayers::containsKey)) {
            return;
        }
        instanceToPlayer.remove(instance);
        for (UUID uuid : players) {
            seats.remove(uuid);
        }
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
            StructureLoader.close(instance);
            MinecraftServer.getInstanceManager().unregisterInstance(instance);
        });
    }

    public synchronized Instance getPlayerInstance(Player player) {
        UUID playerId = player.getUuid();
        Seat existing = seats.get(playerId);
        if (existing != null) {
            Player existingPlayer = connectedPlayers.get(playerId);
            if (existingPlayer != null && existingPlayer != player) {
                throw new IllegalStateException("Player UUID already owns a connected seat: " + playerId);
            }
            connectedPlayers.put(playerId, player);
            player.setTag(LOBBY_SEAT, existing.index);
            player.setRespawnPoint(LOBBY_CAMERA);
            return existing.instance;
        }

        Instance instance = instanceToPlayer.entrySet().stream()
            .filter(entry -> entry.getValue().size() < MAX_INSTANCE_PLAYERS)
            .filter(entry -> GameStageManager.stage(entry.getKey()) == GameStageManager.Stage.LOBBY)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseGet(this::getNewInstance);

        connectedPlayers.put(playerId, player);

        Set<UUID> uuids = instanceToPlayer.getOrDefault(instance, new HashSet<>());

        int seat = uuids.size();
        seats.put(playerId, new Seat(instance, seat));
        player.setTag(LOBBY_SEAT, seat);
        player.setRespawnPoint(LOBBY_CAMERA);

        uuids.add(playerId);
        instanceToPlayer.put(instance, uuids);

        return instance;
    }

    public synchronized void removePlayer(Player player) {
        UUID uuid = player.getUuid();
        // seat ownership retained when rejecting stale connections
        if (!connectedPlayers.remove(uuid, player)) {
            return;
        }
        Seat seat = seats.get(uuid);
        if (seat != null) removeInstance(seat.instance);
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(getPlayerInstance(event.getPlayer())));
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> removePlayer(event.getPlayer()));
    }

    private record Seat(Instance instance, int index) {
    }
}
