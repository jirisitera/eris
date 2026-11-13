uniform sampler2D EmoteFontSampler;
#include <eris:post/emote_font.glsl>

const float EMOTE_WHEEL_PI = 3.14159265;
const float EMOTE_WHEEL_RADIUS = 0.38;
const float EMOTE_WHEEL_DEADZONE = 0.25; // shared with EmoteWheel.java
// Logical input uses this reference aspect, independent of the viewport.
const float EMOTE_WHEEL_CURSOR_ASPECT = 16.0 / 9.0;

// Four ASCII characters per uint, least significant byte first.
const uvec4 EMOTE_TEXT[18] = uvec4[](
    uvec4(0x544f4d45u, 0x00005345u, 0u, 0u), // EMOTES
    uvec4(0x65766157u, 0u, 0u, 0u), // Wave
    uvec4(0x6c6c6f52u, 0u, 0u, 0u), // Roll
    uvec4(0x74706d45u, 0x00000079u, 0u, 0u), // Empty
    uvec4(0x20796153u, 0x6c6c6568u, 0x0000006fu, 0u), // Say hello
    uvec4(0x656b6154u, 0x74206120u, 0x6c626d75u, 0x00000065u), // Take a tumble
    uvec4(0x20746f4eu, 0x69737361u, 0x64656e67u, 0u), // Not assigned
    uvec4(0x63696c43u, 0x6f74206bu, 0x616c7020u, 0x00000079u), // Click to play
    uvec4(0x6f6f6843u, 0x61206573u, 0x6d65206eu, 0x0065746fu), // Choose an emote
    uvec4(0x6f742046u, 0x6f6c6320u, 0x00006573u, 0u), // F to close
    uvec4(0x00003130u, 0u, 0u, 0u), // 01
    uvec4(0x00003230u, 0u, 0u, 0u), // 02
    uvec4(0x00003330u, 0u, 0u, 0u), // 03
    uvec4(0x00003430u, 0u, 0u, 0u), // 04
    uvec4(0x00003530u, 0u, 0u, 0u), // 05
    uvec4(0x00003630u, 0u, 0u, 0u), // 06
    uvec4(0x00003730u, 0u, 0u, 0u), // 07
    uvec4(0x00003830u, 0u, 0u, 0u)  // 08
);
const int EMOTE_TEXT_LENGTH[18] = int[](6, 4, 4, 5, 9, 13, 12, 13, 15, 10, 2, 2, 2, 2, 2, 2, 2, 2);

float emoteText(vec2 uv, vec2 center, float height, int label) {
    int count = EMOTE_TEXT_LENGTH[label];
    // Small captions need a little area filtering when the atlas is reduced.
    vec2 filterRadius = fwidth(uv) * (18.0 / height) / vec2(768.0, 432.0);
    vec2 rel = vec2(uv.x - center.x, center.y - uv.y) * (72.0 / height);
    if (abs(rel.y) >= 36.0) return 0.0;
    float width = 0.0;
    for (int i = 0; i < count; i++) {
        int glyph = int((EMOTE_TEXT[label][i / 4] >> uint((i % 4) * 8)) & 255u) - 32;
        width += EMOTE_GLYPH_ADVANCE[glyph];
    }
    float x = rel.x + width * 0.5;
    if (x < 0.0 || x >= width) return 0.0;
    for (int i = 0; i < count; i++) {
        int glyph = int((EMOTE_TEXT[label][i / 4] >> uint((i % 4) * 8)) & 255u) - 32;
        float advance = EMOTE_GLYPH_ADVANCE[glyph];
        if (x < advance) {
            vec2 local = vec2(x + 24.0 - advance * 0.5, rel.y + 36.0);
            vec2 pixel = vec2(glyph % 16, glyph / 16) * vec2(48.0, 72.0) + local;
            vec2 sampleUv = pixel / vec2(768.0, 432.0);
            return 0.25 * (
                texture(EmoteFontSampler, sampleUv + filterRadius).r +
                texture(EmoteFontSampler, sampleUv - filterRadius).r +
                texture(EmoteFontSampler, sampleUv + filterRadius * vec2(1.0, -1.0)).r +
                texture(EmoteFontSampler, sampleUv + filterRadius * vec2(-1.0, 1.0)).r);
        }
        x -= advance;
    }
    return 0.0;
}

int getEmoteWheelSlice(float angle) {
    float shifted = mod(angle + 2.0 * EMOTE_WHEEL_PI + EMOTE_WHEEL_PI / 8.0, 2.0 * EMOTE_WHEEL_PI);
    return int(floor(shifted / (EMOTE_WHEEL_PI / 4.0))) % 8;
}

int getHoveredEmoteSlice(vec2 cursorOffset) {
    if (length(cursorOffset) < EMOTE_WHEEL_DEADZONE) return -1;
    return getEmoteWheelSlice(atan(cursorOffset.x, cursorOffset.y));
}

