package com.japicraft.environment;

import com.japicraft.camera.CursorProbe;
import net.minestom.server.entity.Player;

/**
 * Screen coordinates use a top-left origin, matching the fullscreen cursor shader.
 */
public final class LobbyUi {
    public static final int READY_BUTTON = 3;

    // Keep these bounds in sync with assets/eris/shaders/include/lobby.glsl.
    public static int hoveredButton(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y)) return -1;
        if (y >= 0.755f && y <= 0.835f) {
            for (int role = 0; role < 3; role++) {
                float left = 0.08f + role * 0.29f;
                if (x >= left && x <= left + 0.26f) return role;
            }
        }
        return x >= 0.35f && x <= 0.65f && y >= 0.87f && y <= 0.935f ? READY_BUTTON : -1;
    }

    public static void click(Player player) {
        if (player.getInstance() == null || GameStageManager.isGame(player)) return;
        CursorProbe.ScreenPosition cursor = CursorProbe.screenPosition(player);
        int button = hoveredButton(cursor.x(), cursor.y());
        if (button == READY_BUTTON) GameStageManager.toggleReady(player);
        else if (button >= 0) GameStageManager.selectRole(player, PlayerRole.values()[button]);
    }
}
