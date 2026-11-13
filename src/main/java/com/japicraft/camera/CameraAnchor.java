package com.japicraft.camera;

import com.japicraft.emote.EmoteManager;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.InstanceManager;
import com.japicraft.environment.StructureLoader;
import com.japicraft.environment.StructureLoader.CameraTarget;
import com.japicraft.game.GameStageManager;
import com.japicraft.player.PlayerRole;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.play.EntityPositionSyncPacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.network.packet.server.play.data.PositionPath;
import net.minestom.server.tag.Tag;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class CameraAnchor {
    public static final float MAX_YAW_OFFSET = 60.0f;
    public static final float MAX_PITCH_OFFSET = 60.0f;
    public static final float MOVE_SPEED = 20.0f;
    public static final float ARRIVAL_FACTOR = 0.50f;
    public static final float CAMERA_HEIGHT = 1.65f;
    // helper view from the right-hand side of the case
    public static final Pos HELPER_LEAN_POSITION = StructureLoader.CASE_SPAWN.add(0.65, 1.0, 0).withView(90.0F, 52.5F);
    // expert view of the manual from their side of the table
    public static final Pos EXPERT_LEAN_POSITION = StructureLoader.MANUAL_SPAWN.add(0, 1.0, 0.45).withView(180.0F, 45F);
    private static final Tag<Entity> anchor = Tag.Transient("cameraAnchor");
    private static final Tag<Entity> bodyAnchor = Tag.Transient("bodyAnchor");
    private static final Tag<Float> baseYaw = Tag.Transient("baseYaw");
    private static final Tag<View> view = Tag.Transient("view");

    public static boolean isAttached(Player player) {
        return player.hasTag(anchor);
    }

    public static void enterLean(Player player) {
        if (PlayerRole.get(player) == PlayerRole.BOMB_DEFUSER) {
            BombView.enter(player);
            return;
        }
        View current = player.getTag(view);
        if (current == null || current.roleLeaning || player.getInstance() == null
            || !GameStageManager.isGame(player) || (PlayerRole.get(player) != PlayerRole.DEFUSAL_HELPER
            && PlayerRole.get(player) != PlayerRole.MANUAL_EXPERT)) return;
        if (PlayerRole.get(player) == PlayerRole.DEFUSAL_HELPER
            && !StructureLoader.isBombOpen(player.getInstance())) return;
        current.roleLeaning = true;
        ClientView.selectCursor(player, EmoteWheel.dismissForModeSwitch(player));
    }

    public static void exitLean(Player player) {
        BombView.exit(player);
        View current = player.getTag(view);
        if (current != null) current.roleLeaning = false;
    }

    private static Pos leanPosition(Player player, View current) {
        if (BombView.isActive(player)) return BombView.CAMERA_POSITION;
        if (!current.roleLeaning) return null;
        return PlayerRole.get(player) == PlayerRole.MANUAL_EXPERT ? EXPERT_LEAN_POSITION : HELPER_LEAN_POSITION;
    }

    private static boolean canSendCamera(Player player) {
        return player.isOnline()
            && player.getPlayerConnection().getServerState() == ConnectionState.PLAY
            && player.getPlayerConnection().getClientState() == ConnectionState.PLAY;
    }

    /**
     * stationary body mount with model feet offset to the role spawn
     */
    public static Pos bodyPosition(Player player) {
        // underwater input based on the real player's eyes rather than the camera
        // visual lobby seats independent of real player positions
        if (!GameStageManager.isGame(player)) {
            // vehicle attachment compensation for real player positioning at the camera

            var attachments = player.getEntityType().entityAttachments("VEHICLE");
            Vec riding = attachments == null ? Vec.ZERO : attachments.getFirst().rotateAroundY(-Math.PI);
            return InstanceManager.LOBBY_CENTER.add(riding).withYaw(180);
        }
        return InstanceManager.spawnFor(player).add(0, CAMERA_HEIGHT, 0);
    }

    /**
     * shared lobby view or role-specific eye position
     */
    public static Pos homePosition(Player player) {
        if (!GameStageManager.isGame(player)) return InstanceManager.LOBBY_CAMERA;
        return InstanceManager.spawnFor(player).add(0, CAMERA_HEIGHT, 0);
    }

    public static float baseYaw(Player player) {
        Float yaw = player.getTag(baseYaw);
        return yaw != null ? yaw : player.getPosition().yaw();
    }

    public static float viewYaw(Player player) {
        Entity display = player.getTag(anchor);
        return Objects.requireNonNullElse(display, player).getPosition().yaw();
    }

    public static float viewPitch(Player player) {
        Entity display = player.getTag(anchor);
        return Objects.requireNonNullElse(display, player).getPosition().pitch();
    }

    /**
     * body mount replay after vehicle and player delivery to the joining viewer
     */
    public static void syncBodyMounts(Player viewer) {
        if (viewer.getInstance() == null) return;
        for (Player player : viewer.getInstance().getPlayers()) {
            if (player == viewer || !player.isViewer(viewer)) continue;
            Entity body = player.getTag(bodyAnchor);
            if (body == null || !body.isViewer(viewer) || player.getVehicle() != body) continue;
            viewer.sendPacket(new SetPassengersPacket(body.getEntityId(),
                body.getPassengers().stream().map(Entity::getEntityId).toList()));
        }
    }

    private static float unwrapDelta(float delta) {
        while (delta > 180.0F) {
            delta -= 360.0F;
        }
        while (delta < -180.0F) {
            delta += 360.0F;
        }
        return delta;
    }

    /**
     * body reuse and persistent camera switching
     */
    public static void attach(Player player) {
        boolean playing = GameStageManager.isGame(player);
        Pos mainPosition = homePosition(player);

        ClientView.reset(player);
        player.setTag(CameraAnchor.baseYaw, mainPosition.yaw());
        player.setTag(EmoteManager.BODY_YAW_TAG,
            (playing ? InstanceManager.spawnFor(player) : InstanceManager.lobbyAvatarPosition(player)).yaw());
        float initialYawOffset = getYawOffset(player);
        float initialPitch = Math.clamp(mainPosition.pitch() + getPitchOffset(player), -90, 90);
        Pos startPos = mainPosition.withView(
            unwrapDelta(mainPosition.yaw() + initialYawOffset),
            initialPitch
        );
        player.setTag(CameraAnchor.view, new View(initialYawOffset, initialPitch));

        CameraTarget camera = StructureLoader.cameraFor(player, playing, startPos);
        Entity display = camera.entity();
        player.setTag(CameraAnchor.anchor, display);

        Entity body = player.getTag(bodyAnchor);
        if (body == null) {
            body = new Entity(EntityType.ITEM_DISPLAY);
            body.setNoGravity(true);
            player.setTag(bodyAnchor, body);
            Entity mount = body;
            body.setInstance(player.getInstance(), bodyPosition(player)).thenRun(() -> {
                if (player.getTag(bodyAnchor) == mount) mount.addPassenger(player);
                else mount.remove();
            });
        } else {
            body.teleport(bodyPosition(player));
        }
        CompletableFuture<Void> cameraReady = playing ? camera.ready().thenCompose(_ -> display.teleport(startPos)) : camera.ready();
        cameraReady.thenRun(() -> {
            if (player.getTag(CameraAnchor.anchor) == display && canSendCamera(player)) {
                // byte-rounded SpawnEntity angles (8 degrees becomes 7.03125)
                // precise transform before spectating to avoid a delayed pitch shift

                player.sendPacket(new EntityPositionSyncPacket(display.getEntityId(),
                    new PositionPath.Linear(startPos), startPos.yaw(), startPos.pitch(), false));
                player.spectate(display);
            }
        });
    }

    public static void updateInput(Player player) {
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        if (!GameStageManager.isGame(player)) {
            view.targetYawOffset = 0;
            view.targetPitch = InstanceManager.LOBBY_CAMERA.pitch();
            return;
        }

        if (view.roleLeaning && ((PlayerRole.get(player) != PlayerRole.DEFUSAL_HELPER
            && PlayerRole.get(player) != PlayerRole.MANUAL_EXPERT)
            || !GameStageManager.isGame(player))) view.roleLeaning = false;
        Pos leaning = leanPosition(player, view);
        if (leaning != null) {
            view.targetYawOffset = unwrapDelta(leaning.yaw() - baseYaw(player));
            view.targetPitch = leaning.pitch();
            return;
        }

        if (EmoteWheel.isTurningPaused(player)) {
            view.targetYawOffset = view.yawOffset;
            view.targetPitch = view.pitch;
            return;
        }

        view.targetYawOffset = getYawOffset(player);
        view.targetPitch = Math.clamp(homePosition(player).pitch() + getPitchOffset(player), -90, 90);
    }

    public static void update(Player player) {
        // shared stationary lobby camera with independent per-player UI cursors

        if (!GameStageManager.isGame(player)) return;
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        updateInput(player);

        float deltaYaw = view.targetYawOffset - view.yawOffset;
        float deltaPitch = view.targetPitch - view.pitch;
        float distance = (float) Math.hypot(deltaYaw, deltaPitch);
        Pos leaning = leanPosition(player, view);
        Pos targetPosition = leaning != null ? leaning : homePosition(player);
        Pos currentPosition = anchor.getPosition();
        double positionDistance = currentPosition.distance(targetPosition);
        if (distance < 0.001f && positionDistance < 0.001) {
            return;
        }

        float step = Math.min(distance * ARRIVAL_FACTOR, MOVE_SPEED);
        if (step >= distance || distance < 0.02f) {
            view.yawOffset = view.targetYawOffset;
            view.pitch = view.targetPitch;
        } else {
            float scale = step / distance;
            view.yawOffset += deltaYaw * scale;
            view.pitch += deltaPitch * scale;
        }

        float baseYaw = player.getTag(CameraAnchor.baseYaw);
        Pos nextPosition = positionDistance < 0.005 ? targetPosition : currentPosition.add(
            (targetPosition.x() - currentPosition.x()) * ARRIVAL_FACTOR,
            (targetPosition.y() - currentPosition.y()) * ARRIVAL_FACTOR,
            (targetPosition.z() - currentPosition.z()) * ARRIVAL_FACTOR);
        anchor.teleport(nextPosition.withView(unwrapDelta(baseYaw + view.yawOffset), view.pitch));
    }

    /**
     * camera yaw offset following the fullscreen cursor (-1..1 across the screen width)
     */
    public static float getYawOffset(Player player) {
        if (!GameStageManager.isGame(player)) return 0;
        return Math.clamp(ClientView.mouseYaw(player) / ClientView.MAX_CURSOR_YAW, -1.0f, 1.0f) * MAX_YAW_OFFSET;
    }

    /**
     * camera pitch offset following the fullscreen cursor (-1..1 across the screen height)
     */
    public static float getPitchOffset(Player player) {
        if (!GameStageManager.isGame(player)) return 0;
        return Math.clamp(ClientView.mousePitch(player) / ClientView.MAX_CURSOR_PITCH, -1.0f, 1.0f) * MAX_PITCH_OFFSET;
    }

    public static void detach(Player player) {
        if (isAttached(player) && canSendCamera(player)) player.stopSpectating();
        Entity body = player.getTag(bodyAnchor);
        if (body != null) {
            body.removePassenger(player);
            body.remove();
        }
        player.removeTag(bodyAnchor);
        player.removeTag(CameraAnchor.anchor);
        StructureLoader.removePlayerCamera(player);
        player.removeTag(CameraAnchor.baseYaw);
        player.removeTag(CameraAnchor.view);
    }

    private static final class View {
        private boolean roleLeaning;
        private float yawOffset;
        private float pitch;
        private float targetYawOffset;
        private float targetPitch;

        private View(float yawOffset, float pitch) {
            this.yawOffset = yawOffset;
            this.pitch = pitch;
            this.targetYawOffset = yawOffset;
            this.targetPitch = pitch;
        }
    }
}
