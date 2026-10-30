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
    vec2 resolution = vec2(textureSize(InSampler, 0));
    float time = GameTime * 1200.0;
    float line = floor(texCoord.y * resolution.y);
    float tear = step(0.86, noise(vec2(line, floor(time * 0.5))));
    float offset = (noise(vec2(line, floor(time))) - 0.5) * 0.18 * tear;
    vec2 sampleCoord = clamp(texCoord + vec2(offset, 0.0), 0.0, 1.0);
    vec3 color = texture(InSampler, sampleCoord).rgb;

    float luminance = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 monochrome = vec3(luminance);
    float staticPattern = floor(noise(gl_FragCoord.xy + time) * 10.0) / 10.0;
    float staticNoise = (staticPattern - 0.5) * 0.48;
    float scanline = 0.68 + 0.32 * sin(texCoord.y * resolution.y * 3.14159265);
    float flash = 0.82 + 0.18 * sin(time * 0.7);

    monochrome = monochrome * 0.28 + vec3(0.5) + vec3(staticNoise);
    monochrome = monochrome * scanline * flash;
    fragColor = vec4(clamp(monochrome, 0.0, 1.0), 1.0);
}
