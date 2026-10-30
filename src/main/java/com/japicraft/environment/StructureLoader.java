package com.japicraft.environment;

import com.japicraft.Eris;
import com.japicraft.camera.MapProbe;
import net.hollowcube.schem.Schematic;
import net.hollowcube.schem.reader.SchematicReader;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class StructureLoader {
    public static final Tag<MonitorState> TAG = Tag.Transient("monitorState");
    private static final float[][] rotations = new float[][]{
        // computers
        new float[]{0.0F, 0.4226F, 0.0F, 0.9063F},
        new float[]{0.0F, -0.0175F, 0.0F, 1.0F},
        new float[]{0.0F, -0.3007F, 0.0F, 0.9537F},
        // cameras
        new float[]{0.2538F, 0.2424F, -0.0659F, 0.9341F},
        new float[]{0.1946F, -0.2848F, 0.0600F, 0.9367F},
        new float[]{0.2097F, 0.3610F, -0.0837F, 0.9048F},
        new float[]{0.2685F, -0.3417F, 0.1025F, 0.8948F}
    };
    private static final Pos[] positions = new Pos[]{
        // computers
        new Pos(9.8125, 12.5, 5.790771484375),
        new Pos(8.4375, 12.5, 6.1375),
        new Pos(7.125, 12.5, 5.875),
        // cameras
        new Pos(1.4375, 13.925, 1.375),
        new Pos(15.53515625, 13.925, 1.41796875),
        new Pos(1.479736328125, 13.925, 13.46142578125),
        new Pos(15.54638671875, 13.925, 13.45458984375)
    };
    private static final ItemStack DEACTIVATED = ItemStack.of(Material.ECHO_SHARD).withItemModel(Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "computer");
    private static final ItemStack CAMERA = ItemStack.of(Material.ECHO_SHARD).withItemModel(Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "camera");
    private final List<Entity> entities = new ArrayList<>();

    public static void load(ClassLoader loader, String path, InstanceContainer instance) {
        instance.loadChunk(0, 0);
        instance.loadChunk(1, 0);
        instance.loadChunk(0, 1);
        instance.loadChunk(1, 1);
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new RuntimeException("Could not find structure file in resources!");
            }
            byte[] data = stream.readAllBytes();
            Schematic structure = SchematicReader.structure().read(data);
            structure.createBatch().apply(instance, 0, 10, 0, _ -> Eris.LOGGER.atInfo().log(Component.text("Loaded structure " + path + " into the primary instance.")));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void activateLobby(Player player) {
        MonitorState state = player.getTag(StructureLoader.TAG);
        if (state == null) return;
        List<Entity> monitors = state.monitors();
        int index = MapProbe.LOBBY_MONITOR;
        for (int i = 0; i < 3; i++) {
            if (i != index) {
                MapProbe.deactivateMonitor(player, i);
                monitors.get(i).editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(DEACTIVATED));
            }
        }
        MapProbe.activateMonitor(player, index, monitors.get(index), MapProbe.lobbyDots());
    }

    public static void activate(Player player, int skipIndex) {
        MonitorState state = player.getTag(StructureLoader.TAG);
        if (state == null) return;
        List<Entity> monitors = state.monitors();
        for (int i = 0; i < 3; i++) {
            if (i == skipIndex) continue;
            // activate computer after a delay with its own data (0 = clock, 1 = energy, 2 = camera buttons)
            MapProbe.activateMonitor(player, i, monitors.get(i), switch (i) {
                case MapProbe.CLOCK_MONITOR -> List.of(MapProbe.clockDot(0, 0.0F));
                case 1 -> List.of(MapProbe.energyDot(0, 5, 10));
                default -> List.of();
            });
        }
    }

    public static void assignBaseEntityData(Player player) {
        Instance instance = player.getInstance();
        if (instance != null) {
            MonitorState state = instance.getTag(TAG);
            if (state != null) {
                player.setTag(TAG, state);
                return;
            }
        }
        player.removeTag(TAG);
    }

    public void spawnAll(InstanceContainer instance) {
        spawn(instance, 0, 3, StructureLoader.DEACTIVATED);
        spawn(instance, 3, 4, StructureLoader.CAMERA);
        instance.setTag(TAG, new MonitorState(List.copyOf(entities)));
    }

    public void spawn(InstanceContainer instance, int offset, int count, ItemStack item) {
        for (int i = offset; i < offset + count; i++) {
            int current = i;
            Entity entity = new Entity(EntityType.ITEM_DISPLAY);
            entities.add(entity);

            entity.editEntityMeta(ItemDisplayMeta.class, meta -> {
                meta.setItemStack(item);
                meta.setLeftRotation(rotations[current]);
                meta.setHasNoGravity(true);
            });
            entity.setInstance(instance, positions[current]);
        }
    }

    public void assignEntityData(Player player) {
        if (player != null) {
            player.setTag(StructureLoader.TAG, new MonitorState(List.copyOf(entities)));
        }
    }

    public record MonitorState(List<Entity> monitors) {
    }
}
