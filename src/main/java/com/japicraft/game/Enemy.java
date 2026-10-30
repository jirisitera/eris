package com.japicraft.game;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.controller.DoorController;
import com.japicraft.route.PathNode;
import com.japicraft.route.Route;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;

import java.util.List;

public class Enemy {
    // Anti-cheese: Door must be held closed for at least 25 ticks (~1.25s) to repel an enemy,
    // preventing 1-tick door flicking to save battery.
    private static final int DOOR_HOLD_TO_WARD_TICKS = 25;
    private static final int WARDED_COOLDOWN_TICKS = 160;
    private static final int RETREAT_VANISH_TICKS = 25;
    private final EnemyType type;
    private final Route route;
    private final String startNodeId;
    private PathNode currentNode;
    private PathNode targetNode;
    private State state = State.DORMANT;
    private Entity entity;
    private int observedTicks = 0;
    private int doorWaitTicks = 0;
    private int doorBlockedTicks = 0;
    private int moveCooldownTicks;
    private int wardedCooldownTicks = 0;
    private int transitTicks = 0;
    private int retreatDelayTicks = 0;

    public Enemy(EnemyType type, Route route, String startNodeId) {
        this.type = type;
        this.route = route;
        this.startNodeId = startNodeId;
        this.currentNode = route.requireNode(startNodeId);
        this.moveCooldownTicks = type.baseMoveIntervalTicks();
    }

    public EnemyType type() {
        return type;
    }

    public Route route() {
        return route;
    }

    public State state() {
        return state;
    }

    public PathNode currentNode() {
        return currentNode;
    }

    public PathNode targetNode() {
        return targetNode;
    }

    public Entity entity() {
        return entity;
    }

    public void spawn(InstanceContainer instance) {
        if (entity != null) return;
        currentNode = route.requireNode(startNodeId);
        targetNode = null;
        state = State.STALKING;
        moveCooldownTicks = type.baseMoveIntervalTicks();
        spawnAt(instance, currentNode.position());
    }

    public void spawnAt(InstanceContainer instance, Pos pos) {
        vanish();
        LivingEntity living = new LivingEntity(type.entityType());
        living.setNoGravity(true);
        living.setInstance(instance, pos);
        this.entity = living;
    }

    public void vanish() {
        if (entity != null) {
            entity.remove();
            entity = null;
        }
    }

    private void playVanishEffects(Player player, Pos pos) {
        player.sendPacket(new ParticlePacket(
            Particle.POOF,
            pos.x(),
            pos.y() + 1.0,
            pos.z(),
            0.3F,
            0.5F,
            0.3F,
            0.05F,
            12
        ));
        player.playSound(
            Sound.sound(Key.key("minecraft:entity.enderman.teleport"), Sound.Source.HOSTILE, 0.4F, 1.4F),
            pos.x(), pos.y(), pos.z()
        );
    }

    private void playReappearEffects(Player player, Pos pos) {
        player.sendPacket(new ParticlePacket(
            Particle.SMOKE,
            pos.x(),
            pos.y() + 0.8,
            pos.z(),
            0.25F,
            0.4F,
            0.25F,
            0.02F,
            8
        ));
    }

    public void tick(Player player, Director director, DoorController doors, InstanceContainer instance) {
        if (state == State.DORMANT || state == State.BREACHED) {
            return;
        }

        // 1. Relocating state: enemy has vanished from old node and is traveling to targetNode
        if (state == State.RELOCATING) {
            transitTicks--;
            if (transitTicks <= 0) {
                completeRelocation(instance, player, director);
            }
            return;
        }

        PathNode node = currentNode;
        int activeCam = CameraAnchor.getActiveCamera(player);

        // 2. Camera Gaze detection: Only counts if the camera is functional (not jammed and finished switching)
        boolean watchedByPlayer = (activeCam > 0
            && node.cameraZone() == activeCam
            && director.isCameraFunctional(activeCam));

        if (watchedByPlayer) {
            observedTicks++;
            if (state == State.STALKING) {
                state = State.OBSERVED;
            }
            if (observedTicks >= type.gazeToleranceTicks()) {
                ward(WardReason.CAMERA_GAZE, instance, player, director);
                return;
            }
        } else {
            observedTicks = Math.max(0, observedTicks - 1);
            if (state == State.OBSERVED) {
                state = node.isDoorApproach() ? State.WAITING_AT_DOOR : State.STALKING;
            }
        }

        // 3. Door Approach handling
        if (state == State.WAITING_AT_DOOR) {
            boolean doorClosed = doors.isClosed(node.isLeftFlank());
            if (doorClosed) {
                doorBlockedTicks++;
                if (doorBlockedTicks >= DOOR_HOLD_TO_WARD_TICKS) {
                    // Defender held the door shut long enough to repel the enemy
                    ward(WardReason.DOOR_BLOCKED, instance, player, director);
                }
                return;
            } else {
                doorBlockedTicks = 0;
                doorWaitTicks++;
                if (doorWaitTicks % 25 == 0) {
                    // Impending danger sound outside the office
                    player.playSound(
                        Sound.sound(Key.key("minecraft:entity.warden.heartbeat"), Sound.Source.HOSTILE, 0.8F, 1.4F),
                        node.position().x(), node.position().y(), node.position().z()
                    );
                }
                if (doorWaitTicks >= type.doorPatienceTicks()) {
                    // Door was left open too long -> Breach!
                    stepToBreach(instance, player, director);
                    return;
                }
            }
            return;
        }

        // 4. Warded cooldown
        if (state == State.WARDED) {
            wardedCooldownTicks--;
            if (retreatDelayTicks > 0) {
                retreatDelayTicks--;
                if (retreatDelayTicks <= 0 && targetNode != null) {
                    // Reappear at retreat destination after brief vanished retreat
                    currentNode = targetNode;
                    targetNode = null;
                    spawnAt(instance, currentNode.position());
                    playReappearEffects(player, currentNode.position());
                }
            }
            if (wardedCooldownTicks <= 0) {
                if (targetNode != null) {
                    currentNode = targetNode;
                    targetNode = null;
                }
                if (entity == null) {
                    spawnAt(instance, currentNode.position());
                }
                state = State.STALKING;
                moveCooldownTicks = type.baseMoveIntervalTicks();
            }
            return;
        }

        // 5. Stalking movement
        if (state == State.STALKING) {
            moveCooldownTicks--;
            if (moveCooldownTicks <= 0) {
                if (director.canAdvance(this)) {
                    stepForward(instance, player, director);
                }
                moveCooldownTicks = type.baseMoveIntervalTicks();
            }
        }
    }

