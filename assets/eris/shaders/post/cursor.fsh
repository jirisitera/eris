#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:globals.glsl>
#include <eris:monitor_data.glsl>
// @formatter:on

uniform sampler2D InSampler;
uniform sampler2D CursorSampler;
uniform sampler2D BloomSampler;
uniform sampler2D AoSampler;
uniform sampler2D FontSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Marker and post-processing constants
const int PLAYER_LOOK_ROW_Y = 16;
const int MONITOR_ROW_BASE_Y = 19;
const ivec3 ACTIVE_MARKER = ivec3(91, 126, 209);

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

const float PI = 3.14159265;
const vec2 MONITOR_ASPECT = vec2(11.0 / 8.0, 1.0);

vec2 gLookAngles = vec2(0.0);
bool gHasLook = false;

bool monitorActive(int monitorIndex) {
    int rowY = MONITOR_ROW_BASE_Y - monitorIndex;
    return ivec3(round(texelFetch(InSampler, ivec2(40, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER &&
    ivec3(round(texelFetch(InSampler, ivec2(41, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER;
}

vec2 decodeAngles(ivec2 pixel0, ivec2 pixel1, float pitchRange) {
    ivec3 color0 = ivec3(round(texelFetch(InSampler, pixel0, 0).rgb * 255.0));
    ivec3 color1 = ivec3(round(texelFetch(InSampler, pixel1, 0).rgb * 255.0));
    return vec2(
        float(color0.r * 256 + color0.g) / 65535.0 * 360.0 - 180.0,
        float(color0.b * 256 + color1.r) / 65535.0 * pitchRange - pitchRange * 0.5
    );
}

bool getLookAngles(out vec2 lookAngles) {
    lookAngles = vec2(0.0);
    ivec2 pixel0 = ivec2(0);
    ivec2 pixel1 = ivec2(0);
    bool found = false;
    for (int m = 0; m < 3; m++) {
        if (monitorActive(m)) {
            int rowY = MONITOR_ROW_BASE_Y - m;
            pixel0 = ivec2(42, rowY);
            pixel1 = ivec2(43, rowY);
            found = true;
            break;
        }
    }
    if (!found) {
        return false;
    }
    vec2 bias = decodeAngles(pixel0, pixel1, 360.0);
    if (
        ivec3(round(texelFetch(InSampler, ivec2(28, PLAYER_LOOK_ROW_Y), 0).rgb * 255.0)) == ACTIVE_MARKER
    ) {
        vec2 rawLook = decodeAngles(ivec2(26, PLAYER_LOOK_ROW_Y), ivec2(27, PLAYER_LOOK_ROW_Y), 180.0);
        lookAngles = vec2(
            mod(mod(rawLook.x + bias.x + 180.0, 360.0) + 360.0, 360.0) - 180.0,
            rawLook.y + bias.y
        );
    } else {
        lookAngles = bias;
    }
    return true;
}

// Cursor position on the monitor desk: returns the monitor under the cursor (or -1) and the UV on it.
int cursorMonitor(out vec2 cursorLocal) {
    cursorLocal = vec2(-1.0);
    if (!gHasLook) return -1;
    float virtualX = clamp((gLookAngles.x + 54.0) / 108.0 * 3.0, 0.0, 2.9999);
    cursorLocal = vec2(fract(virtualX), clamp((gLookAngles.y + 25.0) / 50.0, 0.0, 1.0));
    return int(floor(virtualX));
}

// 4x4 Bayer matrix ordered dithering
float bayerDither4x4(vec2 coord) {
    const int bayerMatrix[16] = int[](
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    );
    ivec2 modCoord = ivec2(mod(coord, 4.0));
    return (float(bayerMatrix[modCoord.y * 4 + modCoord.x]) + 0.5) / 16.0;
}

float hash12(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// ---- Font atlas (the terminal font from the old TV, textures/effect/monitor_font.png) ----
// 40x64 cells, 12 per row: 0-9 :, A-L, M-X, Y Z ! ? . - '
const vec2 FONT_CELL = vec2(40.0, 64.0);
const int FONT_COLUMNS = 12;
#define CH_SPACE - 1
#define CH_COLON 10
#define CH_A 12
#define CH_B 13
#define CH_C 14
#define CH_D 15
#define CH_E 16
#define CH_F 17
#define CH_G 18
#define CH_H 19
#define CH_I 20
#define CH_J 21
#define CH_K 22
#define CH_L 23
#define CH_M 24
#define CH_N 25
#define CH_O 26
#define CH_P 27
#define CH_Q 28
#define CH_R 29
#define CH_S 30
#define CH_T 31
#define CH_U 32
#define CH_V 33
#define CH_W 34
#define CH_X 35
#define CH_Y 36
#define CH_Z 37
#define CH_EXCLAMATION 38
#define CH_QUESTION 39
#define CH_PERIOD 40
#define CH_DASH 41
#define CH_APOSTROPHE 42
#define CH_PERCENT 100 // not in the atlas, drawn procedurally

float segmentDistance(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a;
    vec2 ba = b - a;
    return length(pa - ba * clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0));
}

// '%' matching the atlas glyph metrics (ink spans roughly x 5..33, y 13..53 of the 40x64 cell)
float percentGlyph(vec2 local) {
    vec2 p = local * FONT_CELL;
    float d = min(abs(length(p - vec2(12.0, 21.0)) - 5.0), abs(length(p - vec2(27.0, 45.0)) - 5.0));
    d = min(d, segmentDistance(p, vec2(31.0, 13.0), vec2(8.0, 53.0)));
    return 1.0 - smoothstep(1.8, 3.2, d);
}

// Antialiased glyph coverage, local is the position inside the glyph cell (0..1, y down)
float fontGlyph(int glyph, vec2 local) {
    if (glyph < 0 || any(lessThan(local, vec2(0.0))) || any(greaterThanEqual(local, vec2(1.0)))) return 0.0;
    if (glyph == CH_PERCENT) return percentGlyph(local);
    vec2 texel = (vec2(float(glyph % FONT_COLUMNS), float(glyph / FONT_COLUMNS)) + local) * FONT_CELL;
    return texture(FontSampler, texel / vec2(textureSize(FontSampler, 0))).r;
}

// Finds the character cell of a text line (centered on `center`, `height` in monitor UV) under uv.
// Returns the character index (or -1) and the position inside its cell.
int textLine(vec2 uv, vec2 center, float height, int count, out vec2 local) {
    float cellWidth = height * (FONT_CELL.x / FONT_CELL.y) / MONITOR_ASPECT.x;
    vec2 rel = (uv - center) / vec2(cellWidth, height) + vec2(float(count) * 0.5, 0.5);
    local = fract(rel);
    if (rel.y < 0.0 || rel.y >= 1.0 || rel.x < 0.0 || rel.x >= float(count)) return -1;
    return int(floor(rel.x));
}

// ---- Camera buttons (right monitor) ----
// 2x2 matrix, measured in monitor-height units so they stay square.
// Must match MapProbe.hoveredButton on the server.
#define BTN_HALF 0.18
#define BTN_CENTER 0.22
int buttonAt(vec2 uv, out vec2 local) {
    vec2 p = (uv - 0.5) * MONITOR_ASPECT;
    vec2 d = abs(p) - vec2(BTN_CENTER);
    local = (d * sign(p)) / (BTN_HALF * 2.0) + 0.5;
    if (max(abs(d.x), abs(d.y)) > BTN_HALF) return -1;
    return (p.x > 0.0 ? 1 : 0) + (p.y > 0.0 ? 2 : 0);
}

vec3 renderButton(int index, vec2 local, bool hovered) {
    const float bevel = 0.07;
    vec3 col = hovered ? vec3(0.45, 0.5, 0.45) : vec3(0.22);
    if (local.x < bevel || local.y < bevel) {
        col = hovered ? vec3(0.75, 0.8, 0.75) : vec3(0.5);
    } else if (local.x > 1.0 - bevel || local.y > 1.0 - bevel) {
        col = vec3(0.08);
    }
    // digit glyph cell: 0.6 of the button height, keeping the 40:64 cell aspect
    vec2 cellSize = vec2(0.6 * FONT_CELL.x / FONT_CELL.y, 0.6);
    float ink = fontGlyph(index + 1, (local - (0.5 - cellSize * 0.5)) / cellSize);
    return mix(col, hovered ? vec3(1.0) : vec3(0.78), ink);
}

vec4 cameraButtonsContent(int monitorIndex, vec2 uv) {
    vec2 local;
    int button = buttonAt(uv, local);
    if (button < 0) return vec4(0.0);
    vec2 cursorLocal;
    int hovered = -1;
    if (cursorMonitor(cursorLocal) == monitorIndex) {
        vec2 unused;
        hovered = buttonAt(cursorLocal, unused);
    }
    return vec4(renderButton(button, local, hovered == button), 1.0);
}

// ---- Energy monitor (center) ----
vec4 energyContent(int rowY, vec2 uv) {
    vec4 content = vec4(0.0);
    ivec3 posPixel = ivec3(round(texelFetch(InSampler, ivec2(8, rowY), 0).rgb * 255.0));
    int percent = 100;
    int usage = 2;
    int maxUsage = 5;
    if (posPixel != ivec3(255, 255, 255)) {
        ivec3 data = ivec3(round(texelFetch(InSampler, ivec2(9, rowY), 0).rgb * 255.0));
        percent = clamp(data.r, 0, 100);
        usage = clamp(data.g, 0, 16);
        maxUsage = max(1, data.b);
    }

    vec2 local;
    // 1. "POWER" label
    const int LABEL[5] = int[](CH_P, CH_O, CH_W, CH_E, CH_R);
    int idx = textLine(uv, vec2(0.5, 0.13), 0.1, 5, local);
    if (idx >= 0) {
        content = vec4(0.5, 0.58, 0.5, fontGlyph(LABEL[idx], local));
    }

    // 2. Percentage readout (e.g. "100%", "85%", "7%")
    int chars[4];
    int charCount = 0;
    if (percent >= 100) {
        chars[0] = 1;
        chars[1] = 0;
        chars[2] = 0;
        chars[3] = CH_PERCENT;
        charCount = 4;
    } else if (percent >= 10) {
        chars[0] = percent / 10;
        chars[1] = percent - chars[0] * 10;
        chars[2] = CH_PERCENT;
        charCount = 3;
    } else {
        chars[0] = percent;
        chars[1] = CH_PERCENT;
        charCount = 2;
    }
    idx = textLine(uv, vec2(0.5, 0.35), 0.26, charCount, local);
    if (idx >= 0) {
        vec3 textCol = percent <= 20 ? vec3(0.95, 0.25, 0.25) : vec3(0.85, 0.92, 0.85);
        content = vec4(textCol, fontGlyph(chars[idx], local));
    }

    // 3. Segmented energy usage bar in the lower half
    vec2 barMin = vec2(0.12, 0.56);
    vec2 barMax = vec2(0.86, 0.84);
    vec2 border = vec2(0.02, 0.025);
    // Battery terminal nub on the right
    if (uv.x >= barMax.x && uv.x <= barMax.x + 0.035 && uv.y >= 0.64 && uv.y <= 0.76) {
        content = vec4(vec3(0.45), 1.0);
    }
    if (all(greaterThanEqual(uv, barMin)) && all(lessThanEqual(uv, barMax))) {
        bool isBorder =
        uv.x < barMin.x + border.x || uv.x > barMax.x - border.x ||
        uv.y < barMin.y + border.y || uv.y > barMax.y - border.y;
        if (isBorder) {
            content = vec4(vec3(0.45), 1.0);
        } else {
            vec2 innerMin = barMin + border * 1.8;
            vec2 innerMax = barMax - border * 1.8;
            if (all(greaterThanEqual(uv, innerMin)) && all(lessThanEqual(uv, innerMax))) {
                float normX = (uv.x - innerMin.x) / (innerMax.x - innerMin.x);
                float segCoord = normX * float(maxUsage);
                int seg = clamp(int(floor(segCoord)), 0, maxUsage - 1);
                float segFrac = fract(segCoord);
                // Leave a gap between adjacent segments
                if (segFrac >= 0.08 && segFrac <= 0.92) {
                    if (seg < usage) {
                        float t = maxUsage > 1 ? float(seg) / float(maxUsage - 1) : 0.0;
                        vec3 lowCol = vec3(0.2, 0.88, 0.32);
                        vec3 midCol = vec3(0.95, 0.82, 0.18);
                        vec3 highCol = vec3(0.95, 0.22, 0.2);
                        vec3 segCol = t < 0.5 ? mix(lowCol, midCol, t * 2.0) : mix(midCol, highCol, (t - 0.5) * 2.0);
                        float localY = (uv.y - innerMin.y) / (innerMax.y - innerMin.y);
                        if (localY < 0.18) {
                            segCol = mix(segCol, vec3(1.0), 0.35);
                        }
                        content = vec4(segCol, 1.0);
                    } else {
                        content = vec4(vec3(0.08), 1.0);
                    }
                }
            }
        }
    }
    return content;
}

// ---- Clock monitor (left) ----
// Slot 0 carries (hour, hourProgress, 0), see MapProbe.clockDot. Hour 0 = 12 AM, CLOCK_FINAL_HOUR = 6 AM.
const int CLOCK_FINAL_HOUR = 6;
vec4 clockContent(int rowY, vec2 uv) {
    vec4 content = vec4(0.0);
    ivec3 posPixel = ivec3(round(texelFetch(InSampler, ivec2(8, rowY), 0).rgb * 255.0));
    int hour = 0;
    float hourProgress = 0.0;
    if (posPixel != ivec3(255, 255, 255)) {
        ivec3 data = ivec3(round(texelFetch(InSampler, ivec2(9, rowY), 0).rgb * 255.0));
        hour = clamp(data.r, 0, CLOCK_FINAL_HOUR);
        hourProgress = clamp(float(data.g) / 255.0, 0.0, 1.0);
    }
    bool finished = hour >= CLOCK_FINAL_HOUR;

    vec2 local;
    // 1. "TIME" label
    const int LABEL[4] = int[](CH_T, CH_I, CH_M, CH_E);
    int idx = textLine(uv, vec2(0.5, 0.13), 0.1, 4, local);
    if (idx >= 0) {
        content = vec4(0.5, 0.58, 0.5, fontGlyph(LABEL[idx], local));
    }

    // 2. Time readout ("12 AM", "1 AM", ... "6 AM")
    int chars[5];
    int charCount = 0;
    if (hour == 0) {
        chars[0] = 1;
        chars[1] = 2;
        charCount = 2;
    } else {
        chars[0] = hour;
        charCount = 1;
    }
    chars[charCount] = CH_SPACE;
    chars[charCount + 1] = CH_A;
    chars[charCount + 2] = CH_M;
    charCount += 3;
    idx = textLine(uv, vec2(0.5, 0.38), 0.26, charCount, local);
    if (idx >= 0) {
        // colon-less clock, so blink the whole readout once the night is over
        bool blinkOff = finished && fract(GameTime * 1200.0 * 1.5) > 0.6;
        vec3 textCol = finished ? vec3(0.35, 0.95, 0.4) : vec3(0.85, 0.92, 0.85);
        content = vec4(textCol, blinkOff ? 0.0 : fontGlyph(chars[idx], local));
    }

    // 3. Night progress bar: one segment per hour, the current one fills up gradually
    vec2 barMin = vec2(0.12, 0.62);
    vec2 barMax = vec2(0.88, 0.82);
    vec2 border = vec2(0.02, 0.025);
    if (all(greaterThanEqual(uv, barMin)) && all(lessThanEqual(uv, barMax))) {
        bool isBorder =
        uv.x < barMin.x + border.x || uv.x > barMax.x - border.x ||
        uv.y < barMin.y + border.y || uv.y > barMax.y - border.y;
        if (isBorder) {
            content = vec4(vec3(0.45), 1.0);
        } else {
            vec2 innerMin = barMin + border * 1.8;
            vec2 innerMax = barMax - border * 1.8;
            if (all(greaterThanEqual(uv, innerMin)) && all(lessThanEqual(uv, innerMax))) {
                float normX = (uv.x - innerMin.x) / (innerMax.x - innerMin.x);
                float segCoord = normX * float(CLOCK_FINAL_HOUR);
                int seg = clamp(int(floor(segCoord)), 0, CLOCK_FINAL_HOUR - 1);
                float segFrac = fract(segCoord);
                if (segFrac >= 0.08 && segFrac <= 0.92) {
                    float fill = seg < hour ? 1.0 : seg == hour ? hourProgress : 0.0;
                    bool filled = (segFrac - 0.08) / 0.84 <= fill && fill > 0.0;
                    if (filled) {
                        vec3 segCol = finished ? vec3(0.35, 0.95, 0.4) : vec3(0.75, 0.82, 0.75);
                        float localY = (uv.y - innerMin.y) / (innerMax.y - innerMin.y);
                        if (localY < 0.18) {
                            segCol = mix(segCol, vec3(1.0), 0.35);
                        }
                        content = vec4(segCol, 1.0);
                    } else {
                        content = vec4(vec3(0.08), 1.0);
                    }
                }
            }
        }
    }
    return content;
}

// ---- Lobby screen ("DO YOU WANT TO PLAY A GAME?" + YES button) ----
const int LOBBY_LINE_1[14] = int[](CH_D, CH_O, CH_SPACE, CH_Y, CH_O, CH_U, CH_SPACE, CH_W, CH_A, CH_N, CH_T, CH_SPACE, CH_T, CH_O);
const int LOBBY_LINE_2[12] = int[](CH_P, CH_L, CH_A, CH_Y, CH_SPACE, CH_A, CH_SPACE, CH_G, CH_A, CH_M, CH_E, CH_QUESTION);
const int LOBBY_YES[3] = int[](CH_Y, CH_E, CH_S);

// Must match MapProbe.LOBBY_* on the server.
const float LOBBY_BTN_CENTER_Y = 0.68;
const vec2 LOBBY_BTN_HALF = vec2(0.2, 0.11);

// Slot 0 marker that switches a monitor into the lobby screen (MapProbe.lobbyDots)
bool lobbyActive(int rowY) {
    return ivec3(round(texelFetch(InSampler, ivec2(8, rowY), 0).rgb * 255.0)) == ivec3(0) &&
    ivec3(round(texelFetch(InSampler, ivec2(9, rowY), 0).rgb * 255.0)) == ACTIVE_MARKER;
}

bool lobbyButton(vec2 uv, out vec2 local) {
    vec2 p = (uv - vec2(0.5, LOBBY_BTN_CENTER_Y)) * MONITOR_ASPECT;
    local = p / (LOBBY_BTN_HALF * 2.0) + 0.5;
    return abs(p.x) <= LOBBY_BTN_HALF.x && abs(p.y) <= LOBBY_BTN_HALF.y;
}

vec4 lobbyContent(vec2 uv, bool hovered) {
    // Title: black ink straight on top of the static, like the old TV
    vec2 local;
    float ink = 0.0;
    int idx = textLine(uv, vec2(0.5, 0.24), 0.13, 14, local);
    if (idx >= 0) ink = fontGlyph(LOBBY_LINE_1[idx], local);
    idx = textLine(uv, vec2(0.5, 0.40), 0.13, 12, local);
    if (idx >= 0) ink = max(ink, fontGlyph(LOBBY_LINE_2[idx], local));
    vec4 content = vec4(vec3(0.02), ink);

    vec2 btnLocal;
    if (lobbyButton(uv, btnLocal)) {
        vec3 light = vec3(0.92);
        vec3 dark = vec3(0.03);
        // distance to the button edge in monitor-height units
        vec2 edge = min(btnLocal, 1.0 - btnLocal) * LOBBY_BTN_HALF * 2.0;
        float edgeDist = min(edge.x, edge.y);
        // solid panel with an inset frame; hovering inverts it
        vec3 col = hovered ? light : dark;
        if (edgeDist > 0.014 && edgeDist < 0.026) col = hovered ? dark : light;
        idx = textLine(uv, vec2(0.5, LOBBY_BTN_CENTER_Y), 0.12, 3, local);
        float textInk = idx >= 0 ? fontGlyph(LOBBY_YES[idx], local) : 0.0;
        col = mix(col, hovered ? dark : light, textInk);
        content = vec4(col, 1.0);
    }
    return content;
}

vec4 monitorContent(int monitorIndex, int rowY, bool lobby, bool lobbyHovered, vec2 uv) {
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec4(0.0);
    if (lobby) return lobbyContent(uv, lobbyHovered);
    if (monitorIndex == 1) return energyContent(rowY, uv);
    if (monitorIndex == 2) return cameraButtonsContent(monitorIndex, uv);
    return clockContent(rowY, uv);
}

// ---- CRT tube (ported from the old tv.glsl) ----
const float CRT_LINES = 150.0;   // scanlines across the screen height
const float CRT_TRIADS = 210.0;  // RGB phosphor columns across the screen width

// Animated TV static with a slowly rolling interference bar
vec3 tvStatic(vec2 uv, float time, float strength) {
    float frame = mod(floor(time * 30.0), 1000.0);
    float line = floor(uv.y * CRT_LINES);
    vec2 wobble = vec2(sin(uv.y * 41.0 + time * 23.0), sin(uv.x * 37.0 - time * 19.0)) * 0.006;
    float grain = hash12(floor((uv + wobble) * vec2(300.0, 220.0)) + frame * 1.37);
    float lineFlicker = hash12(vec2(line, mod(floor(time * 24.0), 1000.0)));
    float rollingBar = smoothstep(0.1, 0.0, abs(uv.y - fract(time * 0.35)));
    return (vec3(0.5 + 0.5 * grain) * (0.88 + 0.12 * lineFlicker) + rollingBar * 0.15) * strength;
}

// Reflections on the glass (screen-space, unaffected by the tube curvature)
vec3 screenGlare(vec2 uv, vec2 centered) {
    float streak = smoothstep(0.22, 0.0, abs(uv.x - uv.y * 0.7 - 0.12)) * 0.06;
    float spot = exp(-length((uv - vec2(0.3, 0.16)) * vec2(1.0, 1.6)) * 9.0) * 0.12;
    return vec3(0.9, 0.95, 1.0) * (streak + spot) * (1.0 - smoothstep(0.7, 1.1, length(centered)));
}

vec3 renderMonitor(int monitorIndex, vec2 monitorUv) {
    int rowY = MONITOR_ROW_BASE_Y - monitorIndex;
    float time = GameTime * 1200.0;

    vec2 uv;
    vec2 centered;
    if (!curveMonitorUv(monitorUv, uv, centered)) {
        return vec3(0.0); // rounded tube corners
    }
    vec3 glare = screenGlare(monitorUv, centered);
    if (!monitorActive(monitorIndex)) {
        return vec3(0.012) + glare * 0.6; // switched off: dark glass
    }

    bool lobby = lobbyActive(rowY);
    vec2 cursorLocal;
    bool cursorHere = cursorMonitor(cursorLocal) == monitorIndex;
    vec2 unused;
    bool lobbyHovered = lobby && cursorHere && lobbyButton(cursorLocal, unused);

    // per-scanline horizontal jitter
    float line = floor(uv.y * CRT_LINES);
    uv.x += (hash12(vec2(line, mod(floor(time * 30.0), 1000.0))) - 0.5) * (lobby ? 0.006 : 0.0015);

    // full static on the lobby screen, a faint noise floor during the game
    vec3 col = tvStatic(uv, time, lobby ? 0.7 : 0.09);

    if (lobby) {
        // chromatic fringing on the content, like a badly converged tube
        const float split = 0.004;
        vec4 contentR = monitorContent(monitorIndex, rowY, lobby, lobbyHovered, uv + vec2(split, 0.0));
        vec4 contentG = monitorContent(monitorIndex, rowY, lobby, lobbyHovered, uv);
        vec4 contentB = monitorContent(monitorIndex, rowY, lobby, lobbyHovered, uv - vec2(split, 0.0));
        col = vec3(
            mix(col.r, contentR.r, contentR.a),
            mix(col.g, contentG.g, contentG.a),
            mix(col.b, contentB.b, contentB.a)
        );
    } else {
        vec4 content = monitorContent(monitorIndex, rowY, lobby, lobbyHovered, uv);
        col = mix(col, content.rgb, content.a);
    }

    // Cursor
    if (cursorHere) {
        vec2 cursorSize = vec2(0.18 * 8.0 / 11.0, 0.18);
        vec2 cursorTexel = floor((uv - cursorLocal) / cursorSize * 32.0);
        if (all(greaterThanEqual(cursorTexel, vec2(0.0))) && all(lessThan(cursorTexel, vec2(32.0)))) {
            vec4 cursorSample = texelFetch(CursorSampler, ivec2(cursorTexel), 0);
            col = mix(col, cursorSample.rgb, cursorSample.a);
        }
    }

    // Scanlines
    col *= 0.8 + 0.2 * pow(sin(uv.y * CRT_LINES * PI) * 0.5 + 0.5, 1.5);
    // RGB phosphor mask
    int triad = int(mod(floor(uv.x * CRT_TRIADS), 3.0));
    col *= triad == 0 ? vec3(1.06, 0.94, 0.94) : triad == 1 ? vec3(0.94, 1.06, 0.94) : vec3(0.94, 0.94, 1.06);
    // Tube vignette and mains flicker
    col *= 1.0 - smoothstep(0.6, 1.08, length(centered) * 0.8);
    col *= 0.96 + 0.04 * sin(time * 120.0);
    return clamp(col + glare, 0.0, 1.0);
}

// The top-left data rows are covered by metadata pixels; look just below them instead
ivec2 skipDataRows(ivec2 pixelCoord) {
    if (pixelCoord.y >= PLAYER_LOOK_ROW_Y - 2 && pixelCoord.y <= MONITOR_ROW_BASE_Y + 2 && pixelCoord.x < 48) {
        pixelCoord.y += 9;
    }
    return pixelCoord;
}

vec3 sampleScene(vec2 uv) {
    vec2 screenSize = vec2(textureSize(InSampler, 0));
    ivec2 pixelCoord = clamp(ivec2(uv * screenSize), ivec2(0), ivec2(screenSize) - 1);
    if (pixelCoord.y >= PLAYER_LOOK_ROW_Y - 2 && pixelCoord.y <= MONITOR_ROW_BASE_Y + 2 && pixelCoord.x < 48) {
        uv.y += 9.0 / screenSize.y;
        pixelCoord = clamp(ivec2(uv * screenSize), ivec2(0), ivec2(screenSize) - 1);
    }
    vec2 monitorUv;
    int monitorIndex = decodeMonitor(InSampler, pixelCoord, monitorUv);
    if (monitorIndex >= 0) {
        return renderMonitor(monitorIndex, monitorUv);
    }
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
    vec2 screenSize = vec2(textureSize(InSampler, 0));
    vec2 texelSize = 1.0 / screenSize;
    float time = GameTime * 1200.0;
    gHasLook = getLookAngles(gLookAngles);

    // Monitors are rendered at full resolution (no pixelation/posterization) so the CRT detail survives
    ivec2 fullPixel = skipDataRows(clamp(ivec2(texCoord * screenSize), ivec2(0), ivec2(screenSize) - 1));
    vec2 fullMonitorUv;
    int fullMonitor = decodeMonitor(InSampler, fullPixel, fullMonitorUv);
    if (fullMonitor >= 0) {
        vec3 screenColor = renderMonitor(fullMonitor, fullMonitorUv) * EXPOSURE + texture(BloomSampler, texCoord).rgb;
        screenColor = mix(screenColor, screenColor * COLOR_TINT, TINT_STRENGTH);
        screenColor = (screenColor - 0.5) * CONTRAST + 0.5;
        screenColor = clamp(adjustSaturation(screenColor, SATURATION), 0.0, 1.0);
        screenColor *= clamp(1.0 - smoothstep(0.5, 0.9, length(texCoord - 0.5)) * VIGNETTE_INTENSITY, 0.0, 1.0);
        fragColor = vec4(screenColor, 1.0);
        return;
    }

    vec2 pixelSize = texelSize * PIXEL_SCALE;
    vec2 pixelUv = floor(texCoord / pixelSize) * pixelSize + pixelSize * 0.5;
    vec2 centeredUv = pixelUv - 0.5;
    float distFromCenter = length(centeredUv);
    float distortion = CHROMATIC_ABERRATION * distFromCenter * distFromCenter;

    // Chromatic aberration + AO + Bloom
    vec3 sceneColor = vec3(
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
