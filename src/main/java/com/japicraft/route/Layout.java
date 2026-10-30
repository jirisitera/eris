package com.japicraft.route;

import net.minestom.server.coordinate.Pos;

/**
 * Facility waypoint graph matching the physical office and camera layouts.
 * To add new rooms or paths, register a {@link PathNode} with {@code addNode} and wire it with {@code connect}.
 */
public final class Layout {
    public static final Route FACILITY_GRAPH = new Route()
        // --- Deep Spawn Nodes ---
        .addNode(new PathNode("storage_deep_left", new Pos(3.5, 11.0, 24.0, 180.0F, 0.0F), -1, false, true))
        .addNode(new PathNode("generator_deep_right", new Pos(13.5, 11.0, 24.0, 180.0F, 0.0F), -1, false, false))

        // --- South Hallways & Cross-Connector ---
        .addNode(new PathNode("hallway_south_left", new Pos(3.5, 11.0, 18.0, 180.0F, 0.0F), 3, false, true))
        .addNode(new PathNode("hallway_south_center", new Pos(8.5, 11.0, 18.0, 180.0F, 0.0F), -1, false, true))
        .addNode(new PathNode("hallway_south_right", new Pos(13.5, 11.0, 18.0, 180.0F, 0.0F), 4, false, false))

        // --- Mid-Facility Camera Junctions & Cross-Hallway ---
        .addNode(new PathNode("cam3_junction_left", new Pos(2.5, 11.0, 12.0, 180.0F, 0.0F), 3, false, true))
        .addNode(new PathNode("mid_crossover", new Pos(8.5, 11.0, 12.0, 180.0F, 0.0F), -1, false, false))
        .addNode(new PathNode("cam4_junction_right", new Pos(14.5, 11.0, 12.0, 180.0F, 0.0F), 4, false, false))

        // --- North Corridors (Near Office) ---
        .addNode(new PathNode("cam1_corridor_left", new Pos(3.5, 11.0, 7.0, 180.0F, 0.0F), 1, false, true))
        .addNode(new PathNode("cam2_corridor_right", new Pos(13.5, 11.0, 7.0, 180.0F, 0.0F), 2, false, false))

        // --- Office Door Thresholds ---
        .addNode(new PathNode("door_approach_left", new Pos(4.0, 11.0, 2.0, 90.0F, 0.0F), -1, true, true))
        .addNode(new PathNode("door_approach_right", new Pos(12.0, 11.0, 2.0, -90.0F, 0.0F), -1, true, false))

        // --- Office Interior (Breach) ---
        .addNode(new PathNode("office_breach_left", new Pos(7.0, 11.0, 3.5, 90.0F, 0.0F), 0, false, true))
        .addNode(new PathNode("office_breach_right", new Pos(10.0, 11.0, 3.5, -90.0F, 0.0F), 0, false, false))

        // --- Graph Connections (supports branching between Left and Right flanks!) ---
        .connect("storage_deep_left", "hallway_south_left", "hallway_south_center")
        .connect("generator_deep_right", "hallway_south_right", "hallway_south_center")

        .connect("hallway_south_left", "cam3_junction_left", "mid_crossover")
        .connect("hallway_south_center", "cam3_junction_left", "cam4_junction_right", "mid_crossover")
        .connect("hallway_south_right", "cam4_junction_right", "mid_crossover")

        .connect("cam3_junction_left", "cam1_corridor_left", "mid_crossover")
        .connect("mid_crossover", "cam1_corridor_left", "cam2_corridor_right")
        .connect("cam4_junction_right", "cam2_corridor_right", "mid_crossover")

        .connect("cam1_corridor_left", "door_approach_left")
        .connect("cam2_corridor_right", "door_approach_right")

        .connect("door_approach_left", "office_breach_left")
        .connect("door_approach_right", "office_breach_right");
}