float roundedWedgeSDF(float dist, float innerRadius, float outerRadius, float dividerDist, float gapHalfWidth, float cornerRadius) {
    vec2 q = vec2(max(dist - outerRadius, innerRadius - dist), gapHalfWidth - dividerDist) + cornerRadius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - cornerRadius;
}

vec4 renderEmoteWheel(vec2 uv, int selectedSlice) {
    float dist = length(uv);
    float aa = max(fwidth(dist), 0.001);
    if (dist > 1.18) return vec4(0.0);
    vec3 accent = vec3(0.59, 0.86, 0.70);
    vec3 ink = vec3(0.94, 0.94, 0.88);
    vec3 muted = vec3(0.51, 0.57, 0.54);
    float angle = atan(uv.x, uv.y);
    int slice = getEmoteWheelSlice(angle);
    bool assigned = slice < 2;
    bool hovered = slice == selectedSlice;
    bool activeHover = hovered && assigned;
    float sliceAngle = float(slice) * (EMOTE_WHEEL_PI / 4.0);
    float localAngle = abs(mod(angle - sliceAngle + EMOTE_WHEEL_PI, 2.0 * EMOTE_WHEEL_PI) - EMOTE_WHEEL_PI);
    float divider = dist * sin(max(EMOTE_WHEEL_PI / 8.0 - localAngle, 0.0));
    float wedge = roundedWedgeSDF(dist, 0.33, activeHover ? 0.96 : 0.93, divider, 0.013, 0.028);

    // Soft exterior shadow; panel edges use screen-space antialiasing.
    vec3 color = vec3(0.012, 0.019, 0.016);
    float alpha = exp(-max(wedge, 0.0) * 35.0) * 0.24;
    float mask = 1.0 - smoothstep(-aa, aa, wedge);
    float radial = clamp((dist - 0.33) / 0.63, 0.0, 1.0);
    vec3 fill = mix(vec3(0.075, 0.095, 0.085), vec3(0.13, 0.15, 0.135), radial);
    if (activeHover) fill = mix(vec3(0.13, 0.26, 0.20), vec3(0.22, 0.38, 0.28), radial);
    if (!assigned) fill *= 0.78;
    float border = 1.0 - smoothstep(aa, aa * 2.0, abs(wedge + 0.004));
    vec3 borderColor = activeHover ? accent : hovered ? vec3(0.39, 0.44, 0.39) : vec3(0.24, 0.28, 0.25);
    fill = mix(fill, borderColor, border * (activeHover ? 0.85 : 0.55));
    // A restrained upper highlight suggests stone/metal without a pixel bevel.
    fill += vec3(0.018) * smoothstep(-0.8, 0.8, uv.y);

    vec2 slot = vec2(sin(sliceAngle), cos(sliceAngle)) * 0.65;
    float number = emoteText(uv, slot + vec2(0.0, 0.105), 0.075, 10 + slice);
    float name = emoteText(uv, slot + vec2(0.0, 0.005), assigned ? 0.14 : 0.11, assigned ? slice + 1 : 3);
    float description = assigned ? emoteText(uv, slot - vec2(0.0, 0.095), 0.085, slice + 4) : 0.0;
    fill = mix(fill, activeHover ? accent : muted, number);
    fill = mix(fill, assigned ? ink : muted, name);
    fill = mix(fill, activeHover ? vec3(0.76, 0.85, 0.77) : muted, description);
    color = mix(color, fill, mask);
    alpha = mix(alpha, 0.97, mask);

    float hub = 1.0 - smoothstep(-aa, aa, dist - 0.275);
    vec3 hubColor = vec3(0.055, 0.075, 0.065);
    float ring = 1.0 - smoothstep(aa, aa * 2.0, abs(dist - 0.272));
    hubColor = mix(hubColor, vec3(0.29, 0.36, 0.30), ring * 0.65);
    float heading = emoteText(uv, vec2(0.0, 0.125), 0.085, 0);
    int title = selectedSlice < 0 ? 8 : selectedSlice < 2 ? selectedSlice + 1 : 3;
    float titleInk = emoteText(uv, vec2(0.0, 0.005), selectedSlice < 0 ? 0.09 : 0.16, title);
    int hint = selectedSlice < 0 ? 9 : selectedSlice < 2 ? 7 : 6;
    float hintInk = emoteText(uv, vec2(0.0, -0.115), 0.07, hint);
    hubColor = mix(hubColor, muted, heading);
    hubColor = mix(hubColor, selectedSlice >= 0 && selectedSlice < 2 ? accent : ink, titleInk);
    hubColor = mix(hubColor, muted, hintInk);
    color = mix(color, hubColor, hub);
    alpha = mix(alpha, 0.98, hub);

    float footer = emoteText(uv, vec2(0.0, -1.045), 0.085, 9);
    color = mix(color, vec3(0.78, 0.82, 0.77), footer);
    alpha = max(alpha, footer);
    return vec4(color, alpha);
}
