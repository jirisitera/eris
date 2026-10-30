package com.japicraft.game;

import net.minestom.server.color.Color;
import net.minestom.server.entity.EntityType;

public record EnemyType(
    String id,
    String displayName,
    int baseMoveIntervalTicks,
    int gazeToleranceTicks,
    int doorPatienceTicks,
    int retreatSteps,
    float cameraAvoidanceWeight,
    Color radarColor,
    EntityType entityType,
    int transitTicks
) {
    public static final EnemyType STALKER = new EnemyType(
        "stalker",
        "The Stalker",
        90,                     // moves every 4.5s
        25,                     // 1.25s camera gaze freezes/wards
        100,                    // 5.0s door patience before breach
        2,                      // retreats 2 nodes when warded
        2.0F,                   // strongly avoids heavily watched cameras
        new Color(255, 60, 60), // Red radar dot
        EntityType.ENDERMAN,
        20                      // 1.0s vanish transit duration
    );
    public static final EnemyType LURKER = new EnemyType(
        "lurker",
        "The Lurker",
        130,                    // slower approach (6.5s)
        40,                     // more resilient to camera gaze (2s)
        80,                     // faster door breach once it arrives (4.0s)
        3,                      // larger retreat when warded
        1.0F,                   // moderate camera avoidance
        new Color(255, 160, 40),// Orange radar dot
        EntityType.ENDERMAN,
        30                      // 1.5s vanish transit duration (heavy stalker)
    );
    public static final EnemyType SHADE = new EnemyType(
        "shade",
        "The Shade",
        75,                     // fast mover (3.75s)
        15,                     // very fragile to camera gaze (0.75s)
        110,                    // 5.5s door patience
        2,
        3.5F,                   // actively hunts for unwatched blind-spot routes
        new Color(180, 90, 255),// Purple radar dot
        EntityType.ENDERMAN,
        10                      // 0.5s quick ghostly vanish transit
    );

    public EnemyType(
        String id,
        String displayName,
        int baseMoveIntervalTicks,
        int gazeToleranceTicks,
        int doorPatienceTicks,
        int retreatSteps,
        float cameraAvoidanceWeight,
        Color radarColor,
        EntityType entityType
    ) {
        this(id, displayName, baseMoveIntervalTicks, gazeToleranceTicks, doorPatienceTicks, retreatSteps, cameraAvoidanceWeight, radarColor, entityType, 20);
    }
}
