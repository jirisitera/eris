#ifndef ERIS_PBR_SCENE_GLSL
#define ERIS_PBR_SCENE_GLSL

// Camera room positions / world offsets
#define CAMERA_OFFSET_NORTH vec3(0.5, 2.8, 2.0)
#define CAMERA_OFFSET_SOUTH vec3(0.5, 2.8, 5.0)

// Primary spotlights
#define SPOTLIGHT_COUNT 4

const vec3 SPOTLIGHT_POSITIONS[SPOTLIGHT_COUNT] = vec3[](
    vec3(0.5, 4.7, 3.5),
    vec3(0.5, 9.0, -2.0),
    vec3(0.5, 9.0, 9.0),
    vec3(0.5, 8.0, 3.5)
);

const vec3 SPOTLIGHT_DIRECTIONS[SPOTLIGHT_COUNT] = vec3[](
    vec3(0.0, -1.0, 0.0),
    vec3(-0.0371, -0.9883, -0.1482),
    vec3(0.0494, -0.9877, 0.1482),
    vec3(-0.6988, -0.6851, 0.2055)
);

const vec3 SPOTLIGHT_COLORS[SPOTLIGHT_COUNT] = vec3[](
    vec3(26.0, 23.0, 19.0),
    vec3(20.0, 17.0, 13.0),
    vec3(20.0, 17.0, 13.0),
    vec3(8.0, 9.0, 12.0)
);

// Inner and outer cone cutoff cosines (cosOuter, cosInner)
const vec2 SPOTLIGHT_CUTOFFS[SPOTLIGHT_COUNT] = vec2[](
    vec2(0.89101, 0.96126),
    vec2(0.97815, 0.99452),
    vec2(0.97815, 0.99452),
    vec2(0.98163, 0.99619)
);

// Secondary / auxiliary scene lights
#define AUX_LIGHT_COUNT 4
#define AUX_LIGHT_RADIUS 0.7

const vec3 AUX_LIGHT_POSITIONS[AUX_LIGHT_COUNT] = vec3[](
    vec3(1.2674, 2.5679, 6.4242),
    vec3(-1.3084, 1.9656, 6.7495),
    vec3(-0.2674, 2.5679, 0.5758),
    vec3(2.3084, 1.9656, 0.2505)
);

const vec3 AUX_LIGHT_DIRECTIONS[AUX_LIGHT_COUNT] = vec3[](
    vec3(-0.3746, 0.0, -0.9272),
    vec3(0.3746, 0.0, -0.9272),
    vec3(0.3746, 0.0, 0.9272),
    vec3(-0.3746, 0.0, 0.9272)
);

const float AUX_LIGHT_INTENSITIES[AUX_LIGHT_COUNT] = float[](5.29, 0.98, 5.29, 0.98);

const vec2 AUX_LIGHT_CUTOFFS[AUX_LIGHT_COUNT] = vec2[](
    vec2(0.9562, 0.7083),
    vec2(0.4311, 0.4311),
    vec2(0.9562, 0.7083),
    vec2(0.4311, 0.4311)
);

// Ambient light probe locations
#define AMBIENT_PROBE_COUNT 8
#define AMBIENT_PROBE_RADIUS 1.6

const vec3 AMBIENT_PROBE_POSITIONS[AMBIENT_PROBE_COUNT] = vec3[](
    vec3(-3.9, 3.2, -2.5),
    vec3(4.9, 3.6, 0.5),
    vec3(-3.9, 3.0, 9.5),
    vec3(4.9, 3.4, 6.5),
    vec3(0.5, 5.5, -4.6),
    vec3(0.5, 5.5, 11.6),
    vec3(-3.8, 4.2, 3.5),
    vec3(4.8, 2.6, 10.8)
);

#endif
