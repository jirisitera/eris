package com.japicraft.environment;

import com.japicraft.camera.BombView;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.ClientView;
import net.minestom.server.MinecraftServer;
import net.minestom.server.network.ConnectionState;
import net.minestom.server.network.packet.server.play.CameraPacket;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;

import static org.junit.jupiter.api.Assertions.*;

class RoleCameraTest {
    @BeforeAll
    static void initialize() {
        MinecraftServer.init();
    }

    private Player player(PlayerRole role) {
        Player player = new Player(new PlayerConnection() {
            @Override public void sendPacket(SendablePacket packet) {
                if (packet instanceof CameraPacket) {
                    assertEquals(ConnectionState.PLAY, getServerState());
                    assertEquals(ConnectionState.PLAY, getClientState());
                }
            }
            @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress(0); }
        }, new GameProfile(UUID.randomUUID(), "Tester"));
        player.getPlayerConnection().setClientState(ConnectionState.PLAY);
        player.getPlayerConnection().setServerState(ConnectionState.PLAY);
        player.setTag(PlayerRole.TAG, role);
        player.setInstance(MinecraftServer.getInstanceManager().createInstanceContainer(), InstanceManager.spawnFor(role)).join();
        CameraAnchor.attach(player);
        BombView.attach(player);
        return player;
    }

    @Test
    void lateViewerReceivesExistingPlayersBodyMount() {
        Player existing = player(PlayerRole.BOMB_DEFUSER);
        List<SendablePacket> packets = new ArrayList<>();
        Player viewer = new Player(new PlayerConnection() {
            @Override public void sendPacket(SendablePacket packet) { packets.add(packet); }
            @Override public SocketAddress getRemoteAddress() { return new InetSocketAddress(0); }
        }, new GameProfile(UUID.randomUUID(), "LateViewer"));
        viewer.getPlayerConnection().setClientState(ConnectionState.PLAY);
        viewer.getPlayerConnection().setServerState(ConnectionState.PLAY);
        viewer.setInstance(existing.getInstance(), InstanceManager.HELPER_SPAWN).join();
        existing.addViewer(viewer);
        existing.getVehicle().addViewer(viewer);
        packets.clear();
        CameraAnchor.syncBodyMounts(viewer);
        assertTrue(packets.stream().anyMatch(packet -> packet instanceof SetPassengersPacket mount
            && mount.vehicleEntityId() == existing.getVehicle().getEntityId()
            && mount.passengersId().contains(existing.getEntityId())));
        BombView.detach(existing);
        CameraAnchor.detach(existing);
    }

    @Test
    void configurationCleanupDoesNotSendPlayCameraPackets() {
        Player player = player(PlayerRole.BOMB_DEFUSER);
        player.getPlayerConnection().setServerState(ConnectionState.CONFIGURATION);
        player.getPlayerConnection().setClientState(ConnectionState.CONFIGURATION);
        assertDoesNotThrow(() -> CameraAnchor.detach(player));
        assertFalse(CameraAnchor.isAttached(player));
        assertDoesNotThrow(() -> CameraAnchor.detach(player));
        BombView.detach(player);
    }

    @Test
    void rolesMapToTheirNamedSpawns() {
        assertEquals(InstanceManager.DEFUSER_SPAWN, InstanceManager.spawnFor(PlayerRole.BOMB_DEFUSER));
        assertEquals(InstanceManager.EXPERT_SPAWN, InstanceManager.spawnFor(PlayerRole.MANUAL_EXPERT));
        assertEquals(InstanceManager.HELPER_SPAWN, InstanceManager.spawnFor(PlayerRole.DEFUSAL_HELPER));
    }

    @Test
    void eachCameraUsesItsOwnSpawnAndBodyStaysFixedAsTheCursorMoves() {
        for (PlayerRole role : PlayerRole.values()) {
            Player player = player(role);
            Pos spawn = InstanceManager.spawnFor(role);
            Pos home = CameraAnchor.homePosition(player);
            assertEquals(spawn.y() + CameraAnchor.CAMERA_HEIGHT, home.y(), 0.00001);
            assertEquals(0, Math.hypot(home.x() - spawn.x(), home.z() - spawn.z()), 0.00001);
            assertEquals(spawn.yaw(), CameraAnchor.viewYaw(player), 0.00001);
            var body = player.getVehicle();
            assertNotNull(body);
            Pos bodyPosition = body.getPosition();
            assertEquals(spawn.x(), bodyPosition.x(), 0.00001);
            assertEquals(spawn.z(), bodyPosition.z(), 0.00001);
            assertEquals(spawn.yaw(), bodyPosition.yaw(), 0.00001);
            ClientView.selectCursor(player, new ClientView.CursorPosition(30, 15));
            for (int tick = 0; tick < 30; tick++) CameraAnchor.update(player);
            assertEquals(0, ClientView.wrapDegrees(CameraAnchor.viewYaw(player) - spawn.yaw() - 30), 0.001);
            assertEquals(spawn.pitch() + 15, CameraAnchor.viewPitch(player), 0.001);
            assertEquals(bodyPosition, body.getPosition());
            BombView.detach(player);
            CameraAnchor.detach(player);
        }
    }

    @Test
    void onlyDefuserHasSurfaceItemAndCanLeanWithoutMovingTheBody() {
        for (PlayerRole role : PlayerRole.values()) {
            Player player = player(role);
            Pos bodyPosition = player.getVehicle().getPosition();
            int entities = player.getInstance().getEntities().size();
            // Player + stationary body mount + camera, plus a surface item only for the defuser.
            assertEquals(role == PlayerRole.BOMB_DEFUSER ? 4 : 3, entities);
            BombView.enter(player);
            assertEquals(role == PlayerRole.BOMB_DEFUSER, BombView.isActive(player));
            for (int tick = 0; tick < 30; tick++) CameraAnchor.update(player);
            assertEquals(bodyPosition, player.getVehicle().getPosition());
            BombView.exit(player);
            for (int tick = 0; tick < 30; tick++) CameraAnchor.update(player);
            assertEquals(InstanceManager.spawnFor(role).yaw(), CameraAnchor.viewYaw(player), 0.001);
            assertEquals(bodyPosition, player.getVehicle().getPosition());
            assertEquals(entities, player.getInstance().getEntities().size());
            BombView.detach(player);
            CameraAnchor.detach(player);
        }
    }
}
