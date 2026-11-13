#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <eris:post/timer.glsl>
// @formatter:on

uniform sampler2D InSampler;

layout(std140) uniform BloomConfig {
    vec2 Step;
    float Bright;
};

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// sample bloom input: extracts bright highlights in the first pass (Bright >= 0.5),
// or samples the intermediate blurred texture in the second pass (Bright < 0.5)
vec3 sampleBloomHighlight(vec2 uv) {
    if (Bright < 0.5) {
        return texture(InSampler, uv).rgb;
    }

    ivec2 imageSize = textureSize(InSampler, 0);
    ivec2 pixelCoord = clamp(ivec2(uv * vec2(imageSize)), ivec2(0), imageSize - 1);

    // exclude UI data rows (reserved for CPU-GPU metadata communication) from bloom
    if (pixelCoord.y >= 14 && pixelCoord.y <= 21 && pixelCoord.x < 48) {
        return vec3(0.0);
    }

    vec3 color = texelFetch(InSampler, pixelCoord, 0).rgb;
    ivec3 rgb255 = ivec3(round(color * 255.0));
    float maxChannel = max(color.r, max(color.g, color.b));
    float minChannel = min(color.r, min(color.g, color.b));

    // case timer screen LED glow (red channel 250)
    if (rgb255.r == 250) {
        vec2 timerUv;
        int timerMinutes;
        int timerSeconds;
        int timerStrikes;
        if (!decodeTimer(InSampler, pixelCoord, timerUv, timerMinutes, timerSeconds, timerStrikes)) {
            return vec3(0.0);
        }
        vec2 centered = (timerUv - 0.5) * vec2(2.0, 1.0);
        float glow = exp(-dot(centered * vec2(1.1, 2.2), centered * vec2(1.1, 2.2)) * 2.5);
        return vec3(0.55, 0.40, 0.08) * glow;
    }

    // smooth thresholds avoid flashes when ordinary shaded colors cross RGB parity
    // or the neutral-color boundary. Timer emission is handled explicitly above
    float neutrality = 1.0 - smoothstep(0.15, 0.25,
        (maxChannel - minChannel) / max(maxChannel, 0.0001));
    float bloomWeight = smoothstep(0.55, 0.85, maxChannel) * neutrality;
    return color * bloomWeight;
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
