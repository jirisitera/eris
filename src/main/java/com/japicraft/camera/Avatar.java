package com.japicraft.camera;

import com.japicraft.Eris;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import java.util.Objects;

public final class Avatar {
    private static final String LEFT_HAND = Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "arm_left";
    private static final String RIGHT_HAND = Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "arm_right";
    private static final double FORWARD_OFFSET = 0.15;
    private static final double SIDE_OFFSET = 0.35;
    private static final double TABLE_HEIGHT_OFFSET = 0.25;
    private static final float RIGHT_YAW_ROTATION = 22.5F;
    private static final float RIGHT_PITCH_ROTATION = 7.5F;
    private static final float SMOOTHING = 0.16f;
    private static final float[] LEFT_ROTATION = rotationFromDegrees(75.0F, 180.0F, 5.0F);
    private static final float[] RIGHT_ROTATION = rotationFromDegrees(75.0F, 175.0F, 0.0F);
    private static final Tag<Parts> TAG = Tag.Transient("firstPersonParts");

    private static Entity createHand(String hand) {
        Entity entity = new Entity(EntityType.ITEM_DISPLAY);
        entity.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(ItemStack.of(Material.ECHO_SHARD).withItemModel(hand));
            meta.setLeftRotation(Objects.equals(hand, LEFT_HAND) ? LEFT_ROTATION : RIGHT_ROTATION);
            meta.setHasNoGravity(true);
            meta.setTransformationInterpolationDuration(3);
        });
        return entity;
    }

    private static float[] rotationFromDegrees(float x, float y, float z) {
        double halfX = Math.toRadians(x) * 0.5;
        double halfY = Math.toRadians(y) * 0.5;
        double halfZ = Math.toRadians(z) * 0.5;
        double sinX = Math.sin(halfX);
        double cosX = Math.cos(halfX);
        double sinY = Math.sin(halfY);
        double cosY = Math.cos(halfY);
        double sinZ = Math.sin(halfZ);
        double cosZ = Math.cos(halfZ);
        return new float[]{
            (float) (sinX * cosY * cosZ - cosX * sinY * sinZ),
            (float) (cosX * sinY * cosZ + sinX * cosY * sinZ),
            (float) (cosX * cosY * sinZ - sinX * sinY * cosZ),
            (float) (cosX * cosY * cosZ + sinX * sinY * sinZ)
        };
    }

    private static Pos leftHandPosition(Pos camera) {
        return new Pos(camera.x() + SIDE_OFFSET, camera.y() + TABLE_HEIGHT_OFFSET, camera.z() + FORWARD_OFFSET);
    }

    private static Pos rightHandPosition(Pos camera) {
        return new Pos(camera.x() - SIDE_OFFSET, camera.y() + TABLE_HEIGHT_OFFSET, camera.z() + FORWARD_OFFSET);
    }

    private static float ease(float current, float target) {
        float progress = SMOOTHING * SMOOTHING * (3.0F - 2.0F * SMOOTHING);
        if (Math.abs(target - current) < 0.01F) {
            return target;
        }
        return current + (target - current) * progress;
    }

    private static float[] rightHandRotation(float yawOffset, float pitchOffset) {
        return rotationFromDegrees(75.0F + pitchOffset, 180.0F + yawOffset, 0.0F);
    }

    public static void attach(Player player) {
        detach(player);

        Entity left = createHand(LEFT_HAND);
        Entity right = createHand(RIGHT_HAND);

        ClientView.View view = updateRightHand(player);

        right.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setLeftRotation(rightHandRotation(view.yaw(), view.pitch())));
        player.setTag(Avatar.TAG, new Parts(left, right, view.yaw(), view.pitch()));

        Pos camera = player.getPosition();
        Instance instance = player.getInstance();

        left.setInstance(instance, leftHandPosition(camera));
        right.setInstance(instance, rightHandPosition(camera));
    }

    private static ClientView.View updateRightHand(Player player) {
        CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
        float normalX = (cursor.x() - 0.5F) * 2.0F;
        float normalY = (cursor.y() - 0.5F) * 2.0F;
        return new ClientView.View(-normalX * RIGHT_YAW_ROTATION, -normalY * RIGHT_PITCH_ROTATION);
    }

    public static void update(Player player) {
        Parts parts = player.getTag(Avatar.TAG);
        if (parts == null) {
            return;
        }
        ClientView.View view = updateRightHand(player);

        float yawOffset = ease(parts.currentYawOffset, view.yaw());
        float pitchOffset = ease(parts.currentPitchOffset, view.pitch());
        if (yawOffset == parts.currentYawOffset && pitchOffset == parts.currentPitchOffset) {
            return;
        }

        parts.currentYawOffset = yawOffset;
        parts.currentPitchOffset = pitchOffset;

        parts.right.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setLeftRotation(rightHandRotation(yawOffset, pitchOffset));
            meta.setTransformationInterpolationStartDelta(0);
        });
    }

    public static void detach(Player player) {
        Parts parts = player.getTag(Avatar.TAG);
        if (parts == null) {
            return;
        }
        parts.left.remove();
        parts.right.remove();
        player.removeTag(Avatar.TAG);
    }

    private static final class Parts {
        final Entity left;
        final Entity right;
        float currentYawOffset;
        float currentPitchOffset;

        Parts(Entity left, Entity right, float currentYawOffset, float currentPitchOffset) {
            this.left = left;
            this.right = right;
            this.currentYawOffset = currentYawOffset;
            this.currentPitchOffset = currentPitchOffset;
        }
    }
}
