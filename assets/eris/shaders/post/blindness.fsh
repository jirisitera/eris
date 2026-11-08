#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <eris:ui_data.glsl>
// @formatter:on

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const float SHAPE_BRIGHTNESS = 0.85;
const float DETAIL_BRIGHTNESS = 0.42;
const float EDGE_START = 0.18;
const float EDGE_END = 0.26;
const float RELATIVE_NOISE_FLOOR = 0.002;

float depthAt(ivec2 pixel, ivec2 size) {
    pixel = clamp(pixel, ivec2(0), size - 1);
    vec2 uv = withoutUiData(DepthSampler, (vec2(pixel) + 0.5) / vec2(size));
    return texelFetch(DepthSampler, ivec2(uv * vec2(size)), 0).r;
}

float crease(float before, float center, float after) {
    // Raw reversed depth is affine across a face. Reject its slope, keep corners.
    float curvature = abs(before + after - 2.0 * center);
    float slope = abs(before - center) + abs(after - center);
    float localDepth = max(center, max(before, after));
    float scale = max(slope, max(localDepth * RELATIVE_NOISE_FLOOR, 1e-7));
    return smoothstep(EDGE_START, EDGE_END, curvature / scale);
}

vec3 sceneAt(ivec2 pixel, ivec2 size) {
    vec2 uv = withoutUiData(InSampler, (vec2(pixel) + 0.5) / vec2(size));
    return texelFetch(InSampler, ivec2(uv * vec2(size)), 0).rgb;
}

float lightAt(ivec2 pixel, ivec2 size) {
    return dot(sceneAt(pixel, size), vec3(0.2126, 0.7152, 0.0722));
}

vec3 visibleSignal(vec3 color) {
    // Only retain nearly full-brightness, strongly saturated red/yellow signals.
    float red = max(color.r, 0.0001);
    float greenRatio = color.g / red;
    float blueRatio = color.b / red;
    float lowBlue = 1.0 - smoothstep(0.18, 0.24, blueRatio);
    float redMask = (1.0 - smoothstep(0.22, 0.28, greenRatio)) * lowBlue;
    float yellowMask = smoothstep(0.78, 0.88, greenRatio)
    * (1.0 - smoothstep(1.05, 1.12, greenRatio))
    * smoothstep(0.75, 0.90, color.g) * lowBlue;
    float brightness = smoothstep(0.85, 0.97, color.r);
    // Output only canonical red/yellow, never the rest of the scene's palette.
    return vec3(1.0, 0.0, 0.0) * redMask * brightness
    + vec3(1.0, 1.0, 0.0) * yellowMask * brightness;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    if (isUiDataPixel(pixel)) {
        fragColor = texelFetch(InSampler, pixel, 0);
        return;
    }
    ivec2 size = textureSize(DepthSampler, 0);
    ivec2 dx = ivec2(1, 0), dy = ivec2(0, 1);
    float center = depthAt(pixel, size);
    float shape = crease(depthAt(pixel - dx, size), center, depthAt(pixel + dx, size));
    shape = max(shape, crease(depthAt(pixel - dy, size), center, depthAt(pixel + dy, size)));
    shape = max(shape, crease(depthAt(pixel - dx - dy, size), center, depthAt(pixel + dx + dy, size)));
    shape = max(shape, crease(depthAt(pixel - dx + dy, size), center, depthAt(pixel + dx - dy, size)));
    if (pixel.x < 1 || pixel.y < 1 || pixel.x >= size.x - 1 || pixel.y >= size.y - 1) shape = 0.0;

    // Retain crisp texture/panel outlines too: depth alone cannot show flat digits.
    float light = lightAt(pixel, size);
    float contrast = max(max(abs(lightAt(pixel - dx, size) - light), abs(lightAt(pixel + dx, size) - light)),
        max(abs(lightAt(pixel - dy, size) - light), abs(lightAt(pixel + dy, size) - light)));
    float detail = smoothstep(0.10, 0.18, contrast);
    vec3 outline = vec3(max(shape * SHAPE_BRIGHTNESS, detail * DETAIL_BRIGHTNESS));
    vec3 signal = visibleSignal(sceneAt(pixel, size));
    float signalStrength = max(signal.r, signal.g);
    fragColor = vec4(mix(outline, signal, signalStrength), 1.0);
}
