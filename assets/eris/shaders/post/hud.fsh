#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:globals.glsl>
#include <eris:ui_data.glsl>

uniform sampler2D InSampler;
uniform sampler2D BloomSampler;
uniform sampler2D AoSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float EXPOSURE = 1.0;
const float CONTRAST = 1.05;
const float SATURATION = 0.9;
const float PIXEL_SCALE = 3.0;
const float COLOR_LEVELS = 16.0;
const float DITHER_STRENGTH = 0.35;
const float CHROMATIC_ABERRATION = 0.07;
const float VIGNETTE_INTENSITY = 1.0;
const float NOISE_INTENSITY = 0.2;
const vec3 COLOR_TINT = vec3(0.95, 1.03, 0.9);
const float TINT_STRENGTH = 0.15;

float bayerDither4x4(vec2 coord) {
    const int bayerMatrix[16] = int[](
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    );
    ivec2 cell = ivec2(mod(coord, 4.0));
    return (float(bayerMatrix[cell.y * 4 + cell.x]) + 0.5) / 16.0;
}

vec3 sampleScene(vec2 uv) {
    uv = withoutUiData(InSampler, uv);
    return texture(InSampler, uv).rgb;
}

float sampleBlurredAo(vec2 uv) {
    vec2 texelSize = 1.0 / vec2(textureSize(AoSampler, 0));
    float totalAo = 0.0;
    for (int dy = -1; dy <= 1; dy++) {
        for (int dx = -1; dx <= 1; dx++) {
            totalAo += texture(AoSampler, uv + vec2(dx, dy) * texelSize * 1.5).r;
        }
    }
    return totalAo / 9.0;
}

vec3 adjustSaturation(vec3 color, float saturation) {
    float luminance = dot(color, vec3(0.2126, 0.7152, 0.0722));
    return mix(vec3(luminance), color, saturation);
}

void main() {
    ivec2 dataPixel = ivec2(gl_FragCoord.xy);
    if (isUiDataPixel(dataPixel)) {
        fragColor = texelFetch(InSampler, dataPixel, 0);
        return;
    }
    vec2 screenSize = vec2(textureSize(InSampler, 0));
    vec2 texelSize = 1.0 / screenSize;
    float time = GameTime * 1200.0;

    vec2 pixelSize = texelSize * PIXEL_SCALE;
    vec2 pixelUv = floor(texCoord / pixelSize) * pixelSize + pixelSize * 0.5;
    vec2 centeredUv = pixelUv - 0.5;
    float distFromCenter = length(centeredUv);
    float distortion = CHROMATIC_ABERRATION * distFromCenter * distFromCenter;

    vec3 sceneColor;
    // Chromatic aberration + AO + Bloom
    sceneColor = vec3(
        sampleScene(pixelUv + centeredUv * distortion).r,
        sampleScene(pixelUv).g,
        sampleScene(pixelUv - centeredUv * distortion).b
    ) * sampleBlurredAo(pixelUv) + texture(BloomSampler, pixelUv).rgb;

    // Post-processing color adjustments
    sceneColor *= EXPOSURE;
    sceneColor = mix(sceneColor, sceneColor * COLOR_TINT, TINT_STRENGTH);

    float dither = bayerDither4x4(floor(pixelUv / pixelSize));
    sceneColor = floor(sceneColor * COLOR_LEVELS + dither * DITHER_STRENGTH) / (COLOR_LEVELS - 1.0);
    sceneColor = (sceneColor - 0.5) * CONTRAST + 0.5;
    sceneColor = clamp(adjustSaturation(sceneColor, SATURATION), 0.0, 1.0);
    sceneColor *= clamp(1.0 - smoothstep(0.5, 0.9, distFromCenter) * VIGNETTE_INTENSITY, 0.0, 1.0);

    float noise = fract(
        sin(dot(floor(pixelUv / pixelSize), vec2(12.9898, 78.233)) + time * 37.0) * 43758.5453
    );
    sceneColor += (noise - 0.5) * 0.08 * NOISE_INTENSITY;

    fragColor = vec4(clamp(sceneColor, 0.0, 1.0), 1.0);
}
