package com.japicraft.route;

import net.minestom.server.coordinate.Pos;

/**
 * Represents a tactical waypoint in the facility graph.
 *
 * @param id             Unique identifier for debugging and telemetry.
 * @param position       Physical 3D coordinate where the enemy stands.
 * @param cameraZone     Camera index (1..4) that directly oversees this node, or -1 for unmonitored blind spots.
 * @param isDoorApproach True if this node is directly outside an office door.
 * @param isLeftFlank    True for the left (west) corridor, false for right (east).
 * @param mapX           Normalized X position (0.0..1.0) on the security monitor map.
 * @param mapY           Normalized Y position (0.0..1.0) on the security monitor map.
 */
public record PathNode(
    String id,
    Pos position,
    int cameraZone,
    boolean isDoorApproach,
    boolean isLeftFlank,
    float mapX,
    float mapY
) {
    public PathNode(String id, Pos position, int cameraZone, boolean isDoorApproach, boolean isLeftFlank) {
        this(
            id,
            position,
            cameraZone,
            isDoorApproach,
            isLeftFlank,
            (float) Math.clamp(position.x() / 17.0, 0.05, 0.95),
            (float) Math.clamp(position.z() / 28.0, 0.05, 0.95)
        );
    }
}
