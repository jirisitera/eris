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
    private static final float RAW_PITCH_RECENTER_THRESHOLD = 75.0F;
    private static final float VIEW_EPSILON = 0.0001F;
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
        restoreCursor(player, new CursorPosition(0, 0));
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
                // unbounded rotation-only yaw for fast movements beyond half a turn per packet

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

        if (!Float.isFinite(nextRawYaw) || !Float.isFinite(nextRawPitch)) return false;

        View pendingView = player.getTag(PENDING_VIEW);
        if (pendingView != null) {
            // client rotation echo after corrections
            // old in-flight input and correction movement ignored until the echo
            deltaYaw = 0.0F;
            deltaPitch = 0.0F;
            if (Math.abs(wrapDegrees(nextRawYaw - pendingView.yaw())) <= VIEW_EPSILON
                && Math.abs(nextRawPitch - pendingView.pitch()) <= VIEW_EPSILON) {
                player.removeTag(PENDING_VIEW);
                nextRawYaw = pendingView.yaw();
            }
        }

        Float mouseYaw = player.getTag(MOUSE_YAW);
        Float mousePitch = player.getTag(MOUSE_PITCH);
        float yawLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_YAW;
        float pitchLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_PITCH;
        float nextMouseYaw = Math.clamp(
            (mouseYaw == null ? 0.0F : mouseYaw) + deltaYaw,
            -yawLimit,
            yawLimit
        );
        float nextMousePitch = Math.clamp(
            (mousePitch == null ? 0.0F : mousePitch) + deltaPitch,
            -pitchLimit,
            pitchLimit
        );
        player.setTag(MOUSE_YAW, nextMouseYaw);
        player.setTag(MOUSE_PITCH, nextMousePitch);
        player.setTag(TAG, new View(nextRawYaw, nextRawPitch));
        // native pitch clamp at +/-90, independent of the virtual cursor
        // pitch rebasing after overscroll or mode switches, before the native clamp

        if (!player.hasTag(PENDING_VIEW) && Math.abs(nextRawPitch) >= RAW_PITCH_RECENTER_THRESHOLD
            && Math.abs(nextRawPitch - nextMousePitch) > VIEW_EPSILON) {
            correctView(player, new View(wrapDegrees(nextRawYaw), nextMousePitch));
        }
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

    public static boolean isCorrectionPending(Player player) {
        return player.hasTag(PENDING_VIEW);
    }

    public static float pitch(Player player) {
        View pendingView = player.getTag(PENDING_VIEW);
        if (pendingView != null) {
            // extrapolation from the corrected view bundled with the probe
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

    /**
     * switch virtual canvases without moving the native input baseline
     */
    public static void selectCursor(Player player, CursorPosition position) {
        setCursorPosition(player, position);
        // The held probe can render a tick after a rotation packet. Keep raw input
        // steady so the old canvas cannot extrapolate toward the new position.
        CursorProbe.update(player);
    }

    public static void restoreCursor(Player player, CursorPosition position) {
        View target = setCursorPosition(player, position);
        correctView(player, target);
    }

    private static View setCursorPosition(Player player, CursorPosition position) {
        float yawLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_YAW;
        float pitchLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_PITCH;
        float yaw = Math.clamp(position.yaw(), -yawLimit, yawLimit);
        float pitch = Math.clamp(position.pitch(), -pitchLimit, pitchLimit);
        player.setTag(MOUSE_YAW, yaw);
        player.setTag(MOUSE_PITCH, pitch);
        return new View(yaw, pitch);
    }

    private static void correctView(Player player, View target) {
        // paired axis correction using the restored probe baseline
        player.setTag(PENDING_VIEW, target);
        CursorProbe.updateWithRotation(player, new PlayerRotationPacket(target.yaw(), false, target.pitch(), false));
    }

    public record CursorPosition(float yaw, float pitch) {
        public CursorPosition {
            if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
                throw new IllegalArgumentException("Cursor angles must be finite");
            }
        }
    }

    public record View(float yaw, float pitch) {
    }
}
