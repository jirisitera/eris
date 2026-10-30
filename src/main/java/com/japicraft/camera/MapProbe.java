package com.japicraft.camera;

import com.japicraft.Eris;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.color.Color;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class MapProbe {
    public static final int MONITOR_COUNT = 3;
    public static final int MAX_DOTS = 16;
    // Lobby "YES" button on the main (center) monitor, measured in monitor-height units. Must match cursor.fsh.
    public static final int LOBBY_MONITOR = 1;
    // Left monitor showing the night clock (12 AM -> 6 AM)
    public static final int CLOCK_MONITOR = 0;
    private static final float MAX_COORDINATE = 4094.0F;
    private static final float BUTTON_HALF_SIZE = 0.18F;
    private static final float BUTTON_CENTER = 0.22F;
    private static final float LOBBY_BUTTON_CENTER_Y = 0.68F;
    private static final float LOBBY_BUTTON_HALF_WIDTH = 0.2F;
    private static final float LOBBY_BUTTON_HALF_HEIGHT = 0.11F;
    // Slot 0 marker that switches a monitor into the lobby screen (energy dots never use g > 16, so no clash)
    private static final Color LOBBY_MARKER = new Color(91, 126, 209);
    private static final String COMPUTER_ON_MODEL = Eris.NAMESPACE + Eris.NAMESPACE_SEPARATOR + "computer_on";
    private static final Color DEFAULT_COLOR = new Color(255, 255, 255);
    private static final Color UNUSED_POSITION = new Color(255, 255, 255);
    private static final Tag<MonitorDesk> TAG = Tag.Transient("monitorDesk");

    public static Color encodePosition(float x, float y) {
        int encodedX = Math.round(Math.clamp(x, 0.0F, 1.0F) * MAX_COORDINATE);
        int encodedY = Math.round(Math.clamp(y, 0.0F, 1.0F) * MAX_COORDINATE);
        int red = (encodedX >>> 4) & 0xFF;
        int green = ((encodedX & 0xF) << 4) | ((encodedY >>> 8) & 0xF);
        int blue = encodedY & 0xFF;
        return new Color(red, green, blue);
    }

    private static int encodeAngle(float angle) {
        return Math.round(Math.clamp(angle + 180.0F, 0.0F, 360.0F) / 360.0F * 65535.0F);
    }

    private static int cursorKey(Player player) {
        float yawBias = ClientView.wrapDegrees(ClientView.mouseYaw(player) - ClientView.yaw(player));
        float pitchBias = ClientView.wrapDegrees(ClientView.mousePitch(player) - ClientView.pitch(player));
        // Quantize to 0.05 degrees so tiny floating-point jitter inside the monitors doesn't trigger item updates
        int yaw = encodeAngle(Math.round(yawBias * 20.0F) / 20.0F);
        int pitch = encodeAngle(Math.round(pitchBias * 20.0F) / 20.0F);
        return (yaw << 16) | pitch;
    }

    public static ItemStack createMonitorItem(int monitorIndex, List<Dot> dots, int cursorKey) {
        int yaw = cursorKey >>> 16;
        int pitch = cursorKey & 0xFFFF;
        int count = Math.min(dots.size(), MAX_DOTS);

        List<RGBLike> colors = new ArrayList<>(MAX_DOTS * 2 + 2);
        for (int i = 0; i < MAX_DOTS; i++) {
            if (i < count) {
                Dot dot = dots.get(i);
                colors.add(encodePosition(dot.x(), dot.y()));
                colors.add(dot.color());
            } else {
                colors.add(UNUSED_POSITION);
                colors.add(DEFAULT_COLOR);
            }
        }
        // Tint 32 and 33: cursor yaw & pitch
        colors.add(new Color(yaw >>> 8, yaw & 0xFF, pitch >>> 8));
        colors.add(new Color(pitch & 0xFF, 91, 126));

        return ItemStack.of(Material.ECHO_SHARD)
            .withItemModel(COMPUTER_ON_MODEL)
            .withCustomModelData(List.of((float) monitorIndex), List.of(), List.of(), colors);
    }

    public static void activateMonitor(Player player, int monitorIndex, Entity monitorEntity, List<Dot> dots) {
        if (monitorIndex < 0 || monitorIndex >= MONITOR_COUNT) {
            return;
        }
        MonitorDesk desk = player.getTag(TAG);
        if (desk == null) {
            desk = new MonitorDesk();
            player.setTag(TAG, desk);
        }
        List<Dot> clamped = dots.size() > MAX_DOTS ? List.copyOf(dots.subList(0, MAX_DOTS)) : List.copyOf(dots);
        int key = cursorKey(player);
        desk.lastCursorKey = key;
        desk.entities[monitorIndex] = monitorEntity;
        desk.dots[monitorIndex] = clamped;

        ItemStack item = createMonitorItem(monitorIndex, clamped, key);
        monitorEntity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(item));
    }

    public static void deactivateMonitor(Player player, int monitorIndex) {
        if (monitorIndex < 0 || monitorIndex >= MONITOR_COUNT) {
            return;
        }
        MonitorDesk desk = player.getTag(TAG);
        if (desk == null) {
            return;
        }
        desk.entities[monitorIndex] = null;
        desk.dots[monitorIndex] = List.of();
    }

    public static void setDots(Player player, int monitorIndex, Dot... dots) {
        setDots(player, monitorIndex, List.of(dots));
    }

    public static void setDots(Player player, int monitorIndex, List<Dot> dots) {
        if (monitorIndex < 0 || monitorIndex >= MONITOR_COUNT) {
            return;
        }
        MonitorDesk desk = player.getTag(TAG);
        if (desk == null || desk.entities[monitorIndex] == null) {
            return;
        }
        List<Dot> clamped = dots.size() > MAX_DOTS ? List.copyOf(dots.subList(0, MAX_DOTS)) : List.copyOf(dots);
        if (clamped.equals(desk.dots[monitorIndex])) {
            return;
        }
        desk.dots[monitorIndex] = clamped;
        int key = cursorKey(player);
        desk.lastCursorKey = key;
        ItemStack item = createMonitorItem(monitorIndex, clamped, key);
        desk.entities[monitorIndex].editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(item));
    }

    public static Dot energyDot(int percent, int usage, int maxUsage) {
        return new Dot(0.0F, 0.0F, new Color(Math.clamp(percent, 0, 100), Math.clamp(usage, 0, 16), Math.clamp(maxUsage, 1, 16)));
    }

    public static void setEnergy(Player player, int percent, int usage) {
        setEnergy(player, percent, usage, 4);
    }

    public static void setEnergy(Player player, int percent, int usage, int maxUsage) {
        setDots(player, 1, energyDot(percent, usage, maxUsage));
    }

    public static Dot clockDot(int hour, float progress) {
        // r stays <= 6, so this never clashes with the lobby marker in lobbyDots()
        return new Dot(0.0F, 0.0F, new Color(Math.clamp(hour, 0, 6), Math.round(Math.clamp(progress, 0.0F, 1.0F) * 255.0F), 0));
    }

    public static void setClock(Player player, int hour, float progress) {
        setDots(player, CLOCK_MONITOR, clockDot(hour, progress));
    }

    public static void updateCursor(Player player) {
        MonitorDesk desk = player.getTag(TAG);
        if (desk == null) {
            return;
        }
        int key = cursorKey(player);
        if (key == desk.lastCursorKey) {
            return;
        }
        desk.lastCursorKey = key;
        for (int i = 0; i < MONITOR_COUNT; i++) {
            Entity entity = desk.entities[i];
            if (entity != null) {
                ItemStack item = createMonitorItem(i, desk.dots[i], key);
                entity.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(item));
            }
        }
    }

    public static ScreenPosition screenPosition(Player player) {
        float x = Math.clamp((ClientView.mouseYaw(player) + ClientView.MAX_CURSOR_YAW) / (ClientView.MAX_CURSOR_YAW * 2.0F), 0.0F, 1.0F);
        float y = Math.clamp((ClientView.mousePitch(player) + ClientView.MAX_CURSOR_PITCH) / (ClientView.MAX_CURSOR_PITCH * 2.0F), 0.0F, 1.0F);
        return new ScreenPosition(x, y);
    }

    /**
     * Returns the camera button (0..3, row-major: top-left, top-right, bottom-left, bottom-right)
     * under the cursor on the right monitor, or -1. Must match buttonAt() in cursor.fsh.
     */
    public static int hoveredButton(Player player) {
        ScreenPosition screen = screenPosition(player);
        float virtualX = Math.clamp(screen.x() * MONITOR_COUNT, 0.0F, MONITOR_COUNT - 0.0001F);
        if ((int) Math.floor(virtualX) != 2) {
            return -1;
        }
        float u = virtualX - (float) Math.floor(virtualX);
        float v = screen.y();
        float px = (u - 0.5F) * (11.0F / 8.0F);
        float py = v - 0.5F;
        float dx = Math.abs(px) - BUTTON_CENTER;
        float dy = Math.abs(py) - BUTTON_CENTER;
        if (Math.max(Math.abs(dx), Math.abs(dy)) > BUTTON_HALF_SIZE) {
            return -1;
        }
        return (px > 0.0F ? 1 : 0) + (py > 0.0F ? 2 : 0);
    }

    /**
     * Data for the lobby screen ("Do you want to play a game?" + YES button).
     */
    public static List<Dot> lobbyDots() {
        return List.of(new Dot(0.0F, 0.0F, LOBBY_MARKER));
    }

    /**
     * Returns true if the cursor is over the lobby YES button on the main monitor. Must match lobbyButton() in cursor.fsh.
     */
    public static boolean isLobbyButtonHovered(Player player) {
        ScreenPosition screen = screenPosition(player);
        float virtualX = Math.clamp(screen.x() * MONITOR_COUNT, 0.0F, MONITOR_COUNT - 0.0001F);
        if ((int) Math.floor(virtualX) != LOBBY_MONITOR) {
            return false;
        }
        float u = virtualX - (float) Math.floor(virtualX);
        float v = screen.y();
        float px = (u - 0.5F) * (11.0F / 8.0F);
        float py = v - LOBBY_BUTTON_CENTER_Y;
        return Math.abs(px) <= LOBBY_BUTTON_HALF_WIDTH && Math.abs(py) <= LOBBY_BUTTON_HALF_HEIGHT;
    }

    public record ScreenPosition(float x, float y) {
    }

    public static void detach(Player player) {
        player.removeTag(TAG);
    }

    public record Dot(float x, float y, Color color) {
        public Dot {
            Objects.requireNonNull(color, "color");
        }
    }

    private static final class MonitorDesk {
        private final Entity[] entities = new Entity[MONITOR_COUNT];
        @SuppressWarnings("unchecked")
        private final List<Dot>[] dots = new List[]{List.of(), List.of(), List.of()};
        private int lastCursorKey = -1;
    }
}
