#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <eris:monitor_data.glsl>
// @formatter:on

uniform sampler2D InSampler;

layout(std140) uniform BloomConfig {
    vec2 Step;
    float Bright;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const ivec3 ACTIVE_MARKER = ivec3(91, 126, 209);

bool monitorActive(int rowY) {
    return ivec3(round(texelFetch(InSampler, ivec2(40, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER &&
    ivec3(round(texelFetch(InSampler, ivec2(41, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER;
}

// Lobby screen marker (see MapProbe.lobbyDots / cursor.fsh)
bool lobbyActive(int rowY) {
    return ivec3(round(texelFetch(InSampler, ivec2(8, rowY), 0).rgb * 255.0)) == ivec3(0) &&
    ivec3(round(texelFetch(InSampler, ivec2(9, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER;
}

// Glow emitted by a monitor screen pixel (cheap approximation of what cursor.fsh renders)
vec3 monitorGlow(int monitorIndex, vec2 monitorUv) {
    int rowY = 19 - monitorIndex;
    if (!monitorActive(rowY)) {
        return vec3(0.0);
    }
    vec2 curved;
    vec2 centered;
    if (!curveMonitorUv(monitorUv, curved, centered)) {
        return vec3(0.0);
    }
    // TV static of the lobby screen gives off a soft grey glow
    if (lobbyActive(rowY)) {
        return vec3(0.22);
    }
    // In-game screens (clock, energy, camera buttons) don't glow
    return vec3(0.0);
}

// Sample bloom input: extracts bright highlights in the first pass (Bright >= 0.5),
// or samples the intermediate blurred texture in the second pass (Bright < 0.5).
vec3 sampleBloomHighlight(vec2 uv) {
    if (Bright < 0.5) {
        return texture(InSampler, uv).rgb;
    }

    ivec2 pixelCoord = ivec2(uv * vec2(textureSize(InSampler, 0)));

    // Exclude UI data rows (reserved for CPU-GPU metadata communication) from bloom
    if (pixelCoord.y >= 14 && pixelCoord.y <= 21 && pixelCoord.x < 48) {
        return vec3(0.0);
    }

    vec3 color = texelFetch(InSampler, pixelCoord, 0).rgb;
    ivec3 rgb255 = ivec3(round(color * 255.0));
    float maxChannel = max(color.r, max(color.g, color.b));
    float minChannel = min(color.r, min(color.g, color.b));
    bool isWhiteSurface = (maxChannel - minChannel) < 0.2 * maxChannel;

    // Monitor screens (red channel 250..252)
    if (rgb255.r >= 250 && rgb255.r <= 252 && !isWhiteSurface) {
        vec2 monitorUv;
        int monitorIndex = decodeMonitor(InSampler, pixelCoord, monitorUv);
        return monitorIndex >= 0 ? monitorGlow(monitorIndex, monitorUv) : vec3(0.0);
    }

    // Check emissive parity marker: R odd, G even, B odd
    bool isEmissive =
    (rgb255.r & 1) == 1 &&
    (rgb255.g & 1) == 0 &&
    (rgb255.b & 1) == 1 &&
    maxChannel > 0.2;

    if (isEmissive) {
        return color;
    }

    if (isWhiteSurface) {
        float bloomWeight = smoothstep(0.55, 0.85, maxChannel);
        return color * bloomWeight;
    }

    return vec3(0.0);
}

void main() {
    vec2 texelStep = Step / vec2(textureSize(InSampler, 0));
    vec3 blurredColor = sampleBloomHighlight(texCoord) * 0.0798;
    float totalWeight = 0.0798;

    for (int tap = 1; tap <= 12; tap++) {
        float weight = exp(-float(tap * tap) / 32.0);
        vec2 offset = texelStep * float(tap);
        blurredColor += (sampleBloomHighlight(texCoord + offset) + sampleBloomHighlight(texCoord - offset)) * weight;
        totalWeight += 2.0 * weight;
    }

    fragColor = vec4(blurredColor / totalWeight * 0.45, 1.0);
}
