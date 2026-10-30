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

public final class CameraAnchor {
    // camera rotation when the fullscreen cursor reaches the screen edge
    public static final float MAX_YAW_OFFSET = 90.0f;
    public static final float MAX_PITCH_OFFSET = 90.0f;
    public static final float MOVE_SPEED = 1.8f;
    public static final float ARRIVAL_FACTOR = 0.20f;
    private static final float LEAN_YAW_OFFSET = 90.0f;
    private static final Tag<Entity> anchor = Tag.Transient("cameraAnchor");
    private static final Tag<Float> baseYaw = Tag.Transient("baseYaw");
    private static final Tag<View> view = Tag.Transient("view");

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
            meta.setPosRotInterpolationDuration(5);
            meta.setHasNoGravity(true);
        });

        player.setTag(CameraAnchor.anchor, display);

        Pos mainPosition = InstanceManager.MAIN_SPAWN.add(0, 1.75, -0.25);

        ClientView.reset(player);
        player.setTag(CameraAnchor.baseYaw, mainPosition.yaw());
        float initialYawOffset = getYawOffset(player);
        float initialPitch = getPitchOffset(player);
        Pos startPos = mainPosition.withView(
            unwrapDelta(mainPosition.yaw() + initialYawOffset),
            initialPitch
        );
        player.setTag(CameraAnchor.view, new View(initialYawOffset, initialPitch));

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

        if (EmoteWheel.isOpen(player)) {
            view.targetYawOffset = view.yawOffset;
            view.targetPitch = view.pitch;
            return;
        }

        float leanYaw = calculateLeanYaw(player);
        view.targetYawOffset = leanYaw != 0.0f ? leanYaw : getYawOffset(player);
        view.targetPitch = getPitchOffset(player);
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
        if (distance < 0.001f) {
            return;
        }

        float step = Math.min(distance * ARRIVAL_FACTOR, MOVE_SPEED);
        float scale = step / distance;
        view.yawOffset += deltaYaw * scale;
        view.pitch += deltaPitch * scale;

        float baseYaw = player.getTag(CameraAnchor.baseYaw);
        anchor.teleport(anchor.getPosition().withView(unwrapDelta(baseYaw + view.yawOffset), view.pitch));
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
