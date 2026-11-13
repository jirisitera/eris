package com.japicraft.lobby;

import com.japicraft.camera.CursorProbe;
import com.japicraft.game.GameStageManager;
import com.japicraft.player.PlayerRole;
import net.minestom.server.entity.Player;

/**
 * screen coordinates use a top-left origin, matching the fullscreen cursor shader
 */
public final class LobbyInterface {
    public static final int PREVIOUS_ROLE_BUTTON = 0;
    public static final int NEXT_ROLE_BUTTON = 1;
    public static final int READY_BUTTON = 3;

    // shared bounds with assets/eris/shaders/include/post/lobby_text.glsl
    public static int hoveredButton(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return -1;
        if (y >= 0.755f && y <= 0.835f) {
            if (x >= 0.24f && x <= 0.31f) return PREVIOUS_ROLE_BUTTON;
            if (x >= 0.69f && x <= 0.76f) return NEXT_ROLE_BUTTON;
        }
        return x >= 0.35f && x <= 0.65f && y >= 0.87f && y <= 0.935f ? READY_BUTTON : -1;
    }

    public static void click(Player player) {
        if (player.getInstance() == null || GameStageManager.isGame(player)) return;
        CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
        int button = hoveredButton(cursor.x(), cursor.y());
        if (button == READY_BUTTON) GameStageManager.toggleReady(player);
        else if (button == PREVIOUS_ROLE_BUTTON || button == NEXT_ROLE_BUTTON) {
            PlayerRole selected = PlayerRole.get(player);
            if (selected == null) return;
            PlayerRole[] roles = PlayerRole.values();
            int direction = button == PREVIOUS_ROLE_BUTTON ? -1 : 1;
            GameStageManager.selectRole(player, roles[Math.floorMod(selected.ordinal() + direction, roles.length)]);
        }
    }
}
