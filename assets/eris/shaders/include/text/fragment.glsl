#ifdef IS_GUI

#define TEXT_CANVAS_SIZE 256.0
#define TEXT_PROGRESS_SIZE 40.0

// @formatter:off
#include <minecraft:globals.glsl>
#include <eris:text/notification.glsl>
// @formatter:on

layout(location = 6) in float renderNotification;
layout(location = 7) in vec2 notificationPos;
layout(location = 8) in float notificationProgress;
layout(location = 9) flat in vec3 effectIdentifier;
layout(location = 10) flat in int effectGroup;
layout(location = 11) flat in int effectType;
layout(location = 12) in float localX;
layout(location = 13) in float localY;

vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(227.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(333.5, 4444271.9, 6624.6)));
    return -1.0 + 2.2 * fract(sin(p) * 43758.5453123);
}

float tetraNoise(vec2 o) {
    vec3 p = vec3(o.x + GameTime * 20.0, o.y + GameTime * 10.0, GameTime * 10.0);
    vec3 i = floor(p + dot(p, vec3(0.33333, 0.33333, 0.33333)));
    p -= i - dot(i, vec3(0.16666, 0.16666, 0.16666));
    vec3 i1 = step(p.yzx, p);
    vec3 i2 = max(i1, 1.0 - i1.zxy);
    i1 = min(i1, 1.0 - i1.zxy);
    vec3 p1 = p - i1 + 0.16666;
    vec3 p2 = p - i2 + 0.33333;
    vec3 p3 = p - 0.5;
    vec4 v = max(0.5 - vec4(dot(p, p), dot(p1, p1), dot(p2, p2), dot(p3, p3)), 0.0);
    vec4 d = vec4(dot(p, hash33(i)), dot(p1, hash33(i + i1)), dot(p2, hash33(i + i2)), dot(p3, hash33(i + 1.0)));
    float n = clamp(dot(d, v * v * v * 8.0) * 1.732 + 0.5, 0.0, 1.0);
    return n;
}

vec2 roundToPixelGrid(vec2 p, int size) {
    return vec2(ivec2(p) / size * size + size / 2);
}

float topologize(float noise) {
    float smoothFloor = noise * 12.0;
    vec2 fracU = vec2(smoothFloor, fwidth(smoothFloor) * 1.3);
    fracU.x = fract(fracU.x);
    fracU += (1.0 - 2.0 * fracU) * step(fracU.y, fracU.x);
    smoothFloor = smoothFloor - clamp(1.0 - fracU.x / fracU.y, 0.0, 1.0);
    return noise * 1.25 + smoothFloor * 0.95 / 9.0;
}

float drawStripes(in vec2 uv) {
    vec2 p = uv + vec2(GameTime * 10.0, GameTime * 450.0);
    float wave = fract((p.x - p.y) * 0.5);
    return smoothstep(0.0, 0.5, wave) - smoothstep(1.0 - 0.5, 1.0, wave);
}

vec4 renderWorldEffect(in vec2 fragCoord) {
    vec2 p = roundToPixelGrid(fragCoord, 4) * vec2(ScreenSize.x / ScreenSize.y, 1.0);
    p = p / ScreenSize;
    p *= 2.5;
    vec2 e = vec2(15.0 / (ScreenSize.y + ScreenSize.x), 0.0);

    float fxl = topologize(tetraNoise(p + e.xy));

    float fxr = topologize(tetraNoise(p - e.xy));

    float fyu = topologize(tetraNoise(p + e.yx));

    float fyd = topologize(tetraNoise(p - e.yx));
    float weight = clamp((max(abs(fxl - fxr), abs(fyu - fyd)) - 0.01) * 12.0, 0.0, 1.0);
    vec3 highlight = vertexColor.xyz * max(drawStripes(p) * 0.5, 0.25);
    return mix(vec4(0, 0, 0, vertexColor.a), vec4(highlight, vertexColor.a), pow(weight, 8));
}

vec4 renderBackground(in vec2 fragCoord) {
    float time = GameTime * 5000.0;

    vec2 position = vec2(fragCoord.x, -fragCoord.y) / 8.0 + vec2(-time, time);
    vec2 wrappedPosition = vec2(1.0) + mod(position, vec2(16.0));
    vec2 wrappedUV = wrappedPosition / vec2(textureSize(Sampler0, 0));
    return texture(Sampler0, wrappedUV) * vertexColor * ColorModulator;
}

