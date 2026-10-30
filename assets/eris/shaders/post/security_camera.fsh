#version 330
#extension GL_ARB_separate_shader_objects : require

uniform sampler2D InSampler;

// @formatter:off
#include <minecraft:globals.glsl>
// @formatter:on

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

float noise(vec2 position) {
    return fract(sin(dot(position, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
    vec2 centered = texCoord - 0.5;
    float radius = dot(centered, centered);
    vec2 warped = centered * (1.0 + radius * 0.1) + 0.5;
    vec3 color = texture(InSampler, clamp(warped, 0.0, 1.0)).rgb;

    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    luminance = clamp((luminance - 0.5) * 1.25 + 0.5, 0.0, 1.0);
    vec3 monochrome = vec3(luminance);

    vec2 resolution = vec2(textureSize(InSampler, 0));
    float scanline = 0.78 + 0.22 * sin(warped.y * resolution.y * 3.14159265);
    float rollingBand = 0.94 + 0.06 * sin(GameTime * 900.0 + warped.y * 20.0);
    float vignette = 1.0 - smoothstep(0.28, 0.78, radius);
    float grain = (noise(gl_FragCoord.xy + GameTime * 37.0) - 0.5) * 0.12;

    monochrome = monochrome * scanline * rollingBand * (0.62 + vignette * 0.38);
    monochrome += vec3(grain);

    fragColor = vec4(clamp(monochrome, 0.0, 1.0), 1.0);
}
