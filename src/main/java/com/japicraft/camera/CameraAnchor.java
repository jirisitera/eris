package com.japicraft.camera;

import com.japicraft.emote.EmoteWheel;
import com.japicraft.emote.EmoteManager;
import com.japicraft.environment.InstanceManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import net.minestom.server.tag.Tag;

import java.util.Objects;

public final class CameraAnchor {
    // camera rotation when the fullscreen cursor reaches the screen edge
    public static final float MAX_YAW_OFFSET = 90.0f;
    public static final float MAX_PITCH_OFFSET = 90.0f;
    public static final float MOVE_SPEED = 20.0f;
    public static final float ARRIVAL_FACTOR = 0.50f;
    public static final float CAMERA_HEIGHT = 1.65f;
    private static final double CAMERA_BACK_OFFSET = 0;
    private static final Tag<Entity> anchor = Tag.Transient("cameraAnchor");
    private static final Tag<Entity> bodyAnchor = Tag.Transient("bodyAnchor");
    private static final Tag<Float> baseYaw = Tag.Transient("baseYaw");
    private static final Tag<View> view = Tag.Transient("view");

    public static boolean isAttached(Player player) {
        return player.hasTag(anchor);
    }

    private static boolean canSendCamera(Player player) {
        return player.isOnline()
            && player.getPlayerConnection().getServerState() == ConnectionState.PLAY
            && player.getPlayerConnection().getClientState() == ConnectionState.PLAY;
    }

    /** Stationary body mount; the model's existing vertical offset places its feet at the role spawn. */
    public static Pos bodyPosition(Player player) {
        return InstanceManager.spawnFor(player).add(0, CAMERA_HEIGHT, 0);
    }

    /** Eye position behind the role spawn, with the offset rotated into that role's facing direction. */
    public static Pos homePosition(Player player) {
        Pos spawn = InstanceManager.spawnFor(player);
        double yaw = Math.toRadians(spawn.yaw());
        return spawn.add(Math.sin(yaw) * CAMERA_BACK_OFFSET, CAMERA_HEIGHT,
            -Math.cos(yaw) * CAMERA_BACK_OFFSET);
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

    /** Replay body mounts after the joining viewer has received both vehicles and players. */
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

    public static void attach(Player player) {
        detach(player);
        Entity display = new Entity(EntityType.ITEM_DISPLAY);

        display.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(ItemStack.of(Material.AIR));
            meta.setPosRotInterpolationDuration(2);
            meta.setHasNoGravity(true);
        });

        player.setTag(CameraAnchor.anchor, display);

        Pos mainPosition = homePosition(player);

        ClientView.reset(player);
        player.setTag(CameraAnchor.baseYaw, mainPosition.yaw());
        player.setTag(EmoteManager.BODY_YAW_TAG, InstanceManager.spawnFor(player).yaw());
        float initialYawOffset = getYawOffset(player);
        float initialPitch = Math.clamp(mainPosition.pitch() + getPitchOffset(player), -90, 90);
        Pos startPos = mainPosition.withView(
            unwrapDelta(mainPosition.yaw() + initialYawOffset),
            initialPitch
        );
        player.setTag(CameraAnchor.view, new View(initialYawOffset, initialPitch));

        // Keep the player/model on a stationary vehicle; only the spectated camera moves.
        Entity body = new Entity(EntityType.ITEM_DISPLAY);
        body.setNoGravity(true);
        player.setTag(bodyAnchor, body);
        body.setInstance(player.getInstance(), bodyPosition(player)).thenRun(() -> {
            if (player.getTag(bodyAnchor) == body) body.addPassenger(player);
        });
        display.setInstance(player.getInstance(), startPos).thenRun(() -> {
            if (player.getTag(CameraAnchor.anchor) == display && canSendCamera(player)) player.spectate(display);
        });
    }

    public static void updateInput(Player player) {
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        if (BombView.isActive(player)) {
            view.targetYawOffset = unwrapDelta(BombView.CAMERA_POSITION.yaw() - baseYaw(player));
            view.targetPitch = BombView.CAMERA_POSITION.pitch();
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
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        updateInput(player);

        float deltaYaw = view.targetYawOffset - view.yawOffset;
        float deltaPitch = view.targetPitch - view.pitch;
        float distance = (float) Math.hypot(deltaYaw, deltaPitch);
        Pos targetPosition = BombView.isActive(player) ? BombView.CAMERA_POSITION : homePosition(player);
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
     * Camera yaw offset following the fullscreen cursor (-1..1 across the screen width).
     */
    public static float getYawOffset(Player player) {
        return Math.clamp(ClientView.mouseYaw(player) / ClientView.MAX_CURSOR_YAW, -1.0f, 1.0f) * MAX_YAW_OFFSET;
    }

    /**
     * Camera pitch offset following the fullscreen cursor (-1..1 across the screen height).
     */
    public static float getPitchOffset(Player player) {
        return Math.clamp(ClientView.mousePitch(player) / ClientView.MAX_CURSOR_PITCH, -1.0f, 1.0f) * MAX_PITCH_OFFSET;
    }

    public static void updateTarget(Player player) {
        Entity anchor = player.getTag(CameraAnchor.anchor);
        if (anchor == null) {
            return;
        }

        View view = player.getTag(CameraAnchor.view);
        if (view == null) {
            return;
        }

        updateInput(player);
    }

    public static void detach(Player player) {
        if (isAttached(player) && canSendCamera(player)) player.stopSpectating();
        Entity body = player.getTag(bodyAnchor);
        if (body != null) {
            body.removePassenger(player);
            body.remove();
        }
        player.removeTag(bodyAnchor);
        Entity anchor = player.getTag(CameraAnchor.anchor);
        if (anchor != null) {
            anchor.remove();
        }
        player.removeTag(CameraAnchor.anchor);
        player.removeTag(CameraAnchor.baseYaw);
        player.removeTag(CameraAnchor.view);
    }

    private static final class View {
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
