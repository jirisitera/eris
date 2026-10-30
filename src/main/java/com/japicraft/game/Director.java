package com.japicraft.game;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.MapProbe;
import com.japicraft.controller.DoorController;
import com.japicraft.route.Layout;
import com.japicraft.route.PathNode;
import com.japicraft.route.Route;
import com.japicraft.events.PlayerSetupManager;
import com.japicraft.server.Notification;
import com.japicraft.server.PostEffect;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Macro-level AI Director.
 * <p>
 * Responsibilities include pacing of the game, tracking player camera habits via a per-camera Heatmap so enemies dynamically route around frequently checked cameras, some anti-cheese protection.
 * <p>
 */
public final class Director {
    public static final Tag<Director> TAG = Tag.Transient("enemyDirector");
    // anti-cheese tuning
    private static final int CAMERA_STABILIZE_TICKS = 8;      // 0.4s delay after switching before camera can ward
    private static final int STARE_WARNING_TICKS = 100;       // 5.0s continuous stare -> warning audio
    private static final int STARE_OVERLOAD_TICKS = 160;      // 8.0s continuous stare -> hallucination + camera jam
    private static final int CAMERA_JAM_DURATION_TICKS = 100; // 5.0s camera disabled after overload
    // clock settings
    private static final int CLOCK_HOUR_TICKS = 1200;
    private static final int CLOCK_FINAL_HOUR = 6;
    private static final int CLOCK_UPDATE_TICKS = 10;
    private final List<Enemy> bodies = new ArrayList<>();
    private final DoorController doors = new DoorController();
    // Camera usage tracking (1-indexed for cameras 1..4)
    private final float[] cameraHeat = new float[5];
    private int lastActiveCam = 0;
    private int currentCamStareTicks = 0;
    private int cameraStabilizeTicks = 0;
    private int jammedCamera = -1;
    private int cameraJamTicks = 0;
    private PacingPhase phase = PacingPhase.BUILDUP;
    private int phaseTicks = 0;
    private int matchTicks = 0;
    private int clockUpdateTicks = 0;
    private boolean gameOver = false;
    private boolean cleanedUp = false;

    public static Director createDefault() {
        Director director = new Director();
        // Adding new enemies is a single line referencing any node ID in FACILITY_GRAPH:
        director.spawnEnemy(EnemyType.STALKER, "storage_deep_left");
        director.spawnEnemy(EnemyType.LURKER, "generator_deep_right");
        return director;
    }

    public void spawnEnemy(EnemyType type, String startNodeId) {
        spawnEnemy(type, Layout.FACILITY_GRAPH, startNodeId);
    }

    public void spawnEnemy(EnemyType type, Route route, String startNodeId) {
        bodies.add(new Enemy(type, route, startNodeId));
    }

