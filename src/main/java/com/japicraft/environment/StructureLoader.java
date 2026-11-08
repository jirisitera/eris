package com.japicraft.environment;

import com.japicraft.bomb.BombModules;
import net.hollowcube.schem.Schematic;
import net.hollowcube.schem.reader.SchematicReader;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;

import java.io.IOException;
import java.io.InputStream;

public final class StructureLoader {
    public static final Pos WORLD_ORIGIN = InstanceManager.WORLD_ORIGIN;
    public static final Pos CASE_SPAWN = InstanceManager.DEFUSER_SPAWN.withYaw(180F).add(0, 1, 1.25);
    private static final Tag<Entity> CASE_ENTITY = Tag.Transient("caseEntity");
    private static final Tag<Boolean> CASE_STARTED = Tag.Transient("caseStarted");

    public static void loadBlocks(ClassLoader loader, String path, Instance instance) {
        instance.loadChunk(0, 0);
        try (InputStream stream = loader.getResourceAsStream(path)) {
            if (stream == null) {
                throw new RuntimeException("Could not find structure file in resources!");
            }
            Schematic structure = SchematicReader.structure().read(stream.readAllBytes());
            structure.createBatch().apply(instance, WORLD_ORIGIN, null);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void loadEntities(Instance instance) {

        Entity caseEntity = new Entity(EntityType.ITEM_DISPLAY);
        caseEntity.setNoGravity(true);
        caseEntity.setInstance(instance, StructureLoader.CASE_SPAWN);

        AnimationManager.spawn(caseEntity, "case");

        instance.setTag(CASE_ENTITY, caseEntity);
        AnimationManager.setTimer(caseEntity, 5, 0);
    }

    /**
     * Called once on the lobby-to-game transition, never on a player's rejoin.
     */
    public static void startGame(Instance instance) {
        instance.scheduler().scheduleNextTick(() -> {
            if (Boolean.TRUE.equals(instance.getTag(CASE_STARTED))) return;
            Entity caseEntity = instance.getTag(CASE_ENTITY);
            if (caseEntity == null || caseEntity.isRemoved()) return;
            instance.setTag(CASE_STARTED, true);

            AnimationManager.play(caseEntity, "case", "open");
            AnimationManager.startTimer(caseEntity, 5, 0, null);
            BombModules.create(instance, () -> AnimationManager.addStrike(caseEntity));
        });
    }
}
