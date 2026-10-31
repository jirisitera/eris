package com.japicraft.emote;

import com.japicraft.camera.CursorProbe;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;

public final class EmoteWheel {
    public static final int EMOTE_COUNT = 8;
    private static final float SCREEN_ASPECT_RATIO = 16.0F / 9.0F;
    private static final float CENTER_DEADZONE = 0.12F;
    private static final Tag<Boolean> OPEN_TAG = Tag.Transient("emoteWheelOpen");

    // Returns 0..7 clockwise from top (12 o'clock), or -1 inside the center deadzone
    public static int hoveredSlice(Player player) {
        CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
        float dx = (cursor.x() - 0.5F) * 2.0F * SCREEN_ASPECT_RATIO;
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
        return player.hasTag(OPEN_TAG);
    }

    public static void open(Player player) {
        player.setTag(OPEN_TAG, true);
        CursorProbe.update(player);
    }

    public static void close(Player player) {
        if (!isOpen(player)) {
            return;
        }
        player.removeTag(OPEN_TAG);
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
    }
}
