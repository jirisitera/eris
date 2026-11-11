package com.japicraft.environment;

import com.japicraft.bomb.BombModules;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

public final class StructureLoader {
    public static final Pos WORLD_ORIGIN = InstanceManager.WORLD_ORIGIN;
    public static final Pos CASE_SPAWN = InstanceManager.DEFUSER_SPAWN.withYaw(180F).add(0, 1, 1.25);
    public static final Pos MANUAL_SPAWN = InstanceManager.EXPERT_SPAWN.withYaw(90F).add(0, 1, -1.25);
    private static final Tag<Entity> CASE_ENTITY = Tag.Transient("caseEntity");
    private static final Tag<GameRound> ROUND = Tag.Transient("gameRound");
    // The instance owns its shared lobby camera; each player owns their game camera.
    private static final Tag<CameraTarget> CAMERA = Tag.Transient("sceneCamera");

    public static synchronized CameraTarget cameraFor(Player player, boolean playing, Pos position) {
        Taggable owner = playing ? player : player.getInstance();
        CameraTarget camera = owner.getTag(CAMERA);
        if (camera == null || camera.entity.isRemoved()) {
            Entity entity = new Entity(EntityType.ITEM_DISPLAY);
            entity.setNoGravity(true);
            // The fixed lobby camera must apply its initial precise angle without interpolation.
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
        if (instance.hasTag(CASE_ENTITY)) return;

        Entity caseEntity = new Entity(EntityType.ITEM_DISPLAY);
        caseEntity.setNoGravity(true);
        caseEntity.setInstance(instance, StructureLoader.CASE_SPAWN);

        AnimationManager.spawn(caseEntity, "case");

        instance.setTag(CASE_ENTITY, caseEntity);
        AnimationManager.setTimer(caseEntity, 5, 0);

        Entity manualEntity = new Entity(EntityType.ITEM_DISPLAY);
        manualEntity.setNoGravity(true);
        manualEntity.setInstance(instance, MANUAL_SPAWN);
        AnimationManager.spawn(manualEntity, "manual");
        BombModules.create(instance, () -> mistake(instance));
        AnimationManager.setStrikes(caseEntity, 0);
    }

    /**
     * Called once on the lobby-to-game transition, never on a player's rejoin.
     */
    public static void startGame(Instance instance) {
        loadEntities(instance);
        if (instance.hasTag(ROUND)) return;
        GameRound round = new GameRound();
        instance.setTag(ROUND, round);
        instance.scheduler().scheduleNextTick(() -> {
            if (instance.getTag(ROUND) != round) return;
            Entity caseEntity = instance.getTag(CASE_ENTITY);
            BombModules.get(instance).start();
            AnimationManager.play(caseEntity, "case", "open");
            AnimationManager.setStrikes(caseEntity, 0);
            AnimationManager.startTimer(caseEntity, 5, 0, () -> {
                if (instance.getTag(ROUND) == round && round.expire()) queueLoss(instance, round, "Time ran out");
            });
        });
    }

    private static void mistake(Instance instance) {
        GameRound round = instance.getTag(ROUND);
        if (round == null) return;
        boolean lost = round.mistake();
        AnimationManager.setStrikes(instance.getTag(CASE_ENTITY), round.strikes());
        if (lost) queueLoss(instance, round, "Fourth mistake");
    }

    private static void queueLoss(Instance instance, GameRound round, String message) {
        // Finish the current input callback before resetting module state.
        instance.scheduler().scheduleNextTick(() -> {
            if (instance.getTag(ROUND) == round) GameStageManager.lose(instance, message);
        });
    }

    public static void resetGame(Instance instance) {
        instance.removeTag(ROUND);
        Entity caseEntity = instance.getTag(CASE_ENTITY);
        if (caseEntity == null) return;
        AnimationManager.stopTimer(caseEntity);
        AnimationManager.setTimer(caseEntity, 5, 0);
        AnimationManager.setStrikes(caseEntity, 0);
        AnimationManager.stopAnimation(caseEntity, "open");
        BombModules bomb = BombModules.get(instance);
        if (bomb != null) bomb.reset();
    }

    /**
     * Final instance disposal; round transitions only reset existing entities.
     */
    public static void close(Instance instance) {
        instance.removeTag(ROUND);
        instance.removeTag(CAMERA);
        BombModules bomb = BombModules.get(instance);
        if (bomb != null) bomb.close();
        instance.removeTag(CASE_ENTITY);
        for (Entity entity : java.util.List.copyOf(instance.getEntities())) {
            if (entity instanceof net.minestom.server.entity.Player) continue;
            AnimationManager.stop(entity);
            entity.remove();
        }
    }

    public static RoundSummary summary(Instance instance, String reason) {
        Entity caseEntity = instance.getTag(CASE_ENTITY);
        GameRound round = instance.getTag(ROUND);
        BombModules bomb = BombModules.get(instance);
        return new RoundSummary(reason, round == null ? 0 : round.strikes(),
            caseEntity == null ? 0 : AnimationManager.getTimerSeconds(caseEntity),
            bomb == null ? 0 : bomb.solvedCount(), bomb == null ? 0 : bomb.totalCount());
    }

    public record CameraTarget(Entity entity, CompletableFuture<Void> ready) {
    }
}
