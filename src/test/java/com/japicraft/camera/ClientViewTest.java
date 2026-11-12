package com.japicraft.camera;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.network.player.GameProfile;
import net.minestom.server.network.player.PlayerConnection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ClientViewTest {
    private RecordingConnection connection;
    private Player player;

    @BeforeAll
    static void initializeServer() {
        MinecraftServer.init();
    }

    @BeforeEach
    void createPlayer() {
        connection = new RecordingConnection();
        player = new Player(connection, new GameProfile(UUID.randomUUID(), "CursorTest"));
        ClientView.reset(player);
        rotate(0, 0); // Echo the initial correction, as the vanilla client does.
        connection.packets.clear();
    }

    @Test
    void modeSwitchAlignsPhysicalPitchWithSavedCursor() {
        rotate(0, 65);
        ClientView.selectCursor(player, new ClientView.CursorPosition(0, -20));
        assertEquals(-20, lastCorrection().pitch());
        rotate(0, -20);
        rotate(0, 90);
        assertEquals(90, ClientView.mousePitch(player));
        assertEquals(1, CursorProbe.screenPosition(player).y());
    }

    @Test
    void edgeOverscrollCannotStrandCursorOnReturnJourney() {
        // Reproduce a physical/virtual offset left by overscrolling a smaller canvas.
        player.setTag(net.minestom.server.tag.Tag.<Float>Transient("clientMousePitch"), -35F);
        rotate(0, 80);
        assertEquals(45, ClientView.mousePitch(player));
        assertEquals(45, lastCorrection().pitch());
        rotate(lastCorrection().yaw(), 45);
        rotate(0, 90);
        assertEquals(1, CursorProbe.screenPosition(player).y());
        rotate(0, -90);
        assertEquals(0, CursorProbe.screenPosition(player).y());
    }

    @Test
    void freshBombCursorCanReachBottomAfterLookingDownInFullscreen() {
        rotate(0, 90); // Native pitch is already at its downward limit before entering.
        ClientView.selectCursor(player, new ClientView.CursorPosition(0, 0));
        assertEquals(0, lastCorrection().pitch());
        assertEquals(0.5F, BombView.surfacePosition(player).y());
        rotate(0, 0); // Correction echo is not mouse movement.
        rotate(0, BombView.CURSOR_RANGE);
        assertEquals(1, BombView.surfacePosition(player).y());
        rotate(0, -BombView.CURSOR_RANGE);
        assertEquals(0, BombView.surfacePosition(player).y());
    }

    @Test
    void oldPacketsAndCorrectionEchoDoNotMoveRestoredCursor() {
        rotate(20, 30);
        ClientView.restoreCursor(player, new ClientView.CursorPosition(-10, -15));
        rotate(25, 35); // Input already in flight before the correction.
        assertEquals(new ClientView.CursorPosition(-10, -15), ClientView.cursorPosition(player));
        rotate(-10, -15);
        rotate(-8, -12);
        assertEquals(new ClientView.CursorPosition(-8, -12), ClientView.cursorPosition(player));
    }

    @Test
    void newerModeSwitchSupersedesOlderCorrection() {
        ClientView.selectCursor(player, new ClientView.CursorPosition(15, 25));
        ClientView.selectCursor(player, new ClientView.CursorPosition(-20, -30));
        rotate(15, 25);
        assertEquals(new ClientView.CursorPosition(-20, -30), ClientView.cursorPosition(player));
        rotate(-20, -30);
        rotate(-19, -29);
        assertEquals(new ClientView.CursorPosition(-19, -29), ClientView.cursorPosition(player));
    }

    @Test
    void packetTypeChangesAfterWholeTurnsDoNotJumpCursor() {
        // Preserve the client's continuous turn history before Pos normalizes it.
        for (int yaw = 60; yaw <= 720; yaw += 60) rotate(yaw, 0);
        assertTrue(ClientView.record(player, new ClientPlayerPositionAndRotationPacket(
            new Pos(0, 0, 0, 725, 0), false, false)));
        rotate(726, 0);
        assertEquals(90, ClientView.mouseYaw(player));
        assertTrue(ClientView.record(player, new ClientPlayerPositionAndRotationPacket(
            new Pos(0, 0, 0, 727, 0), false, false)));
        rotate(728, 0);
        assertEquals(90, ClientView.mouseYaw(player));
        rotate(723, 0);
        assertEquals(85, ClientView.mouseYaw(player));
    }

    @Test
    void fastRepeatedOverscrollStaysAtTheCorrectHorizontalEdge() {
        for (int sign : new int[]{1, -1}) {
            ClientView.reset(player);
            rotate(0, 0);
            for (int turn = 1; turn <= 40; turn++) {
                // More than half a turn per packet must retain its direction.
                rotate(sign * turn * 240, 0);
                assertEquals(sign * 90, ClientView.mouseYaw(player));
            }
            rotate(sign * (40 * 240 - 5), 0);
            assertEquals(sign * 85, ClientView.mouseYaw(player));
        }
    }

    @Test
    void largeRawYawReversalMovesToTheOppositeEdge() {
        rotate(350, 0);
        assertEquals(90, ClientView.mouseYaw(player));
        rotate(-50, 0);
        assertEquals(-90, ClientView.mouseYaw(player));
    }

    @Test
    void correctionFlagChangesProbeEvenWhenEncodedAnglesStayTheSame() {
        CursorProbe.update(player);
        var normal = player.getEquipment(net.minestom.server.entity.EquipmentSlot.OFF_HAND);
        ClientView.restoreCursor(player, new ClientView.CursorPosition(0, 0));
        assertTrue(ClientView.isCorrectionPending(player));
        var pending = player.getEquipment(net.minestom.server.entity.EquipmentSlot.OFF_HAND);
        assertNotEquals(normal, pending);
        rotate(10, 15); // Ignore old input until the exact correction reply arrives.
        assertTrue(ClientView.isCorrectionPending(player));
        rotate(0, 0);
        assertFalse(ClientView.isCorrectionPending(player));
        CursorProbe.update(player);
        assertEquals(normal, player.getEquipment(net.minestom.server.entity.EquipmentSlot.OFF_HAND));
    }

    @Test
    void resetRealignsClientAndAllowsFullVerticalRange() {
        rotate(0, 60);
        ClientView.reset(player);
        assertEquals(0, lastCorrection().pitch());
        rotate(0, 0);
        rotate(0, -90);
        assertEquals(0, CursorProbe.screenPosition(player).y());
        rotate(0, 90);
        assertEquals(1, CursorProbe.screenPosition(player).y());
    }

    @Test
    void invalidRotationCannotPoisonCursorState() {
        rotate(10, 20);
        assertFalse(ClientView.record(player, new ClientPlayerRotationPacket(Float.NaN, 0, false, false)));
        assertFalse(ClientView.record(player, new ClientPlayerRotationPacket(0, Float.POSITIVE_INFINITY, false, false)));
        rotate(11, 21);
        assertEquals(new ClientView.CursorPosition(11, 21), ClientView.cursorPosition(player));
    }

    private void rotate(float yaw, float pitch) {
        assertTrue(ClientView.record(player, new ClientPlayerRotationPacket(yaw, pitch, false, false)));
    }

    private PlayerRotationPacket lastCorrection() {
        return connection.packets.stream().filter(PlayerRotationPacket.class::isInstance)
            .map(PlayerRotationPacket.class::cast).reduce((first, second) -> second).orElseThrow();
    }

    private static final class RecordingConnection extends PlayerConnection {
        final List<SendablePacket> packets = new ArrayList<>();

        @Override
        public void sendPacket(SendablePacket packet) {
            packets.add(packet);
        }

        @Override
        public SocketAddress getRemoteAddress() {
            return new InetSocketAddress("127.0.0.1", 0);
        }
    }
}
