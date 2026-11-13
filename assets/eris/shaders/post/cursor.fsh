#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <eris:post/emotes.glsl>
#include <eris:post/ui_data.glsl>
// @formatter:on

uniform sampler2D InSampler;
uniform sampler2D CursorSampler;
uniform sampler2D FontSampler;

// @formatter:off
#include <eris:post/lobby_text.glsl>
// @formatter:on

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

const int PLAYER_LOOK_ROW_Y = 16;
const int CURSOR_PROBE_ROW_Y = 19;
const ivec3 ACTIVE_MARKER = ivec3(91, 126, 209);
// reveal radius in world blocks, independent of FOV and resolution
const float SURFACE_REVEAL_RADIUS = 0.12;

vec2 decodeAngles(ivec2 pixel0, ivec2 pixel1, float yawRange, float pitchRange) {
    ivec3 color0 = ivec3(round(texelFetch(InSampler, pixel0, 0).rgb * 255.0));
    ivec3 color1 = ivec3(round(texelFetch(InSampler, pixel1, 0).rgb * 255.0));
    return vec2(
        float(color0.r * 256 + color0.g) / 65535.0 * yawRange - yawRange * 0.5,
        float(color0.b * 256 + color1.r) / 65535.0 * pitchRange - pitchRange * 0.5
    );
}

bool getLookAngles(out vec2 lookAngles) {
    lookAngles = vec2(0.0);

    bool hasProbe = ivec3(round(texelFetch(InSampler, ivec2(4, CURSOR_PROBE_ROW_Y), 0).rgb * 255.0)) == ACTIVE_MARKER;
    if (!hasProbe) {
        return false;
    }

    vec2 mouseLook = decodeAngles(ivec2(0, CURSOR_PROBE_ROW_Y), ivec2(1, CURSOR_PROBE_ROW_Y), 360.0, 360.0);
    ivec3 mode = ivec3(round(texelFetch(InSampler, ivec2(6, CURSOR_PROBE_ROW_Y), 0).rgb * 255.0));
    if (mode.g == 255) {
        // bundled correction/probe delivery with potentially stale raw overlay angles

        lookAngles = mouseLook;
        return true;
    }
    bool hasLook = ivec3(round(texelFetch(InSampler, ivec2(28, PLAYER_LOOK_ROW_Y), 0).rgb * 255.0)) == ACTIVE_MARKER;
    if (!hasLook) {
        lookAngles = mouseLook;
        return true;
    }

    vec2 rawLook = decodeAngles(ivec2(26, PLAYER_LOOK_ROW_Y), ivec2(27, PLAYER_LOOK_ROW_Y), 1440.0, 180.0);
    vec2 serverLook = decodeAngles(ivec2(2, CURSOR_PROBE_ROW_Y), ivec2(3, CURSOR_PROBE_ROW_Y), 1440.0, 180.0);
    vec2 delta = vec2(mod(rawLook.x - serverLook.x + 720.0, 1440.0) - 720.0,
        rawLook.y - serverLook.y);
    float limit = mode.r == 255 ? 35.0 : 90.0;
    // Keep inward movement responsive at either edge, including during overscroll.
    // Native pitch recentering may still produce artifacts beyond the edge.
    for (int axis = 0; axis < 2; axis++) {
        if (abs(mouseLook[axis]) >= limit - 0.01 && mouseLook[axis] * delta[axis] > 0.0) {
            delta[axis] = 0.0;
        }
    }
    lookAngles = clamp(mouseLook + delta, vec2(-limit), vec2(limit));
    return true;
}

vec2 surfaceCorner(int index) {
    ivec3 a = ivec3(round(texelFetch(InSampler, ivec2(index * 2, 20), 0).rgb * 255.0));
    ivec3 b = ivec3(round(texelFetch(InSampler, ivec2(index * 2 + 1, 20), 0).rgb * 255.0));
    return vec2(a.r * 256 + a.g, a.b * 256 + b.r) / 65535.0 * 8.0 - 4.0;
}

