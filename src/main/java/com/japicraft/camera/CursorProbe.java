package com.japicraft.camera;

import com.japicraft.Eris;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.GameStageManager;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.BundlePacket;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.tag.Tag;

import java.util.List;

public final class CursorProbe {
    private static final String ITEM_MODEL = Eris.NAMESPACE + ":ui_probe";
    private static final Color BUFFER_COLOR = new Color(91, 126, 209);
    private static final Tag<ProbeState> TAG = Tag.Transient("cursorProbe");

    public static boolean isProbe(ItemStack item) {
        return item.material() == Material.ECHO_SHARD && ITEM_MODEL.equals(item.get(DataComponents.ITEM_MODEL));
    }

    // Encodes angles and UI flags into the 8 custom-model-data colors read by cursor.fsh at row y=19
    private static ItemStack createProbe(int mouseYaw, int mousePitch, int rawYaw, int rawPitch, boolean emoteWheelOpen, boolean surfaceMode, int lobbyData) {
        return ItemStack.of(Material.ECHO_SHARD).withItemModel(ITEM_MODEL).withCustomModelData(List.of(), List.of(), List.of(), List.of(
            new Color(mouseYaw >>> 8, mouseYaw & 0xFF, mousePitch >>> 8),
            new Color(mousePitch & 0xFF, 91, 126),
            new Color(rawYaw >>> 8, rawYaw & 0xFF, rawPitch >>> 8),
            new Color(rawPitch & 0xFF, 91, 126),
            BUFFER_COLOR,
            new Color(emoteWheelOpen ? 255 : 0, 91, 126),
            new Color(surfaceMode ? 255 : 0, 91, 126),
            new Color(lobbyData)
        ));
    }

    public static ItemStack surfaceItem() {
        return ItemStack.of(Material.ECHO_SHARD).withItemModel("eris:surface_cursor")
            .withCustomModelData(List.of(), List.of(), List.of(), List.of(
                new Color(0, 125, 209), new Color(1, 125, 209),
                new Color(2, 125, 209), new Color(3, 125, 209),
                new Color(4, 125, 209), new Color(5, 125, 209),
                new Color(6, 125, 209), new Color(7, 125, 209)));
    }

    private static int encodeAngle(float angle, float range) {
        float halfRange = range * 0.5F;
        return Math.round(Math.clamp(angle + halfRange, 0.0F, range) / range * 65535.0F);
    }

    private static float wrapAngle(float angle) {
        float halfRange = (float) 1440.0 * 0.5F;
        return ((angle + halfRange) % (float) 1440.0 + (float) 1440.0) % (float) 1440.0 - halfRange;
    }

    public static void update(Player player) {
        update(player, false);
    }

    public static void forceUpdate(Player player) {
        update(player, true);
    }

    public static void updateWithRotation(Player player, PlayerRotationPacket rotation) {
        // Do not render an intermediate frame with a new pitch and the old wheel/probe data.
        player.sendPacket(new BundlePacket());
        try {
            update(player);
            player.sendPacket(new SetSlotPacket(0, 0, (short) 45, player.getEquipment(EquipmentSlot.OFF_HAND)));
            player.sendPacket(rotation);
        } finally {
            player.sendPacket(new BundlePacket());
        }
    }

    private static void update(Player player, boolean force) {
        int mouseYaw = encodeAngle(ClientView.mouseYaw(player), 360.0F);
        int mousePitch = encodeAngle(ClientView.mousePitch(player), 360.0F);
        int rawYaw = encodeAngle(wrapAngle(ClientView.yaw(player)), 1440.0F);
        int rawPitch = encodeAngle(ClientView.pitch(player), 180.0F);
        boolean emoteWheelOpen = EmoteWheel.isOpen(player);
        boolean surfaceMode = BombView.isActive(player);
        int lobbyData = GameStageManager.lobbyUiData(player);
        long key = ((long) mouseYaw << 48) | ((long) mousePitch << 32) | ((long) rawYaw << 16) | rawPitch;

        ProbeState previous = player.getTag(CursorProbe.TAG);
        boolean unchanged = previous != null && previous.key == key && previous.emoteWheelOpen == emoteWheelOpen
            && previous.surfaceMode == surfaceMode && previous.lobbyData == lobbyData;
        if (!force && unchanged && player.getEquipment(EquipmentSlot.OFF_HAND) == previous.item) {
            return;
        }
        ItemStack item = !force && unchanged
            ? previous.item
            : createProbe(mouseYaw, mousePitch, rawYaw, rawPitch, emoteWheelOpen, surfaceMode, lobbyData);
        player.setTag(CursorProbe.TAG, new ProbeState(key, emoteWheelOpen, surfaceMode, lobbyData, item));

        if (force || player.getEquipment(EquipmentSlot.OFF_HAND) != item) {
            player.setEquipment(EquipmentSlot.OFF_HAND, item);
        }

        if (force) {
            player.getInventory().update();
            player.sendPacket(new SetSlotPacket(0, 0, (short) 45, item));
        }
    }

    public static ScreenPosition screenPosition(Player player) {
        float x = Math.clamp((ClientView.mouseYaw(player) + ClientView.MAX_CURSOR_YAW) / (ClientView.MAX_CURSOR_YAW * 2.0F), 0.0F, 1.0F);
        float y = Math.clamp((ClientView.mousePitch(player) + ClientView.MAX_CURSOR_PITCH) / (ClientView.MAX_CURSOR_PITCH * 2.0F), 0.0F, 1.0F);
        return new ScreenPosition(x, y);
    }

    public static void detach(Player player) {
        player.removeTag(CursorProbe.TAG);
    }

    public record ScreenPosition(float x, float y) {
    }

    private record ProbeState(long key, boolean emoteWheelOpen, boolean surfaceMode, int lobbyData, ItemStack item) {
    }
}
