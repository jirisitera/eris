package com.japicraft.emote;

import com.japicraft.camera.BombView;
import com.japicraft.camera.ClientView;
import com.japicraft.camera.CursorProbe;
import com.japicraft.game.GameStageManager;
import com.japicraft.service.NotificationService;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;

public final class EmoteWheel {
    public static final int EMOTE_COUNT = 8;
    // logical wheel cursor aspect shared with post/emotes.glsl
    // viewport mapping in the shader, independent of client dimensions on the server
    private static final float CURSOR_REFERENCE_ASPECT = 16.0F / 9.0F;
    private static final float CENTER_DEADZONE = 0.12F;
    private static final Tag<Boolean> OPEN_TAG = Tag.Transient("emoteWheelOpen");
    private static final Tag<ClientView.CursorPosition> CURSOR_TAG = Tag.Transient("emoteWheelCursor");
    private static final Tag<ClientView.CursorPosition> RESTORE_TAG = Tag.Transient("emoteWheelCursorRestore");

    // slice 0..7 clockwise from 12 o'clock, or -1 inside the center deadzone
    public static int hoveredSlice(Player player) {
        if (BombView.isActive(player)) return -1;
        CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
        float dx = (cursor.x() - 0.5F) * 2.0F * CURSOR_REFERENCE_ASPECT;
        float dy = (cursor.y() - 0.5F) * 2.0F;
        if (Math.hypot(dx, dy) < CENTER_DEADZONE) {
            return -1;
        }
        double angle = Math.atan2(dx, -dy);
        double twoPi = Math.PI * 2.0;
        double shifted = (angle + twoPi + Math.PI / 8.0) % twoPi;
        return (int) Math.floor(shifted / (Math.PI / 4.0)) % EMOTE_COUNT;
    }

    public static boolean isOpen(Player player) {
        return !BombView.isActive(player) && player.hasTag(OPEN_TAG);
    }

    public static boolean isTurningPaused(Player player) {
        return isOpen(player) || player.hasTag(RESTORE_TAG);
    }

    public static void open(Player player) {
        if (!GameStageManager.isGame(player) || BombView.isActive(player) || isOpen(player)) {
            return;
        }
        ClientView.CursorPosition pending = player.getTag(RESTORE_TAG);
        player.removeTag(RESTORE_TAG);
        player.setTag(CURSOR_TAG, pending != null ? pending : ClientView.cursorPosition(player));
        player.setTag(OPEN_TAG, true);
        CursorProbe.update(player);
    }

    public static void perform(Player player, int emoteId) {
        switch (emoteId) {
            case 0 -> EmoteManager.play(player, "steve", "wave");
            case 1 -> EmoteManager.play(player, "steve", "roll");
            default -> {
                EmoteWheel.close(player);
                return;
            }
        }
        NotificationService.info(player, "Selected emote " + emoteId + ".");
        EmoteWheel.close(player);
    }

    public static void close(Player player) {
        if (!isOpen(player)) {
            return;
        }
        ClientView.CursorPosition cursor = player.getTag(CURSOR_TAG);
        player.removeTag(CURSOR_TAG);
        player.removeTag(OPEN_TAG);
        if (cursor != null) {
            var restore = new ClientView.CursorPosition(cursor.yaw(), cursor.pitch());
            player.setTag(RESTORE_TAG, restore);
            player.scheduler().scheduleNextTick(() -> {
                // restoration invalidated by reopening, closing or disconnecting
                if (player.getTag(RESTORE_TAG) != restore) {
                    return;
                }
                player.removeTag(RESTORE_TAG);
                ClientView.restoreCursor(player, restore);
            });
        }
        CursorProbe.update(player);
    }

    public static void toggle(Player player) {
        if (isOpen(player)) {
            close(player);
        } else {
            open(player);
        }
    }

    public static void detach(Player player) {
        player.removeTag(OPEN_TAG);
        player.removeTag(CURSOR_TAG);
        player.removeTag(RESTORE_TAG);
    }

    public static ClientView.CursorPosition dismissForModeSwitch(Player player) {
        ClientView.CursorPosition saved = player.getTag(CURSOR_TAG);
        if (saved == null) saved = player.getTag(RESTORE_TAG);
        if (saved == null) saved = ClientView.cursorPosition(player);
        detach(player); // scheduled wheel cursor restoration invalidation
        return saved;
    }
}
