package com.japicraft.camera;

import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.tag.Tag;

public final class ClientView {
    public static final float MAX_CURSOR_YAW = 90.0F; // mouse degrees from the screen center to the screen edge
    public static final float MAX_CURSOR_PITCH = 90.0F;
    private static final Tag<View> TAG = Tag.Transient("clientView");
    private static final Tag<Float> MOUSE_YAW = Tag.Transient("clientMouseYaw");
    private static final Tag<Float> MOUSE_PITCH = Tag.Transient("clientMousePitch");
    private static final Tag<View> PENDING_VIEW = Tag.Transient("clientCursorViewCorrection");

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
        player.removeTag(PENDING_VIEW);
        player.setTag(MOUSE_YAW, 0.0F);
        player.setTag(MOUSE_PITCH, 0.0F);
        player.setTag(TAG, new View(player.getPosition().yaw(), player.getPosition().pitch()));
    }

    public static boolean record(Player player, ClientPacket packet) {
        View previous = player.getTag(TAG);
        float nextRawYaw;
        float nextRawPitch;
        float deltaYaw;
        float deltaPitch;

        switch (packet) {
            case ClientPlayerRotationPacket rotation -> {
                nextRawYaw = rotation.yaw();
                nextRawPitch = rotation.pitch();
                // ClientPlayerRotationPacket carries the client's raw unbounded yaw, so the difference never wraps at 180 degrees
                deltaYaw = previous == null ? wrapDegrees(nextRawYaw) : nextRawYaw - previous.yaw();
                deltaPitch = previous == null ? nextRawPitch : nextRawPitch - previous.pitch();
            }
            case ClientPlayerPositionAndRotationPacket move -> {
                nextRawPitch = move.position().pitch();
                deltaYaw = previous == null ? wrapDegrees(move.position().yaw()) : wrapDegrees(move.position().yaw() - previous.yaw());
                deltaPitch = previous == null ? nextRawPitch : nextRawPitch - previous.pitch();
                nextRawYaw = previous == null ? move.position().yaw() : previous.yaw() + deltaYaw;
            }
            default -> {
                return false;
            }
        }

        View pendingView = player.getTag(PENDING_VIEW);
        if (pendingView != null) {
            // Rotation corrections immediately echo a rotation packet from the client.
            // Ignore old in-flight input and the correction itself on both axes until that echo arrives.
            deltaYaw = 0.0F;
            deltaPitch = 0.0F;
            if (wrapDegrees(nextRawYaw - pendingView.yaw()) == 0.0F && nextRawPitch == pendingView.pitch()) {
                player.removeTag(PENDING_VIEW);
                nextRawYaw = pendingView.yaw();
            }
        }

        Float mouseYaw = player.getTag(MOUSE_YAW);
        Float mousePitch = player.getTag(MOUSE_PITCH);
        float nextMouseYaw = Math.clamp(
            (mouseYaw == null ? 0.0F : mouseYaw) + deltaYaw,
            -MAX_CURSOR_YAW,
            MAX_CURSOR_YAW
        );
        float nextMousePitch = Math.clamp(
            (mousePitch == null ? 0.0F : mousePitch) + deltaPitch,
            -MAX_CURSOR_PITCH,
            MAX_CURSOR_PITCH
        );
        player.setTag(MOUSE_YAW, nextMouseYaw);
        player.setTag(MOUSE_PITCH, nextMousePitch);
        player.setTag(TAG, new View(nextRawYaw, nextRawPitch));
        return true;
    }

    public static float yaw(Player player) {
        View pendingView = player.getTag(PENDING_VIEW);
        if (pendingView != null) {
            return pendingView.yaw();
        }
        View view = player.getTag(TAG);
        return view == null ? player.getPosition().yaw() : view.yaw();
    }

    public static float pitch(Player player) {
        View pendingView = player.getTag(PENDING_VIEW);
        if (pendingView != null) {
            // The probe and correction are delivered together; extrapolate from the corrected view.
            return pendingView.pitch();
        }
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

    public static CursorPosition cursorPosition(Player player) {
        return new CursorPosition(mouseYaw(player), mousePitch(player));
    }

    public static void restoreCursor(Player player, CursorPosition position) {
        player.setTag(MOUSE_YAW, position.yaw());
        player.setTag(MOUSE_PITCH, position.pitch());
        // Correct both client axes together, using the same baseline as the restored probe.
        player.setTag(PENDING_VIEW, new View(position.yaw(), position.pitch()));
        CursorProbe.updateWithRotation(player, new PlayerRotationPacket(position.yaw(), false, position.pitch(), false));
    }

    public record CursorPosition(float yaw, float pitch) {
    }

    public record View(float yaw, float pitch) {
    }
}
