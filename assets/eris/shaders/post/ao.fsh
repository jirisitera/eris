#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// SSAO configuration
const float NEAR_DEPTH_SCALE = 0.05;
const float TAN_HALF_FOV = 0.7;
const float AO_RADIUS = 0.35;
const int SAMPLE_COUNT = 8;
const float GOLDEN_ANGLE = 2.3999632;
const float TWO_PI = 6.2831853;

vec2 screenSize;

// Reconstruct view-space position from depth buffer
vec3 reconstructViewPos(ivec2 pixelCoord) {
    float rawDepth = texelFetch(DepthSampler, pixelCoord, 0).r;
    float linearDepth = NEAR_DEPTH_SCALE / max(rawDepth, 1e-6);
    vec2 ndc = (vec2(pixelCoord) + 0.5) / screenSize * 2.0 - 1.0;
    return vec3(ndc * vec2(screenSize.x / screenSize.y, 1.0) * TAN_HALF_FOV * linearDepth, -linearDepth);
}

// Pseudo-random hash for sampling jitter
float hash(vec2 coord) {
    return fract(sin(dot(coord, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    screenSize = vec2(textureSize(DepthSampler, 0));
    ivec2 pixelCoord = ivec2(gl_FragCoord.xy);

    float rawDepth = texelFetch(DepthSampler, pixelCoord, 0).r;
    if (rawDepth <= 0.0) {
        fragColor = vec4(1.0);
        return;
    }

    vec3 centerPos = reconstructViewPos(pixelCoord);

    // Reconstruct view-space normal using the best-depth neighbor to avoid edge bleeding
    vec3 posLeft = reconstructViewPos(pixelCoord - ivec2(1, 0));
    vec3 posRight = reconstructViewPos(pixelCoord + ivec2(1, 0));
    vec3 posDown = reconstructViewPos(pixelCoord - ivec2(0, 1));
    vec3 posUp = reconstructViewPos(pixelCoord + ivec2(0, 1));

    vec3 dX = abs(posRight.z - centerPos.z) < abs(centerPos.z - posLeft.z) ? posRight - centerPos : centerPos - posLeft;
    vec3 dY = abs(posUp.z - centerPos.z) < abs(centerPos.z - posDown.z) ? posUp - centerPos : centerPos - posDown;
    vec3 normal = normalize(cross(dX, dY));

    // Project sampling radius into screen pixel coordinates
    float focalLength = screenSize.y * 0.5 / TAN_HALF_FOV;
    float radiusPixels = AO_RADIUS * focalLength / -centerPos.z;
    float randomAngle = hash(vec2(pixelCoord)) * TWO_PI;

    float totalOcclusion = 0.0;
    for (int i = 0; i < SAMPLE_COUNT; i++) {
        float alpha = (float(i) + 0.5) / float(SAMPLE_COUNT);
        float angle = float(i) * GOLDEN_ANGLE + randomAngle;

        // Vogel disk spiral sampling
        vec2 sampleOffset = vec2(cos(angle), sin(angle)) * sqrt(alpha) * radiusPixels;
        ivec2 sampleCoord = clamp(pixelCoord + ivec2(sampleOffset), ivec2(0), ivec2(screenSize) - 1);

        vec3 samplePos = reconstructViewPos(sampleCoord);
        vec3 toSample = samplePos - centerPos;
        float dist = length(toSample);

        // Horizon / angle occlusion test with distance attenuation
        float angleOcclusion = max(dot(normal, toSample / max(dist, 1e-4)) - 0.1, 0.0);
        float distanceFalloff = smoothstep(AO_RADIUS * 2.5, AO_RADIUS * 0.5, dist);

        totalOcclusion += angleOcclusion * distanceFalloff;
    }

    float ao = 1.0 - (totalOcclusion / float(SAMPLE_COUNT)) * 1.6;
    fragColor = vec4(vec3(clamp(ao, 0.0, 1.0)), 1.0);
}
