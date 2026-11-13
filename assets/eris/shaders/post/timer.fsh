#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <eris:post/timer.glsl>
#include <eris:post/ui_data.glsl>
// @formatter:on

uniform sampler2D InSampler;
uniform sampler2D FontSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// font atlas: textures/effect/font.png, 40x64 cells, 12 per row: 0-9 :, A-L, M-X, Y Z ! ? . - '
const vec2 FONT_CELL = vec2(40.0, 64.0);
const int FONT_COLUMNS = 12;
const int CH_COLON = 10;
const int CH_X = 35;
const vec2 TIMER_ASPECT = vec2(2.0, 1.0);

float fontGlyph(int glyph, vec2 local) {
    if (glyph < 0 || any(lessThan(local, vec2(0.0))) || any(greaterThanEqual(local, vec2(1.0)))) {
        return 0.0;
    }
    vec2 texel = (vec2(float(glyph % FONT_COLUMNS), float(glyph / FONT_COLUMNS)) + local) * FONT_CELL;
    return texture(FontSampler, texel / vec2(textureSize(FontSampler, 0))).r;
}

int textLine(vec2 uv, vec2 center, float height, int count, out vec2 local) {
    float cellWidth = height * (FONT_CELL.x / FONT_CELL.y) / TIMER_ASPECT.x;
    vec2 rel = (uv - center) / vec2(cellWidth, height) + vec2(float(count) * 0.5, 0.5);
    local = fract(rel);
    if (rel.y < 0.0 || rel.y >= 1.0 || rel.x < 0.0 || rel.x >= float(count)) {
        return -1;
    }
    return int(floor(rel.x));
}

vec3 renderTimer(vec2 uv, int minutes, int seconds, int strikes) {
    vec2 bezel = vec2(0.055, 0.11);
    if (uv.x < bezel.x || uv.x > 1.0 - bezel.x || uv.y < bezel.y || uv.y > 1.0 - bezel.y) {
        vec3 frame = vec3(0.16, 0.16, 0.18);
        if (uv.x < bezel.x * 0.45 || uv.y < bezel.y * 0.45) {
            frame = vec3(0.28, 0.28, 0.31);
        } else if (uv.x > 1.0 - bezel.x * 0.45 || uv.y > 1.0 - bezel.y * 0.45) {
            frame = vec3(0.08, 0.08, 0.09);
        }
        return frame;
    }

    vec2 innerUv = (uv - bezel) / (1.0 - bezel * 2.0);
    vec3 col = mix(vec3(0.06, 0.045, 0.015), vec3(0.03, 0.022, 0.008), innerUv.y);

    col *= 0.88 + 0.12 * sin(innerUv.y * 64.0 * 3.14159265);
    float glare = smoothstep(0.25, 0.0, abs(innerUv.x - innerUv.y * 0.55 - 0.18)) * 0.04;
    col += vec3(0.95, 0.8, 0.4) * glare;

    // top row: MM:SS countdown (pure yellow LED)
    int chars[5] = int[](minutes / 10, minutes % 10, CH_COLON, seconds / 10, seconds % 10);
    int ghost[5] = int[](8, 8, CH_COLON, 8, 8);

    vec2 local;
    int idx = textLine(innerUv, vec2(0.5, 0.31), 0.56, 5, local);
    if (idx >= 0) {
        float ghostInk = fontGlyph(ghost[idx], local);
        float activeInk = fontGlyph(chars[idx], local);
        col = mix(col, vec3(0.22, 0.16, 0.035), ghostInk * 0.55);
        col = mix(col, vec3(1.0, 1.0, 0.0), activeInk);
    }

    // bottom row: 3 strike indicators ('X') enabled left-to-right
    const float STRIKE_HEIGHT = 0.46;
    const float STRIKE_SPACING = 0.22;
    float strikeCellWidth = STRIKE_HEIGHT * (FONT_CELL.x / FONT_CELL.y) / TIMER_ASPECT.x;
    for (int i = 0; i < 3; i++) {
        vec2 strikeCenter = vec2(0.5 + float(i - 1) * STRIKE_SPACING, 0.72);
        vec2 strikeLocal = (innerUv - strikeCenter) / vec2(strikeCellWidth, STRIKE_HEIGHT) + 0.5;
        if (all(greaterThanEqual(strikeLocal, vec2(0.0))) && all(lessThan(strikeLocal, vec2(1.0)))) {
            float xInk = fontGlyph(CH_X, strikeLocal);
            col = mix(col, vec3(0.20, 0.035, 0.03), xInk * 0.55);
            if (i < strikes) {
                col = mix(col, vec3(1.0, 0.20, 0.14), xInk);
            }
        }
    }

    vec2 edge = min(innerUv, 1.0 - innerUv) * TIMER_ASPECT;
    col *= smoothstep(0.0, 0.06, min(edge.x, edge.y)) * 0.45 + 0.55;
    return col;
}

void main() {
    ivec2 pixel = ivec2(gl_FragCoord.xy);
    vec4 scene = texelFetch(InSampler, pixel, 0);
    vec2 uv;
    int minutes, seconds, strikes;
    if (!isUiDataPixel(pixel) && decodeTimer(InSampler, pixel, uv, minutes, seconds, strikes)) {
        scene.rgb = renderTimer(uv, minutes, seconds, strikes);
    }
    fragColor = scene;
}
