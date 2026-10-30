#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:globals.glsl>
#include <eris:emote_wheel.glsl>
// @formatter:on

uniform sampler2D InSampler;
uniform sampler2D CursorSampler;
uniform sampler2D BloomSampler;
uniform sampler2D AoSampler;

layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;

// Marker and post-processing constants
const int PLAYER_LOOK_ROW_Y = 16;
const int CURSOR_PROBE_ROW_Y = 19;
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
    bool hasLook = ivec3(round(texelFetch(InSampler, ivec2(28, PLAYER_LOOK_ROW_Y), 0).rgb * 255.0)) == ACTIVE_MARKER;
    if (!hasLook) {
        lookAngles = mouseLook;
        return true;
    }

    vec2 rawLook = decodeAngles(ivec2(26, PLAYER_LOOK_ROW_Y), ivec2(27, PLAYER_LOOK_ROW_Y), 1440.0, 180.0);
    vec2 serverLook = decodeAngles(ivec2(2, CURSOR_PROBE_ROW_Y), ivec2(3, CURSOR_PROBE_ROW_Y), 1440.0, 180.0);
    float deltaYaw = mod(mod(rawLook.x - serverLook.x + 720.0, 1440.0) + 1440.0, 1440.0) - 720.0;
    float deltaPitch = rawLook.y - serverLook.y;
    lookAngles = vec2(mouseLook.x + deltaYaw, mouseLook.y + deltaPitch);
    return true;
}

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
    vec2 screenSize = vec2(textureSize(InSampler, 0));
    ivec2 pixelCoord = ivec2(uv * screenSize);
    if (pixelCoord.y >= PLAYER_LOOK_ROW_Y - 2 && pixelCoord.y <= CURSOR_PROBE_ROW_Y + 2 && pixelCoord.x < 48) {
        uv.y += 9.0 / screenSize.y;
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

    // Fullscreen overlays (emote wheel first, then cursor on top)
    vec2 lookAngles;
    if (getLookAngles(lookAngles)) {
        float aspect = screenSize.x / screenSize.y;
        vec2 cursorNdc = clamp(lookAngles / 90.0, -1.0, 1.0);

        bool emoteWheelOpen = round(texelFetch(InSampler, ivec2(5, CURSOR_PROBE_ROW_Y), 0).r * 255.0) > 0.5;
        if (emoteWheelOpen) {
            vec2 cursorOffset = vec2(cursorNdc.x * aspect, -cursorNdc.y);
            int hoveredSlice = getHoveredEmoteSlice(cursorOffset);
            vec2 wheelUv = (texCoord - 0.5) * vec2(aspect, 1.0) / 0.30;
            vec4 wheel = renderEmoteWheel(wheelUv, hoveredSlice);
            sceneColor = mix(sceneColor, wheel.rgb, wheel.a);
        }

        vec2 cursorUv = 0.5 + cursorNdc * 0.5;
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
