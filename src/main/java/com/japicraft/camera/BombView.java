package com.japicraft.camera;

import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.PlayerRole;
import com.japicraft.environment.GameStageManager;
import com.japicraft.environment.StructureLoader;
import com.japicraft.service.ActionBarService;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.tag.Tag;

/** A private, world-space cursor on the open case's horizontal work surface. */
public final class BombView {
    private static final Tag<State> STATE = Tag.Transient("bombView");
    // Center of the open lower case: floor at y=1, rim at y=4 model units.
    public static final double SURFACE_HEIGHT = 2.5 / 16.0;
    public static final float CURSOR_RANGE = 35.0F;
    public static final Pos CAMERA_POSITION = StructureLoader.CASE_SPAWN.add(0, 1.0, -0.45).withView(0, 61.9F);

    public static boolean isActive(Player player) {
        State state = player.getTag(STATE);
        return state != null && state.active;
    }

    /** Spawn once per player/instance; shader activation does not change entity lifetime. */
    public static void attach(Player player) {
        detach(player);
        if (PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER || player.getInstance() == null) return;
        Entity cursor = new Entity(EntityType.ITEM_DISPLAY);
        cursor.setNoGravity(true);
        cursor.setAutoViewable(false);
        cursor.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(CursorProbe.surfaceItem());
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);
            meta.setBrightness(15, 15);
        });
        State state = new State(cursor);
        player.setTag(STATE, state);
        cursor.setInstance(player.getInstance(), StructureLoader.CASE_SPAWN.add(0, SURFACE_HEIGHT, 0).withView(0, 0))
            .thenRun(() -> {
                if (player.getTag(STATE) == state) cursor.addViewer(player);
                else cursor.remove();
            });
    }

    public static void enter(Player player) {
        if (!GameStageManager.isGame(player) || isActive(player) || PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER
            || !CameraAnchor.isAttached(player) || player.getInstance() == null) return;
        if (!player.hasTag(STATE)) attach(player);
        State state = player.getTag(STATE);
        state.fullscreenCursor = EmoteWheel.dismissForModeSwitch(player);
        state.active = true;
        // Switch logical cursors and shader mode together, without resetting client rotation.
        ClientView.selectCursor(player, state.surfaceCursor);
    }

    public static void exit(Player player) {
        State state = player.getTag(STATE);
        if (state == null || !state.active) return;
        state.surfaceCursor = ClientView.cursorPosition(player);
        state.active = false;
        ClientView.selectCursor(player, state.fullscreenCursor);
    }

    /** Normalized surface coordinates, matching the constrained mouse range. */
    public static CursorProbe.ScreenPosition surfacePosition(Player player) {
        return new CursorProbe.ScreenPosition(
            Math.clamp(0.5F + ClientView.mouseYaw(player) / (2 * CURSOR_RANGE), 0, 1),
            Math.clamp(0.5F + ClientView.mousePitch(player) / (2 * CURSOR_RANGE), 0, 1));
    }

    public static void update(Player player) {
        if (isActive(player) && PlayerRole.get(player) != PlayerRole.BOMB_DEFUSER) exit(player);
    }

    public static void detach(Player player) {
        State state = player.getTag(STATE);
        player.removeTag(STATE);
        if (state != null) state.cursor.remove();
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
        boolean active;
        ClientView.CursorPosition fullscreenCursor = new ClientView.CursorPosition(0, 0);
        ClientView.CursorPosition surfaceCursor = new ClientView.CursorPosition(0, 0);

        State(Entity cursor) {
            this.cursor = cursor;
        }
    }
}