    public void stepForward(InstanceContainer instance, Player player, Director director) {
        List<PathNode> candidates = route.nextNodes(currentNode);
        if (candidates.isEmpty()) return;

        PathNode newNode = director.chooseNextNode(this, candidates);
        if (newNode == null) return;

        targetNode = newNode;
        transitTicks = type.transitTicks();

        // Vanish from current position without gliding through walls
        Pos departurePos = currentNode.position();
        vanish();
        playVanishEffects(player, departurePos);

        if (transitTicks <= 0) {
            completeRelocation(instance, player, director);
        } else {
            state = State.RELOCATING;
        }
    }

    private void completeRelocation(InstanceContainer instance, Player player, Director director) {
        currentNode = targetNode;
        targetNode = null;
        transitTicks = 0;

        spawnAt(instance, currentNode.position());
        playReappearEffects(player, currentNode.position());

        // Footstep audio telegraph
        player.playSound(
            Sound.sound(Key.key("minecraft:block.deepslate.step"), Sound.Source.HOSTILE, 0.7F, 0.6F),
            currentNode.position().x(), currentNode.position().y(), currentNode.position().z()
        );

        if (currentNode.isDoorApproach()) {
            state = State.WAITING_AT_DOOR;
            doorWaitTicks = 0;
            doorBlockedTicks = 0;
            // Heavy metallic cue right outside door
            player.playSound(
                Sound.sound(Key.key("minecraft:block.iron_door.close"), Sound.Source.HOSTILE, 0.4F, 0.5F),
                currentNode.position().x(), currentNode.position().y(), currentNode.position().z()
            );
        } else {
            state = State.STALKING;
            moveCooldownTicks = type.baseMoveIntervalTicks();
        }
    }

    public void ward(WardReason reason, InstanceContainer instance, Player player, Director director) {
        state = State.WARDED;
        wardedCooldownTicks = WARDED_COOLDOWN_TICKS; // 8 seconds breathing room
        retreatDelayTicks = RETREAT_VANISH_TICKS;     // 1.25s vanished retreat delay
        observedTicks = 0;
        doorWaitTicks = 0;
        doorBlockedTicks = 0;

        // Immediately vanish from the doorway or camera
        Pos repelledPos = currentNode.position();
        vanish();
        playVanishEffects(player, repelledPos);

        // Determine retreat destination node
        PathNode retreatDest = currentNode;
        for (int i = 0; i < type.retreatSteps(); i++) {
            List<PathNode> previous = route.previousNodes(retreatDest);
            if (previous.isEmpty()) break;
            PathNode chosen = director.chooseNextNode(this, previous);
            if (chosen == null) break;
            retreatDest = chosen;
        }
        targetNode = retreatDest;

        if (reason == WardReason.DOOR_BLOCKED) {
            // Bang loudly against the iron door and retreat
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.zombie.attack_iron_door"), Sound.Source.HOSTILE, 1.0F, 0.9F),
                repelledPos.x(), repelledPos.y(), repelledPos.z()
            );
        } else if (reason == WardReason.CAMERA_GAZE) {
            // Camera interference / hiss as it scurries backwards
            player.playSound(
                Sound.sound(Key.key("minecraft:entity.warden.sniff"), Sound.Source.HOSTILE, 0.9F, 1.2F),
                repelledPos.x(), repelledPos.y(), repelledPos.z()
            );
        }
    }

    private void stepToBreach(InstanceContainer instance, Player player, Director director) {
        List<PathNode> next = route.nextNodes(currentNode);
        if (!next.isEmpty()) {
            currentNode = next.getFirst();
        }
        state = State.BREACHED;
        vanish();
        spawnAt(instance, currentNode.position());
        director.onBreach(this, player);
    }

    public void despawn() {
        vanish();
        state = State.DORMANT;
        targetNode = null;
        transitTicks = 0;
        retreatDelayTicks = 0;
    }

    public enum State {
        DORMANT,
        STALKING,
        OBSERVED,
        RELOCATING,
        WAITING_AT_DOOR,
        WARDED,
        BREACHED
    }

    public enum WardReason {
        CAMERA_GAZE,
        DOOR_BLOCKED
    }
}
