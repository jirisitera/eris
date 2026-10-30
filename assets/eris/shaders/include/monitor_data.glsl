#ifndef ERIS_MONITOR_DATA
#define ERIS_MONITOR_DATA

// Monitor screen pixels are written by item.fsh as (250 + monitorIndex, hi, lo), where hi/lo form a
// 16-bit screen coordinate. Pixels are interleaved in a checkerboard: even pixels ((x + y) % 2 == 0)
// store U, odd pixels store V. Any direct neighbour has the other parity, so one extra fetch recovers
// the full UV at 16-bit precision (8 bits per pixel would make text and scanlines visibly blocky).

const float MONITOR_SCREEN_CURVE = 0.06;

bool readMonitorPixel(sampler2D tex, ivec2 pixel, out int monitorIndex, out float value) {
    ivec3 raw = ivec3(round(texelFetch(tex, pixel, 0).rgb * 255.0));
    monitorIndex = raw.r - 250;
    value = float(raw.g * 256 + raw.b) / 65535.0;
    return raw.r >= 250 && raw.r <= 252;
}

// Returns the monitor index at the given pixel (or -1) and its screen UV (0..1, y pointing down).
int decodeMonitor(sampler2D tex, ivec2 pixel, out vec2 uv) {
    uv = vec2(0.0);
    int monitorIndex;
    float own;
    if (!readMonitorPixel(tex, pixel, monitorIndex, own)) {
        return -1;
    }
    ivec2 maxPixel = textureSize(tex, 0) - 1;
    const ivec2 NEIGHBOURS[4] = ivec2[](ivec2(1, 0), ivec2(-1, 0), ivec2(0, 1), ivec2(0, -1));
    float other = 0.5;
    for (int i = 0; i < 4; i++) {
        ivec2 neighbour = pixel + NEIGHBOURS[i];
        if (any(lessThan(neighbour, ivec2(0))) || any(greaterThan(neighbour, maxPixel))) continue;
        int neighbourIndex;
        float value;
        if (readMonitorPixel(tex, neighbour, neighbourIndex, value) && neighbourIndex == monitorIndex) {
            other = value;
            break;
        }
    }
    uv = ((pixel.x + pixel.y) & 1) == 0 ? vec2(own, other) : vec2(other, own);
    return monitorIndex;
}

// CRT barrel distortion. Returns false outside of the curved tube (bezel corners).
bool curveMonitorUv(vec2 uv, out vec2 curved, out vec2 centered) {
    centered = uv * 2.0 - 1.0;
    centered *= 1.0 + MONITOR_SCREEN_CURVE * dot(centered, centered);
    curved = centered * 0.5 + 0.5;
    return abs(centered.x) <= 1.0 && abs(centered.y) <= 1.0;
}

#endif
