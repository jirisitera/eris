package com.japicraft.environment;

import com.japicraft.Eris;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.registry.RegistryKey;
import net.minestom.server.world.DimensionType;

public class InstanceManager {
    public static final Pos INITIAL_SPAWN = new Pos(0.5, 10.5, 0.5, 0.0F, 0.0F);
    public static final Pos MAIN_SPAWN = new Pos(8.5, 11.0, 4.5, 0.0F, 0.0F);

    public static InstanceContainer create(Player player) {
        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        // generate main structure
        StructureLoader.load(Eris.class.getClassLoader(), "office.nbt", instance);
        return instance;
    }
    public static void remove(Player player) {
        Instance instance = player.getInstance();
        MinecraftServer.getSchedulerManager().scheduleNextTick(() -> MinecraftServer.getInstanceManager().unregisterInstance(instance));
    }
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            Player player = event.getPlayer();

            event.setSpawningInstance(InstanceManager.create(player));
            player.setRespawnPoint(InstanceManager.INITIAL_SPAWN);
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerDisconnectEvent.class, event -> InstanceManager.remove(event.getPlayer()));
    }
}
