package com.japicraft.environment;

import com.japicraft.bomb.BombModules;
import com.japicraft.game.GameRound;
import com.japicraft.game.GameStageManager;
import com.japicraft.game.RoundSummary;
import com.japicraft.service.AnimationService;
import net.hollowcube.schem.Schematic;
import net.hollowcube.schem.reader.SchematicReader;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.instance.block.Block;
import net.minestom.server.tag.Tag;
import net.minestom.server.tag.Taggable;
import net.minestom.server.timer.TaskSchedule;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class StructureLoader {
    public static final Pos WORLD_ORIGIN = InstanceManager.WORLD_ORIGIN;
    public static final Pos CASE_SPAWN = InstanceManager.DEFUSER_SPAWN.withYaw(180F).add(0, 1, 1.25);
    public static final Pos MANUAL_SPAWN = InstanceManager.EXPERT_SPAWN.withYaw(90F).add(0, 1, -1.25);
    private static final Tag<Entity> CASE_ENTITY = Tag.Transient("caseEntity");
    private static final Tag<GameRound> ROUND = Tag.Transient("gameRound");
    // instance-owned lobby camera and player-owned game cameras
    private static final Tag<CameraTarget> CAMERA = Tag.Transient("sceneCamera");

    public static synchronized CameraTarget cameraFor(Player player, boolean playing, Pos position) {
        Taggable owner = playing ? player : player.getInstance();
        CameraTarget camera = owner.getTag(CAMERA);
        if (camera == null || camera.entity.isRemoved()) {
            Entity entity = new Entity(EntityType.ITEM_DISPLAY);
            entity.setNoGravity(true);
            // precise initial lobby camera angle without interpolation
            entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setPosRotInterpolationDuration(playing ? 2 : 0));
            camera = new CameraTarget(entity, entity.setInstance(player.getInstance(), position));
            owner.setTag(CAMERA, camera);
        }
        return camera;
    }

    public static void removePlayerCamera(Player player) {
        CameraTarget camera = player.getTag(CAMERA);
        player.removeTag(CAMERA);
        if (camera != null) camera.ready.thenRun(camera.entity::remove);
    }

    public static void loadBlocks(ClassLoader loader, String path, Instance instance) {
        instance.setBlock(6, 199, 6, Block.WATER, false);
        instance.setBlock(6, 200, 6, Block.WATER, false);
        instance.setBlock(6, 201, 6, Block.WATER, false);
        instance.setBlock(6, 202, 6, Block.WATER, false);

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
        if (instance.hasTag(CASE_ENTITY)) {
            return;
        }
        Entity caseEntity = new Entity(EntityType.ITEM_DISPLAY);
        caseEntity.setNoGravity(true);
        caseEntity.setInstance(instance, StructureLoader.CASE_SPAWN);

        AnimationService.spawn(caseEntity, "case");

        instance.setTag(CASE_ENTITY, caseEntity);
        AnimationService.setTimer(caseEntity, 5, 0);

        Entity manualEntity = new Entity(EntityType.ITEM_DISPLAY);
        manualEntity.setNoGravity(true);
        manualEntity.setInstance(instance, MANUAL_SPAWN);
        AnimationService.spawn(manualEntity, "manual");
        BombModules.create(instance, () -> mistake(instance));
        AnimationService.setStrikes(caseEntity, 0);
    }

    /**
     * called once on the lobby-to-game transition, never on a player's rejoin
     */
    public static void startGame(Instance instance) {
        loadEntities(instance);
        if (instance.hasTag(ROUND)) {
            return;
        }
        GameRound round = new GameRound();
        instance.setTag(ROUND, round);

        Entity caseEntity = instance.getTag(CASE_ENTITY);
        BombModules.get(instance).start();
        AnimationService.setTimer(caseEntity, 5, 0);
        AnimationService.play(caseEntity, "case", "closed");

        instance.scheduler().scheduleTask(() -> AnimationService.play(caseEntity, "case", "open"), TaskSchedule.seconds(4), TaskSchedule.stop());

        instance.scheduler().scheduleTask(() -> AnimationService.startTimer(caseEntity, 5, 0, () -> {
            if (instance.getTag(ROUND) == round && round.expire()) {
                StructureLoader.queueLoss(instance, round, "Time ran out");
            }
        }), TaskSchedule.seconds(10), TaskSchedule.stop());
    }

    /**
     * the case is fully open when its countdown starts
     */
    public static boolean isBombOpen(Instance instance) {
        if (instance == null || !instance.hasTag(ROUND)) return false;
        Entity caseEntity = instance.getTag(CASE_ENTITY);
        return caseEntity != null && AnimationService.isTimerRunning(caseEntity);
    }

    private static void mistake(Instance instance) {
        GameRound round = instance.getTag(ROUND);
        if (round == null) {
            return;
        }
        boolean lost = round.mistake();
        AnimationService.setStrikes(instance.getTag(CASE_ENTITY), round.strikes());
        if (lost) {
            StructureLoader.queueLoss(instance, round, "Too many mistakes");
        }
    }

    private static void queueLoss(Instance instance, GameRound round, String message) {
        // module reset after the current input callback
        instance.scheduler().scheduleNextTick(() -> {
            if (instance.getTag(ROUND) == round) GameStageManager.lose(instance, message);
        });
    }

    public static void resetGame(Instance instance) {
        instance.removeTag(ROUND);
        Entity caseEntity = instance.getTag(CASE_ENTITY);
        if (caseEntity == null) {
            return;
        }
        AnimationService.stopTimer(caseEntity);
        AnimationService.setTimer(caseEntity, 5, 0);
        AnimationService.setStrikes(caseEntity, 0);
        AnimationService.stopAnimation(caseEntity, "open");
        BombModules bomb = BombModules.get(instance);
        if (bomb != null) {
            bomb.reset();
        }
    }

    /**
     * final instance disposal; round transitions only reset existing entities
     */
    public static void close(Instance instance) {
        instance.removeTag(ROUND);
        instance.removeTag(CAMERA);
        BombModules bomb = BombModules.get(instance);
        if (bomb != null) {
            bomb.close();
        }
        instance.removeTag(CASE_ENTITY);
        for (Entity entity : List.copyOf(instance.getEntities())) {
            if (entity instanceof Player) continue;
            AnimationService.stop(entity);
            entity.remove();
        }
    }

    public static RoundSummary summary(Instance instance, String reason) {
        Entity caseEntity = instance.getTag(CASE_ENTITY);
        GameRound round = instance.getTag(ROUND);
        BombModules bomb = BombModules.get(instance);
        return new RoundSummary(reason, round == null ? 0 : round.strikes(),
            caseEntity == null ? 0 : AnimationService.getTimerSeconds(caseEntity),
            bomb == null ? 0 : bomb.solvedCount(), bomb == null ? 0 : bomb.totalCount()
        );
    }

    public record CameraTarget(Entity entity, CompletableFuture<Void> ready) {
    }
}
