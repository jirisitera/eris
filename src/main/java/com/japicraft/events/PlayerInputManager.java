package com.japicraft.events;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.MapProbe;
import com.japicraft.game.Director;
import com.japicraft.game.Lobby;
import com.japicraft.server.PostEffect;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerInputEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;
import net.minestom.server.timer.TaskSchedule;

public final class PlayerInputManager {
    public static void register() {
        MinecraftServer.getGlobalEventHandler().addListener(PlayerHandAnimationEvent.class, event -> {
            Player player = event.getPlayer();
            if (CameraAnchor.getCameraPosition(player) == null) {
                return;
            }
            int cameraIndex;
            if (CameraAnchor.isMainCamera(player)) {
                // lobby: only the YES button on the main monitor responds, doors and cameras are locked
                if (Lobby.handleClick(player)) {
                    return;
                }

                Director director = player.getTag(Director.TAG);
                if (director != null && director.isGameOver()) {
                    director.resetToLobby(player);
                    return;
                }

                float leanYaw = CameraAnchor.calculateLeanYaw(player);
                if (leanYaw != 0.0F) {
                    if (director != null && player.getInstance() instanceof InstanceContainer container) {
                        if (leanYaw < 0.0F) {
                            director.doors().toggleRight(player, container);
                        } else {
                            director.doors().toggleLeft(player, container);
                        }
                    }
                    return;
                }

                // at the desk: only the camera buttons on the right monitor switch cameras
                int button = MapProbe.hoveredButton(player);
                if (button < 0) {
                    return;
                }
                cameraIndex = CameraAnchor.setCamera(player, button + 1);
            } else {
                // viewing a camera: click to return to the desk
                cameraIndex = CameraAnchor.setCamera(player, 0);
            }
            if (cameraIndex < 0) {
                return;
            }
            player.sendPacket(new PostEffectsPacket(CameraAnchor.isMainCamera(player) ? PostEffect.resolveActiveEffects(player) : PostEffect.resolveActiveEffects(player, PostEffect.CAMERA_SWITCH)));

            if (!CameraAnchor.isMainCamera(player)) {
                player.scheduler().scheduleTask(() -> {
                    if (CameraAnchor.isMainCamera(player)) return;
                    Director director = player.getTag(Director.TAG);
                    if (director != null && director.isCameraJammed(CameraAnchor.getActiveCamera(player))) {
                        return;
                    }
                    // stabilize camera
                    PostEffect.sync(player);
                }, TaskSchedule.tick(5), TaskSchedule.stop());
            }
        });

        MinecraftServer.getGlobalEventHandler().addListener(PlayerInputEvent.class, event -> {
            Player player = event.getPlayer();
            CameraAnchor.updateInput(player);
        });
    }
}
