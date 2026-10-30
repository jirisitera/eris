package com.japicraft.camera;

import com.japicraft.Eris;
import net.minestom.server.color.Color;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import java.util.List;

public final class CursorProbe {
    public static final int SECTOR_COLUMNS = 5;
    public static final int SECTOR_ROWS = 4;
    private static final String ITEM_MODEL = Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "ui_probe";
    private static final Color BUFFER_COLOR = new Color(91, 126, 209);
    private static final Tag<ProbeState> TAG = Tag.Transient("cursorProbe");

    // Encodes angles and UI flags into the 8 custom-model-data colors read by cursor.fsh at row y=19
    private static ItemStack createProbe(int mouseYaw, int mousePitch, int rawYaw, int rawPitch, boolean emoteWheelOpen) {
        return ItemStack.of(Material.ECHO_SHARD).withItemModel(ITEM_MODEL).withCustomModelData(List.of(), List.of(), List.of(), List.of(
            new Color(mouseYaw >>> 8, mouseYaw & 0xFF, mousePitch >>> 8),
            new Color(mousePitch & 0xFF, 91, 126),
            new Color(rawYaw >>> 8, rawYaw & 0xFF, rawPitch >>> 8),
            new Color(rawPitch & 0xFF, 91, 126),
            BUFFER_COLOR,
            new Color(emoteWheelOpen ? 255 : 0, 91, 126),
            BUFFER_COLOR,
            BUFFER_COLOR
        ));
    }

    private static int encodeAngle(float angle, float range) {
        float halfRange = range * 0.5F;
        return Math.round(Math.clamp(angle + halfRange, 0.0F, range) / range * 65535.0F);
    }

    private static float wrapAngle(float angle, float range) {
        float halfRange = range * 0.5F;
        float wrapped = ((angle + halfRange) % range + range) % range - halfRange;
        return wrapped;
    }

    public static void update(Player player) {
        int mouseYaw = encodeAngle(ClientView.mouseYaw(player), 360.0F);
        int mousePitch = encodeAngle(ClientView.mousePitch(player), 360.0F);
        int rawYaw = encodeAngle(wrapAngle(ClientView.yaw(player), 1440.0F), 1440.0F);
        int rawPitch = encodeAngle(ClientView.pitch(player), 180.0F);
        boolean emoteWheelOpen = EmoteWheel.isOpen(player);
        long key = ((long) mouseYaw << 48) | ((long) mousePitch << 32) | ((long) rawYaw << 16) | rawPitch;

        ProbeState previous = player.getTag(CursorProbe.TAG);
        if (previous != null && previous.key == key && previous.emoteWheelOpen == emoteWheelOpen && player.getEquipment(EquipmentSlot.OFF_HAND) == previous.item) {
            return;
        }
        ItemStack item = (previous != null && previous.key == key && previous.emoteWheelOpen == emoteWheelOpen)
            ? previous.item
            : createProbe(mouseYaw, mousePitch, rawYaw, rawPitch, emoteWheelOpen);
        player.setTag(CursorProbe.TAG, new ProbeState(key, emoteWheelOpen, item));

        if (player.getEquipment(EquipmentSlot.OFF_HAND) != item) {
            player.setEquipment(EquipmentSlot.OFF_HAND, item);
        }
    }

    public static ScreenPosition screenPosition(Player player) {
        float x = Math.clamp((ClientView.mouseYaw(player) + ClientView.MAX_CURSOR_YAW) / (ClientView.MAX_CURSOR_YAW * 2.0F), 0.0F, 1.0F);
        float y = Math.clamp((ClientView.mousePitch(player) + ClientView.MAX_CURSOR_PITCH) / (ClientView.MAX_CURSOR_PITCH * 2.0F), 0.0F, 1.0F);
        return new ScreenPosition(x, y);
    }

    public static int sector(Player player) {
        ScreenPosition position = screenPosition(player);
        int column = Math.min((int) (position.x() * SECTOR_COLUMNS), SECTOR_COLUMNS - 1);
        int row = Math.min((int) (position.y() * SECTOR_ROWS), SECTOR_ROWS - 1);
        return row * SECTOR_COLUMNS + column;
    }

    public static void detach(Player player) {
        player.removeTag(CursorProbe.TAG);
    }

    public record ScreenPosition(float x, float y) {
    }

    private record ProbeState(long key, boolean emoteWheelOpen, ItemStack item) {
    }
}
