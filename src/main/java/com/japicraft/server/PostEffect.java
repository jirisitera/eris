package com.japicraft.server;

import com.japicraft.Eris;
import com.japicraft.camera.CameraAnchor;
import net.kyori.adventure.key.Key;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.List;

public class PostEffect {
    public static final Key HUD = Key.key(Eris.NAMESPACE, "hud");
    public static final Key CAMERA = Key.key(Eris.NAMESPACE, "security_camera");
    public static final Key CAMERA_SWITCH = Key.key(Eris.NAMESPACE, "camera_switch");
    public static final Key BLACKOUT = Key.key(Eris.NAMESPACE, "blackout");
    public static final Tag<Boolean> BLACKOUT_TAG = Tag.Transient("playerBlackout");

    public static void applyBlackout(Player player) {
        setBlackout(player, true);
    }

    public static void removeBlackout(Player player) {
        setBlackout(player, false);
    }

    public static boolean isBlackout(Player player) {
        Boolean tag = player.getTag(BLACKOUT_TAG);
        if (tag == null) {
            return false;
        }
        return tag;
    }

    public static void setBlackout(Player player, boolean active) {
        player.setTag(BLACKOUT_TAG, active ? true : null);
        sync(player);
    }

    public static List<Key> resolveActiveEffects(Player player, Key... additional) {
        List<Key> effects = new ArrayList<>();
        effects.add(HUD);
        if (!CameraAnchor.isMainCamera(player)) {
            effects.add(CAMERA);
        }
        for (Key key : additional) {
            if (!effects.contains(key)) {
                effects.add(key);
            }
        }
        if (isBlackout(player) && !effects.contains(BLACKOUT)) {
            effects.add(BLACKOUT);
        }
        return effects;
    }

    public static void sync(Player player) {
        player.sendPacket(new PostEffectsPacket(resolveActiveEffects(player)));
    }
}
