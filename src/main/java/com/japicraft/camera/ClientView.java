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
                // Rotation-only packets retain unbounded yaw. A fast movement can exceed
                // half a turn between packets; wrapping that delta would reverse its direction.
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
            // Rotation corrections immediately echo a rotation packet from the client.
            // Ignore old in-flight input and the correction itself on both axes until that echo arrives.
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
        // Minecraft clamps its own pitch to +/-90 independently of the virtual cursor.
        // Edge overscroll and mode switches can offset these ranges. Rebase before the
        // physical pitch limit strands the cursor short of its canvas edge.
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

    /**
     * Change cursors and align the client's physical pitch range with the selected canvas.
     */
    public static void selectCursor(Player player, CursorPosition position) {
        restoreCursor(player, position);
    }

    public static void restoreCursor(Player player, CursorPosition position) {
        float yawLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_YAW;
        float pitchLimit = BombView.isActive(player) ? BombView.CURSOR_RANGE : MAX_CURSOR_PITCH;
        float yaw = Math.clamp(position.yaw(), -yawLimit, yawLimit);
        float pitch = Math.clamp(position.pitch(), -pitchLimit, pitchLimit);
        player.setTag(MOUSE_YAW, yaw);
        player.setTag(MOUSE_PITCH, pitch);
        correctView(player, new View(yaw, pitch));
    }

    private static void correctView(Player player, View target) {
        // Correct both client axes together, using the same baseline as the restored probe.
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
