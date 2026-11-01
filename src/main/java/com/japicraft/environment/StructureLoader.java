package com.japicraft.environment;

import com.japicraft.Eris;
import net.hollowcube.schem.Schematic;
import net.hollowcube.schem.reader.SchematicReader;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.instance.Instance;
import net.minestom.server.timer.TaskSchedule;

import java.io.IOException;
import java.io.InputStream;

public final class StructureLoader {
    public static final Pos CASE_SPAWN = InstanceManager.MAIN_SPAWN.withYaw(180F).add(0, 1, 0.95);

    public static void loadBlocks(ClassLoader loader, String path, Instance instance) {
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

    public static void loadEntities(Instance instance) {

        Entity caseEntity = new Entity(EntityType.ITEM_DISPLAY);
        caseEntity.setNoGravity(true);
        caseEntity.setInstance(instance, StructureLoader.CASE_SPAWN);

        AnimationManager.spawn(caseEntity, "case");

        instance.scheduler().scheduleTask(() -> {
            AnimationManager.setTimer(caseEntity, 5, 0);
            AnimationManager.play(caseEntity, "case", "open");
        }, TaskSchedule.tick(100), TaskSchedule.stop());

        instance.scheduler().scheduleTask(() -> AnimationManager.startTimer(caseEntity, 5, 0, null), TaskSchedule.tick(200), TaskSchedule.stop());

        instance.scheduler().scheduleTask(() -> AnimationManager.addStrike(caseEntity), TaskSchedule.tick(300), TaskSchedule.stop());

        instance.scheduler().scheduleTask(() -> AnimationManager.addStrike(caseEntity), TaskSchedule.tick(400), TaskSchedule.stop());

        instance.scheduler().scheduleTask(() -> AnimationManager.addStrike(caseEntity), TaskSchedule.tick(500), TaskSchedule.stop());
    }
}