bool isWithinDotRadius(in vec2 fragCoord) {
    float dotSize = 40.0;
    vec2 dotCoord = roundToPixelGrid(fragCoord.xy, 4) / dotSize;
    vec2 cellUV = (floor(dotCoord) + 0.5) * dotSize / ScreenSize.xy;

    float progress = vertexColor.a * 1.5 - 0.5;
    float diagonal = (cellUV.x + (1.0 - cellUV.y)) / 2.0;
    float waveProgress = (progress - diagonal + 0.5) * 2.0;

    return length(fract(dotCoord) - 0.5) * 2.0 >= clamp(waveProgress * 1.5, 0.0, 1.5);
}

vec4 renderProgressBar(vec3 color, vec3 sourceColor, float opacity) {
    vec3 fill = texelFetch(Sampler0, ivec2(1, 0), 0).rgb;
    if (opacity < 0.1 || sourceColor == effectIdentifier || sourceColor == fill) {
        discard;
    }
    // axis selection independent of vertex order
    float currentLocalX = (abs(dFdx(localX)) > abs(dFdx(localY))) ? localX : localY;

    bool isFilled = currentLocalX * TEXT_CANVAS_SIZE < vertexColor.a * TEXT_PROGRESS_SIZE;
    return vec4(isFilled ? fill * vertexColor.rgb * ColorModulator.rgb : color, opacity * 0.75);
}

vec4 renderProgressBarReady(vec4 texColor, vec4 color) {
    if (texColor.a < 0.1 || texColor.rgb == effectIdentifier) {
        discard;
    }
    float pulse = 0.5 + 0.5 * sin(GameTime * 10000.0);
    return vec4(color.rgb * (0.85 + 0.15 * pulse), color.a * (0.75 + 0.25 * pulse));
}

vec4 renderShutter(in vec2 fragCoord) {
    float distance = length((fragCoord / ScreenSize.xy * 2.0 - 1.0) * vec2(0.75, 1.0));
    float radius = (1.0 - vertexColor.a) * 1.6;
    return vec4(0.0, 0.0, 0.0, smoothstep(radius - 0.3, radius, distance));
}

vec4 applyNotificationFragment(vec4 color) {
    if (renderNotification > 1.5) {

        if (any(lessThan(notificationPos, vec2(0.0))) || any(greaterThanEqual(notificationPos, NOTIFICATION_SIZE))) {
            discard;
        }

        // text visibility after the underlying cell has mostly appeared
        vec2 cell = floor(notificationPos / NOTIFICATION_CELL);
        if (notificationPop(cell, notificationProgress) < NOTIFICATION_TEXT_POP) {
            discard;
        }

    } else if (renderNotification > 0.5) {
        vec2 cell = floor(notificationPos / NOTIFICATION_CELL);
        float pop = notificationPop(cell, notificationProgress);

        // cell growth from the center in whole GUI pixels
        vec2 fromCenter = abs(notificationPos - (cell + 0.5) * NOTIFICATION_CELL);
        float extent = pop * NOTIFICATION_CELL * 0.5;
        if (max(fromCenter.x, fromCenter.y) >= extent) {
            discard;
        }

        vec4 notificationColor = texture(Sampler0, texCoord0);
        if (notificationColor.a < 0.1) {
            discard;
        }

        // white flash settling into the tint for newly revealed cells
        vec3 tint = mix(vec3(1.0), vertexColor.rgb, smoothstep(0.4, 1.0, pop));
        color = vec4(notificationColor.rgb * tint, notificationColor.a) * ColorModulator;
    }
    return color;
}

vec4 applyTextEffectFragment(vec4 texColor, vec4 color) {
    if (effectGroup == 1) {
        if (effectType == 3) {
            color = renderShutter(gl_FragCoord.xy);
        } else {
            if (isWithinDotRadius(gl_FragCoord.xy) || texColor.a * ColorModulator.a == 0.0) discard;
            color = effectType == 1 ? renderWorldEffect(gl_FragCoord.xy) : renderBackground(gl_FragCoord.xy);
        }
    } else if (effectGroup == 3) {
        if (effectType == 1) {
            color = renderProgressBar(color.rgb, texColor.rgb, texColor.a * ColorModulator.a);
        } else {
            color = renderProgressBarReady(texColor, color);
        }
    }
    return color;
}
#endif