vec4 surfaceCursor(vec2 screen, vec2 look, out float visibility) {
    // unchanged scene outside the projected canvas
    visibility = 1.0;
    // projective mapping of the world plane
    for (int i = 0; i < 4; i++) {
        ivec3 tag = ivec3(round(texelFetch(InSampler, ivec2(i * 2 + 1, 20), 0).rgb * 255.0));
        if (tag.g != 91 || tag.b != 126) return vec4(0.0);
    }
    vec2 p0 = surfaceCorner(0), p1 = surfaceCorner(1);
    vec2 p2 = surfaceCorner(2), p3 = surfaceCorner(3);
    vec2 d1 = p1 - p2, d2 = p3 - p2, sum = p0 - p1 + p2 - p3;
    float denominator = d1.x * d2.y - d2.x * d1.y;
    if (abs(denominator) < 0.000001) return vec4(0.0);
    float g = (sum.x * d2.y - d2.x * sum.y) / denominator;
    float h = (d1.x * sum.y - sum.x * d1.y) / denominator;
    mat3 projection = mat3(vec3(p1 - p0 + g * p1, g),
        vec3(p3 - p0 + h * p3, h), vec3(p0, 1.0));
    if (abs(determinant(projection)) < 0.000001) return vec4(0.0);

    vec2 size = vec2(0.08 / 0.75, 0.08 / 0.5);
    // two cursor-texture pixels of right-edge clearance, matching Surface.cursor
    vec2 cursor = clamp(0.5 + look / 70.0, 0.0, 1.0) * vec2(1.0 - size.x * 2.0 / 32.0, 1.0 - size.y);
    vec3 local = inverse(projection) * vec3(screen, 1.0);
    if (abs(local.z) < 0.000001) return vec4(0.0);
    vec2 uv = local.xy / local.z;
    float surfaceDistance = length((uv - cursor) * vec2(0.75, 0.5));
    float edgeWidth = max(fwidth(surfaceDistance), 0.000001);
    // dark canvas padding, reduced at the bottom
    vec2 padding = vec2(0.0455 / 0.75, 0.085 / 0.5);
    vec2 bottomRightPadding = vec2(padding.x, -0.026 / 0.5);
    if (any(lessThan(uv, -padding)) || any(greaterThan(uv, vec2(1) + bottomRightPadding))) return vec4(0.0);
    visibility = 1.0 - smoothstep(SURFACE_REVEAL_RADIUS - edgeWidth,
        SURFACE_REVEAL_RADIUS + edgeWidth, surfaceDistance);
    vec2 pixel = floor((uv - cursor) / size * 32.0);
    if (any(lessThan(pixel, vec2(0))) || any(greaterThanEqual(pixel, vec2(32)))) return vec4(0.0);
    return texelFetch(CursorSampler, ivec2(pixel), 0);
}

void main() {
    vec2 screenSize = vec2(textureSize(InSampler, 0));
    vec3 sceneColor = texture(InSampler, withoutUiData(InSampler, texCoord)).rgb;

    // fullscreen overlays (emote wheel first, then cursor on top)
    vec2 lookAngles;
    bool surfaceMode = round(texelFetch(InSampler, ivec2(6, CURSOR_PROBE_ROW_Y), 0).r * 255.0) > 254.5;
    if (surfaceMode) {
        float visibility = 1.0;
        vec4 cursor = vec4(0.0);
        if (getLookAngles(lookAngles)) {
            cursor = surfaceCursor(texCoord * 2.0 - 1.0, lookAngles, visibility);
        }
        // cover only the surface canvas, with a circular opening onto the existing scene
        sceneColor = mix(sceneColor * visibility, cursor.rgb, cursor.a);
    }
    if (getLookAngles(lookAngles) && !surfaceMode) {
        float aspect = screenSize.x / screenSize.y;
        bool emoteWheelOpen = round(texelFetch(InSampler, ivec2(5, CURSOR_PROBE_ROW_Y), 0).r * 255.0) > 0.5;
        vec2 cursorNdc = clamp(lookAngles / 90.0, -1.0, 1.0);
        // server reference coordinates mapped to the viewport aspect
        // aspect correction matching EmoteWheel.hoveredSlice

        if (emoteWheelOpen) cursorNdc.x *= EMOTE_WHEEL_CURSOR_ASPECT / aspect;
        vec2 cursorUv = 0.5 + cursorNdc * 0.5;
        ivec3 lobbyData = ivec3(round(texelFetch(InSampler, ivec2(7, CURSOR_PROBE_ROW_Y), 0).rgb * 255.0));
        if (lobbyData.r == 255) {
            vec2 point = vec2(texCoord.x, 1.0 - texCoord.y);
            sceneColor = renderLobby(sceneColor, point, cursorUv, screenSize, lobbyData);
        }

        if (emoteWheelOpen) {
            vec2 cursorOffset = vec2(cursorNdc.x * aspect, -cursorNdc.y);
            int hoveredSlice = getHoveredEmoteSlice(cursorOffset);
            vec2 wheelUv = (texCoord - 0.5) * vec2(aspect, 1.0) / EMOTE_WHEEL_RADIUS;
            vec4 wheel = renderEmoteWheel(wheelUv, hoveredSlice);
            sceneColor *= 0.68;
            sceneColor = mix(sceneColor, wheel.rgb, wheel.a);
        }

        vec2 fragTopLeft = vec2(gl_FragCoord.x, screenSize.y - gl_FragCoord.y);
        vec2 cursorOrigin = cursorUv * screenSize;
        float cursorScale = max(1.0, round(screenSize.y / 540.0));
        vec2 cursorTexel = floor((fragTopLeft - cursorOrigin) / cursorScale);
        if (all(greaterThanEqual(cursorTexel, vec2(0.0))) && all(lessThan(cursorTexel, vec2(32.0)))) {
            vec4 cursorSample = texelFetch(CursorSampler, ivec2(cursorTexel), 0);
            sceneColor = mix(sceneColor, cursorSample.rgb, cursorSample.a);
        }
    }

    fragColor = vec4(clamp(sceneColor, 0.0, 1.0), 1.0);
}
