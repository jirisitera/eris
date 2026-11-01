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

public class InstanceManager {
    public static final Pos MAIN_SPAWN = new Pos(8.5, 11.0, 4.5, 0.0F, 0.0F);
    public static final Pos[] SPAWNS = {
        MAIN_SPAWN,
        MAIN_SPAWN,
        MAIN_SPAWN
    };
    private static final int MAX_INSTANCE_PLAYERS = 3;
    private final Map<UUID, Instance> playerToInstance = new HashMap<>();
    private final Map<Instance, Set<UUID>> instanceToPlayer = new LinkedHashMap<>();

    private Instance getNewInstance() {
        Instance instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);

        StructureLoader.loadBlocks(Eris.class.getClassLoader(), "office.nbt", instance);
        StructureLoader.loadEntities(instance);

        return instance;
    }

    public synchronized void removeInstance(Instance instance) {
        Set<UUID> players = instanceToPlayer.get(instance);
        if (players != null && !players.isEmpty()) {
            return;
        }
        instanceToPlayer.remove(instance);
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> MinecraftServer.getInstanceManager().unregisterInstance(instance));
    }

    public synchronized Instance getPlayerInstance(Player player) {
        UUID playerId = player.getUuid();
        Instance existingInstance = playerToInstance.get(playerId);
        if (existingInstance != null) {
            return existingInstance;
        }

        Instance instance = instanceToPlayer.entrySet().stream()
            .filter(entry -> entry.getValue().size() < MAX_INSTANCE_PLAYERS)
            .map(Map.Entry::getKey)
            .findFirst()
            .orElseGet(this::getNewInstance);

        playerToInstance.put(playerId, instance);

        Set<UUID> uuids = instanceToPlayer.getOrDefault(instance, new HashSet<>());

        player.setRespawnPoint(InstanceManager.SPAWNS[uuids.size()]);

        uuids.add(playerId);
        instanceToPlayer.put(instance, uuids);

        return instance;
    }

    public synchronized void removePlayer(Player player) {
        Instance instance = playerToInstance.remove(player.getUuid());
        if (instance == null) {
            return;
        }

        Set<UUID> players = instanceToPlayer.get(instance);
        players.remove(player.getUuid());
        if (players.isEmpty()) {
            removeInstance(instance);
        }
    }

    public void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> event.setSpawningInstance(getPlayerInstance(event.getPlayer())));
        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> removePlayer(event.getPlayer()));
    }
}
