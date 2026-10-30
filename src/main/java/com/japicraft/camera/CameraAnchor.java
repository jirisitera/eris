package com.japicraft.camera;

import com.japicraft.environment.InstanceManager;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

public final class CameraAnchor {
    // camera rotation when the fullscreen cursor reaches the screen edge
    public static final float MAX_YAW_OFFSET = 90.0f;
    public static final float MAX_PITCH_OFFSET = 90.0f;
    public static final float BASE_SMOOTHING = 0.16f;
    public static final float MAX_SMOOTHING = 0.70f;
    public static final float MIN_DISTANCE = 2.0f;
    public static final float MAX_DISTANCE = 25.0f;
    private static final float LEAN_YAW_OFFSET = 90.0f;
    private static final Tag<Entity> anchor = Tag.Transient("cameraAnchor");
    private static final Tag<Float> baseYaw = Tag.Transient("baseYaw");
    private static final Tag<View> view = Tag.Transient("view");

    private static float calculateProgress(View view) {
        return calculateProgress(view.yaw, view.targetYaw, view.pitch, view.targetPitch);
    }

    static float calculateProgress(float currentYaw, float targetYaw, float currentPitch, float targetPitch) {
        float deltaYaw = unwrapDelta(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;
        float distance = (float) Math.hypot(deltaYaw, deltaPitch);

        if (distance <= MIN_DISTANCE) {
            float base = BASE_SMOOTHING;
            return base * base * (3.0F - 2.0F * base);
        }

        float t = Math.clamp((distance - MIN_DISTANCE) / (MAX_DISTANCE - MIN_DISTANCE), 0.0F, 1.0F);
        float smoothT = t * t * (3.0F - 2.0F * t);
        float smoothing = BASE_SMOOTHING + (MAX_SMOOTHING - BASE_SMOOTHING) * smoothT;
        return smoothing * smoothing * (3.0F - 2.0F * smoothing);
    }

    static float ease(float current, float target, float progress) {
        float delta = unwrapDelta(target - current);
        if (Math.abs(delta) < 0.01F) {
            return target;
        }
        return current + delta * progress;
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
            meta.setPosRotInterpolationDuration(3);
            meta.setHasNoGravity(true);
        });

        player.setTag(CameraAnchor.anchor, display);

        Pos mainPosition = InstanceManager.MAIN_SPAWN.add(0, 1.75, -0.25);

        ClientView.reset(player);
        player.setTag(CameraAnchor.baseYaw, mainPosition.yaw());
        Pos startPos = mainPosition.withView(
            unwrapDelta(mainPosition.yaw() + getYawOffset(player)),
            getPitchOffset(player)
        );
        player.setTag(CameraAnchor.view, new View(startPos));

        display.setInstance(player.getInstance(), startPos).thenRun(() -> {
            player.spectate(display);
            display.addPassenger(player);
        });
    }

    public static float calculateLeanYaw(Player player) {
        boolean left = player.inputs().left();
        boolean right = player.inputs().right();
        if (left && !right) {
            return -LEAN_YAW_OFFSET;
        } else if (right && !left) {
            return LEAN_YAW_OFFSET;
        }
        return 0.0f;
    }

    public static void updateInput(Player player) {
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        float baseYaw = player.getTag(CameraAnchor.baseYaw);
        float leanYaw = calculateLeanYaw(player);
        view.targetYaw = unwrapDelta(baseYaw + (leanYaw != 0.0f ? leanYaw : getYawOffset(player)));
        view.targetPitch = getPitchOffset(player);
    }

    public static void update(Player player) {
        Entity anchor = player.getTag(CameraAnchor.anchor);
        View view = player.getTag(CameraAnchor.view);
        if (anchor == null || view == null) {
            return;
        }

        updateInput(player);

        float progress = calculateProgress(view);

        float yaw = ease(view.yaw, view.targetYaw, progress);
        float pitch = ease(view.pitch, view.targetPitch, progress);
        if (yaw == view.yaw && pitch == view.pitch) {
            return;
        }
        view.yaw = unwrapDelta(yaw);
        view.pitch = pitch;

        anchor.teleport(anchor.getPosition().withView(view.yaw, view.pitch));
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
        player.stopSpectating();
        Entity anchor = player.getTag(CameraAnchor.anchor);
        if (anchor != null) {
            anchor.removePassenger(player);
            anchor.remove();
        }
        player.removeTag(CameraAnchor.anchor);
        player.removeTag(CameraAnchor.baseYaw);
        player.removeTag(CameraAnchor.view);
    }

    private static final class View {
        private float yaw;
        private float pitch;
        private float targetYaw;
        private float targetPitch;

        private View(Pos pos) {
            this.yaw = pos.yaw();
            this.pitch = pos.pitch();
            this.targetYaw = pos.yaw();
            this.targetPitch = pos.pitch();
        }
    }
}
