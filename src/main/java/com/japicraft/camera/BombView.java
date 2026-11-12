package com.japicraft.camera;

import com.japicraft.bomb.BombModules;
import com.japicraft.bomb.Surface;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.GameStageManager;
import com.japicraft.environment.PlayerRole;
import com.japicraft.environment.StructureLoader;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.tag.Tag;

/**
 * Shader projection probe plus a shared, world-space cursor on the case's work surface.
 */
public final class BombView {
    // Center of the open lower case: floor at y=1, rim at y=4 model units.
    public static final double SURFACE_HEIGHT = 2.5 / 16.0;
    public static final float CURSOR_RANGE = 35.0F;
    public static final Pos CAMERA_POSITION = StructureLoader.CASE_SPAWN.add(0, 1.0, -0.45).withView(0, 61.9F);
    private static final ItemStack SHARED_CURSOR = ItemStack.of(Material.ECHO_SHARD).withItemModel("eris:shared_cursor");
    private static final Tag<State> STATE = Tag.Transient("bombView");

    public static boolean isActive(Player player) {
        State state = player.getTag(STATE);
        return state != null && state.active;
    }

    /**
     * Spawn once per player/instance; shader activation does not change entity lifetime.
     */
    public static void attach(Player player) {
        if (player.hasTag(STATE)) return;
        if (PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER || player.getInstance() == null) return;
        Entity cursor = new Entity(EntityType.ITEM_DISPLAY);
        cursor.setNoGravity(true);
        cursor.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(CursorProbe.surfaceItem());
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);
            meta.setBrightness(15, 15);
        });
        Entity indicator = new Entity(EntityType.ITEM_DISPLAY);
        indicator.setNoGravity(true);
        indicator.updateViewableRule(viewer -> PlayerRole.get(viewer) == PlayerRole.DEFUSAL_HELPER);
        indicator.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(ItemStack.AIR);
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);
            meta.setBrightness(15, 15);
            meta.setPosRotInterpolationDuration(1);
        });
        State state = new State(cursor, indicator);
        player.setTag(STATE, state);
        indicator.setInstance(player.getInstance(), Surface.world(Surface.cursor(0.5, 0.5), 0.02))
            .thenRun(() -> {
                if (player.getTag(STATE) != state) indicator.remove();
            });
        cursor.setInstance(player.getInstance(), StructureLoader.CASE_SPAWN.add(0, SURFACE_HEIGHT, 0).withView(0, 0))
            .thenRun(() -> {
                if (player.getTag(STATE) != state) cursor.remove();
            });
    }

    /**
     * Clear interaction state between rounds without destroying the two cursor entities.
     */
    public static void reset(Player player) {
        State state = player.getTag(STATE);
        if (state == null) return;
        clearHover(player);
        state.active = false;
        state.lastPoint = null;
        state.fullscreenCursor = new ClientView.CursorPosition(0, 0);
        state.surfaceCursor = new ClientView.CursorPosition(0, 0);
        state.indicator.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(ItemStack.AIR));
        state.indicator.updateViewableRule(viewer -> PlayerRole.get(viewer) == PlayerRole.DEFUSAL_HELPER);
    }

    public static void enter(Player player) {
        if (!GameStageManager.isGame(player) || isActive(player) || PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER
            || !CameraAnchor.isAttached(player) || player.getInstance() == null) return;
        if (!player.hasTag(STATE)) attach(player);
        State state = player.getTag(STATE);
        state.fullscreenCursor = EmoteWheel.dismissForModeSwitch(player);
        state.active = true;
        // Switch logical cursors, client rotation, and shader mode together.
        ClientView.selectCursor(player, state.surfaceCursor);
        state.indicator.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(SHARED_CURSOR));
        update(player);
    }

    public static void exit(Player player) {
        State state = player.getTag(STATE);
        if (state == null || !state.active) return;
        state.surfaceCursor = ClientView.cursorPosition(player);
        state.active = false;
        clearHover(player);
        state.indicator.editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(ItemStack.AIR));
        state.lastPoint = null;
        ClientView.selectCursor(player, state.fullscreenCursor);
    }

    /**
     * Normalized surface coordinates, matching the constrained mouse range.
     */
    public static CursorProbe.ScreenPosition surfacePosition(Player player) {
        return new CursorProbe.ScreenPosition(
            Math.clamp(0.5F + ClientView.mouseYaw(player) / (2 * CURSOR_RANGE), 0, 1),
            Math.clamp(0.5F + ClientView.mousePitch(player) / (2 * CURSOR_RANGE), 0, 1));
    }

    public static void update(Player player) {
        if (isActive(player) && PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER) exit(player);
        if (!isActive(player)) return;
        State state = player.getTag(STATE);
        CursorProbe.ScreenPosition position = surfacePosition(player);
        Surface.Point point = Surface.cursor(position.x(), position.y());
        if (!point.equals(state.lastPoint)) {
            state.lastPoint = point;
            state.indicator.teleport(Surface.world(point, 0.02));
        }
        BombModules bomb = BombModules.get(player.getInstance());
        if (bomb != null) bomb.hover(point);
    }

    public static void press(Player player) {
        if (!isActive(player) || !GameStageManager.isGame(player)
            || PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER || player.getInstance() == null) return;
        BombModules bomb = BombModules.get(player.getInstance());
        if (bomb == null) return;
        CursorProbe.ScreenPosition position = surfacePosition(player);
        bomb.press(Surface.cursor(position.x(), position.y()));
    }

    private static void clearHover(Player player) {
        if (player.getInstance() == null) return;
        BombModules bomb = BombModules.get(player.getInstance());
        if (bomb != null) bomb.hover(null);
    }

    public static void detach(Player player) {
        State state = player.getTag(STATE);
        player.removeTag(STATE);
        if (state != null) {
            if (state.active) clearHover(player);
            state.cursor.remove();
            state.indicator.remove();
        }
    }

    public static float headYaw(Player player) {
        return isActive(player)
            ? ClientView.wrapDegrees(CameraAnchor.baseYaw(player) + ClientView.mouseYaw(player))
            : CameraAnchor.viewYaw(player);
    }

    public static float headPitch(Player player) {
        return isActive(player)
            ? Math.clamp(CAMERA_POSITION.pitch() + ClientView.mousePitch(player) * 0.5F, -90, 90)
            : CameraAnchor.viewPitch(player);
    }

    private static final class State {
        final Entity cursor;
        final Entity indicator;
        Surface.Point lastPoint;
        boolean active;
        ClientView.CursorPosition fullscreenCursor = new ClientView.CursorPosition(0, 0);
        ClientView.CursorPosition surfaceCursor = new ClientView.CursorPosition(0, 0);

        State(Entity cursor, Entity indicator) {
            this.cursor = cursor;
            this.indicator = indicator;
        }
    }
}
