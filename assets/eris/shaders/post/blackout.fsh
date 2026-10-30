#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

// @formatter:off
#include <minecraft:globals.glsl>
// @formatter:on

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// 12-sample isotropic disc sampling kernel (two concentric rings of 6 taps)
const vec2 TAPS[12] = vec2[](
    vec2(0.0000, 0.4500),
    vec2(0.3897, 0.2250),
    vec2(0.3897, -0.2250),
    vec2(0.0000, -0.4500),
    vec2(-0.3897, -0.2250),
    vec2(-0.3897, 0.2250),
    vec2(0.0000, 0.9000),
    vec2(0.7794, 0.4500),
    vec2(0.7794, -0.4500),
    vec2(0.0000, -0.9000),
    vec2(-0.7794, -0.4500),
    vec2(-0.7794, 0.4500)
);

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 resolution = vec2(textureSize(InSampler, 0));
    vec2 texelSize = 1.0 / resolution;
    float time = GameTime * 1200.0;

    // Aspect-ratio-corrected distance from screen center
    vec2 centered = (texCoord - 0.5) * vec2(resolution.x / resolution.y, 1.0);
    float dist = length(centered);

    // Dynamic blur radius in pixel units:
    // Mild soft-focus in the center (pupil dilation), increasing toward the periphery
    float blurRadius = 2.8 + dist * 2.4;

    // 13-tap blur (center sample + 12 kernel samples)
    vec4 centerSample = texture(InSampler, texCoord);
    vec3 blurredColor = centerSample.rgb * 0.35;
    float totalWeight = 0.35;

    for (int i = 0; i < 12; i++) {
        vec2 offset = TAPS[i] * blurRadius * texelSize;
        float weight = 1.0 - length(TAPS[i]) * 0.35;
        blurredColor += texture(InSampler, clamp(texCoord + offset, 0.0, 1.0)).rgb * weight;
        totalWeight += weight;
    }
    vec3 color = blurredColor / totalWeight;

    // 1. Exposure and black-level crush (lights out)
    // Identify bright emissive sources (eyes, emergency LEDs, power lights)
    float peak = max(color.r, max(color.g, color.b));
    float emissive = smoothstep(0.40, 0.90, peak);

    // Deep shadow crush: diffuse room lighting falls off completely into darkness
    vec3 crushed = max(color - 0.035, vec3(0.0));
    crushed = pow(crushed, vec3(1.35)) * 0.12;

    // Emissive elements retain enough luminance to pierce dimly through the gloom
    color = crushed + (color * 0.40) * (emissive * emissive);

    // 2. Scotopic Vision (Night vision rod sensitivity: desaturation + cold blue-slate tint)
    float lum = dot(color, vec3(0.2126, 0.7152, 0.0722));
    vec3 scotopic = vec3(lum) * vec3(0.72, 0.85, 1.12);
    // Retain a tiny amount (~15%) of original color so emergency red lights still read red
    color = mix(scotopic, color, 0.15);

    // 3. Deep tunnel-vision vignette
    float vignette = 1.0 - smoothstep(0.22, 0.85, dist);
    vignette = pow(vignette, 1.6);
    // Allow strong emissives to partially pierce the vignette
    float edgeDarkening = mix(0.04 + 0.96 * vignette, 1.0, emissive * 0.35);
    color *= edgeDarkening;

    // 4. Eigengrau (visual snow / retinal dark noise in the dark)
    float grain = (hash(gl_FragCoord.xy + time * 17.0) - 0.5) * 0.022;
    vec3 eigengrau = vec3(0.008, 0.010, 0.014); // subtle intrinsic dark floor
    color = max(color + vec3(grain), eigengrau);

    // 5. Subtle ambient breathing pulse in the dark (very slow 0.1 Hz)
    float breath = 0.95 + 0.05 * sin(time * 0.08);
    color *= breath;

    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