    public DoorController doors() {
        return doors;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public float getCameraHeat(int cameraIndex) {
        if (cameraIndex < 1 || cameraIndex >= cameraHeat.length) return 0.0F;
        return cameraHeat[cameraIndex];
    }

    public boolean isCameraJammed(int cameraIndex) {
        return cameraIndex > 0 && jammedCamera == cameraIndex && cameraJamTicks > 0;
    }

    public boolean isCameraFunctional(int cameraIndex) {
        if (cameraIndex <= 0) return false;
        if (cameraStabilizeTicks > 0) return false;
        return !isCameraJammed(cameraIndex);
    }

    public void start(InstanceContainer instance) {
        for (Enemy body : bodies) {
            body.spawn(instance);
        }
    }

    public void tick(Player player, InstanceContainer instance) {
        if (gameOver) return;

        matchTicks++;
        phaseTicks++;

        // win condition
        if (currentHour() >= CLOCK_FINAL_HOUR) {
            onVictory(player);
            return;
        }

        updateCameraTracking(player);

        // tick doors and energy consumption
        doors.tick(player, instance);

        updatePacing();

        // tick Action Bodies
        for (Enemy body : bodies) {
            body.tick(player, this, doors, instance);
        }

        // update the night clock on the left monitor every half-second
        clockUpdateTicks++;
        if (clockUpdateTicks >= CLOCK_UPDATE_TICKS) {
            clockUpdateTicks = 0;
            syncClock(player);
        }
    }

    public int currentHour() {
        return Math.min(CLOCK_FINAL_HOUR, matchTicks / CLOCK_HOUR_TICKS);
    }

    private void syncClock(Player player) {
        int hour = currentHour();
        float progress = hour >= CLOCK_FINAL_HOUR ? 1.0F : (float) (matchTicks % CLOCK_HOUR_TICKS) / CLOCK_HOUR_TICKS;
        MapProbe.setClock(player, hour, progress);
    }

    private void updateCameraTracking(Player player) {
        // slowly cool down all camera heat values over time
        for (int i = 1; i < cameraHeat.length; i++) {
            cameraHeat[i] = Math.max(0.0F, cameraHeat[i] * 0.995F);
        }

        if (cameraStabilizeTicks > 0) {
            cameraStabilizeTicks--;
        }
        if (cameraJamTicks > 0) {
            cameraJamTicks--;
            if (cameraJamTicks == 0) {
                if (CameraAnchor.getActiveCamera(player) == jammedCamera) {
                    // Restore normal camera shader once jam clears
                    PostEffect.sync(player);
                }
                jammedCamera = -1;
            }
        }

        int activeCam = CameraAnchor.getActiveCamera(player);

        // Detect camera switches
        if (activeCam != lastActiveCam) {
            lastActiveCam = activeCam;
            currentCamStareTicks = 0;
            if (activeCam > 0 && activeCam < cameraHeat.length) {
                // Switching to a camera adds a burst of heat and requires brief feed stabilization
                cameraHeat[activeCam] = Math.min(30.0F, cameraHeat[activeCam] + 1.2F);
                cameraStabilizeTicks = CAMERA_STABILIZE_TICKS;
            }
        }

        // Track continuous staring at a single camera
        if (activeCam > 0 && activeCam < cameraHeat.length) {
            cameraHeat[activeCam] = Math.min(30.0F, cameraHeat[activeCam] + 0.08F);

            if (jammedCamera != activeCam) {
                currentCamStareTicks++;

                if (currentCamStareTicks == STARE_WARNING_TICKS) {
                    // Subtle static hum warning that staring too long is overloading the feed
                    player.playSound(
                        Sound.sound(Key.key("minecraft:entity.enderman.stare"), Sound.Source.AMBIENT, 0.5F, 0.5F),
                        player.getPosition().x(), player.getPosition().y(), player.getPosition().z()
                    );
                } else if (currentCamStareTicks >= STARE_OVERLOAD_TICKS) {
                    triggerCameraHallucination(player, activeCam);
                }
            }
        } else {
            currentCamStareTicks = Math.max(0, currentCamStareTicks - 2);
        }
    }

    private void triggerCameraHallucination(Player player, int activeCam) {
        currentCamStareTicks = 0;
        jammedCamera = activeCam;
        cameraJamTicks = CAMERA_JAM_DURATION_TICKS;

        // Play audio hallucination / signal spike
        player.playSound(
            Sound.sound(Key.key("minecraft:entity.warden.listening_angry"), Sound.Source.HOSTILE, 0.85F, 1.6F),
            player.getPosition().x(), player.getPosition().y(), player.getPosition().z()
        );
        // Keep the heavy static distortion active while this camera is jammed
        player.sendPacket(new PostEffectsPacket(PostEffect.resolveActiveEffects(player, PostEffect.CAMERA_SWITCH)));
        Notification.warn(player, "CAMERA 0" + activeCam + " SIGNAL OVERLOAD");
    }

    public PathNode chooseNextNode(Enemy body, List<PathNode> candidates) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.getFirst();

        PathNode best = candidates.getFirst();
        float bestScore = Float.NEGATIVE_INFINITY;
        ThreadLocalRandom random = ThreadLocalRandom.current();

        for (PathNode candidate : candidates) {
            float score = 0.0F;
            int cam = candidate.cameraZone();
            if (cam > 0 && cam < cameraHeat.length) {
                // Penalize cameras the player checks often, scaled by this enemy's avoidance weight
                score -= cameraHeat[cam] * body.type().cameraAvoidanceWeight();

                // Extra penalty if the player is actively looking at that camera right now
                if (cam == lastActiveCam) {
                    score -= 6.0F * body.type().cameraAvoidanceWeight();
                }
            }

            // Slight penalty if another enemy is already standing on or traveling to that node to encourage spreading out
            boolean occupied = bodies.stream()
                .anyMatch(other -> other != body
                    && other.state() != Enemy.State.DORMANT
                    && (other.currentNode() == candidate || other.targetNode() == candidate));
            if (occupied) {
                score -= 4.0F;
            }

            // Small random jitter so routes aren't 100% deterministic
            score += random.nextFloat() * 2.5F;

            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private void updatePacing() {
        boolean anyAtDoor = bodies.stream().anyMatch(b -> b.state() == Enemy.State.WAITING_AT_DOOR);

        switch (phase) {
            case BUILDUP -> {
                if (anyAtDoor) {
                    phase = PacingPhase.PEAK;
                    phaseTicks = 0;
                } else if (phaseTicks > 1200) { // after 60s of buildup, naturally elevate threat
                    phase = PacingPhase.PEAK;
                    phaseTicks = 0;
                }
            }
            case PEAK -> {
                // If all door threats were warded, grant reprieve
                if (!anyAtDoor && phaseTicks > 100) {
                    phase = PacingPhase.RECOVERY;
                    phaseTicks = 0;
                }
            }
            case RECOVERY -> {
                if (phaseTicks >= 200) { // 10s breathing room
                    phase = PacingPhase.BUILDUP;
                    phaseTicks = 0;
                }
            }
        }
    }

    public boolean canAdvance(Enemy requester) {
        if (phase == PacingPhase.RECOVERY) {
            return false;
        }

        // Avoid dogpiling: if an enemy is already at the office door or traveling there, others hold back in the corridor
        boolean anotherAlreadyAtDoor = bodies.stream()
            .anyMatch(b -> b != requester && (b.state() == Enemy.State.WAITING_AT_DOOR
                || (b.state() == Enemy.State.RELOCATING && b.targetNode() != null && b.targetNode().isDoorApproach())));

        return !anotherAlreadyAtDoor || !requester.currentNode().isDoorApproach();
    }

    public void onBreach(Enemy intruder, Player player) {
        if (gameOver) return;
        gameOver = true;

        if (!CameraAnchor.isMainCamera(player)) {
            CameraAnchor.setCamera(player, 0);
            PostEffect.sync(player);
        }

        player.playSound(
            Sound.sound(Key.key("minecraft:entity.warden.roar"), Sound.Source.HOSTILE, 1.0F, 0.8F),
            player.getPosition().x(), player.getPosition().y(), player.getPosition().z()
        );
        Notification.warn(player, "GAME OVER");

        // Allow 2.5s (50 ticks) for the breach jumpscare/roar before resetting to Lobby
        scheduleLobbyReset(player);
    }

    public void onVictory(Player player) {
        if (gameOver) return;
        gameOver = true;

        if (!CameraAnchor.isMainCamera(player)) {
            CameraAnchor.setCamera(player, 0);
            PostEffect.sync(player);
        }

        MapProbe.setClock(player, CLOCK_FINAL_HOUR, 1.0F);
        player.playSound(
            Sound.sound(Key.key("minecraft:ui.toast.challenge_complete"), Sound.Source.MASTER, 1.0F, 1.0F),
            player.getPosition().x(), player.getPosition().y(), player.getPosition().z()
        );
        Notification.success(player, "YOU SURVIVED THE NIGHT!");

        scheduleLobbyReset(player);
    }

    private void scheduleLobbyReset(Player player) {
        player.scheduler().scheduleTask(() -> {
            if (!player.isOnline()) return;
            resetToLobby(player);
        }, TaskSchedule.tick(50), TaskSchedule.stop());
    }

    public void resetToLobby(Player player) {
        if (cleanedUp) return;
        cleanedUp = true;
        gameOver = true;

        cleanup();
        player.removeTag(Director.TAG);

        if (player.getInstance() instanceof InstanceContainer container) {
            doors.reset(container);
        }

        PostEffect.removeBlackout(player);
        if (!CameraAnchor.isMainCamera(player)) {
            CameraAnchor.setCamera(player, 0);
        }
        PostEffect.sync(player);

        Lobby.enter(player);
    }

    public void cleanup() {
        cleanedUp = true;
        for (Enemy body : bodies) {
            body.despawn();
        }
        bodies.clear();
    }

    public enum PacingPhase {
        BUILDUP,
        PEAK,
        RECOVERY
    }
}
