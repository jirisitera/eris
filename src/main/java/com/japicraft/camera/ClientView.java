package com.japicraft.camera;

import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.tag.Tag;

public final class ClientView {
    public static final float MAX_CURSOR_YAW = 90.0F; // mouse degrees from the screen center to the screen edge
    public static final float MAX_CURSOR_PITCH = 90.0F;
    private static final Tag<View> TAG = Tag.Transient("clientView");
    private static final Tag<Float> MOUSE_YAW = Tag.Transient("clientMouseYaw");
    private static final Tag<Float> MOUSE_PITCH = Tag.Transient("clientMousePitch");

    public static float wrapDegrees(float angle) {
        float wrapped = angle % 360.0F;
        if (wrapped >= 180.0F) {
            wrapped -= 360.0F;
        }
        if (wrapped < -180.0F) {
            wrapped += 360.0F;
        }
        return wrapped;
    }

    public static void reset(Player player) {
        player.setTag(MOUSE_YAW, 0.0F);
        player.setTag(MOUSE_PITCH, 0.0F);
        player.setTag(TAG, new View(player.getPosition().yaw(), player.getPosition().pitch()));
    }

    public static boolean record(Player player, ClientPacket packet) {
        View view = switch (packet) {
            case ClientPlayerRotationPacket rotation -> new View(rotation.yaw(), rotation.pitch());
            case ClientPlayerPositionAndRotationPacket move -> new View(move.position().yaw(), move.position().pitch());
            default -> null;
        };
        if (view == null) {
            return false;
        }
        View previous = player.getTag(TAG);
        Float mouseYaw = player.getTag(MOUSE_YAW);
        Float mousePitch = player.getTag(MOUSE_PITCH);
        // accumulate per-packet deltas and clamp to the cursor bounds so offscreen movement never desyncs camera rotation
        float nextMouseYaw = Math.clamp(
            previous == null || mouseYaw == null
                ? wrapDegrees(view.yaw())
                : mouseYaw + wrapDegrees(view.yaw() - previous.yaw()),
            -MAX_CURSOR_YAW,
            MAX_CURSOR_YAW
        );
        float nextMousePitch = Math.clamp(
            previous == null || mousePitch == null
                ? view.pitch()
                : mousePitch + (view.pitch() - previous.pitch()),
            -MAX_CURSOR_PITCH,
            MAX_CURSOR_PITCH
        );
        player.setTag(MOUSE_YAW, nextMouseYaw);
        player.setTag(MOUSE_PITCH, nextMousePitch);
        player.setTag(TAG, view);
        return true;
    }

    public static float yaw(Player player) {
        View view = player.getTag(TAG);
        return view == null ? player.getPosition().yaw() : view.yaw();
    }

    public static float pitch(Player player) {
        View view = player.getTag(TAG);
        return view == null ? player.getPosition().pitch() : view.pitch();
    }

    public static float mouseYaw(Player player) {
        Float mouseYaw = player.getTag(MOUSE_YAW);
        return mouseYaw == null ? Math.clamp(wrapDegrees(yaw(player)), -MAX_CURSOR_YAW, MAX_CURSOR_YAW) : mouseYaw;
    }

    public static float mousePitch(Player player) {
        Float mousePitch = player.getTag(MOUSE_PITCH);
        return mousePitch == null ? Math.clamp(pitch(player), -MAX_CURSOR_PITCH, MAX_CURSOR_PITCH) : mousePitch;
    }

    public record View(float yaw, float pitch) {
    }
}
