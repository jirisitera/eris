#ifndef ERIS_TIMER
#define ERIS_TIMER

// Case timer screen pixels are written by item.fsh with R = 250 and a 16-bit payload in G/B.
// Pixels are interleaved in a checkerboard:
//   - even pixels ((x + y) % 2 == 0) pack (u10 << 6) | seconds
//   - odd pixels  ((x + y) % 2 == 1) pack (v8  << 8) | (strikes << 6) | minutes
// Any direct neighbour has the opposite parity, so one neighbour fetch recovers the screen UV,
// the MM:SS timer readout, and the active strike count (0..3) without off-screen metadata rows.

bool readTimerPixel(sampler2D tex, ivec2 pixel, out int payload) {
    ivec3 raw = ivec3(round(texelFetch(tex, pixel, 0).rgb * 255.0));
    payload = raw.g * 256 + raw.b;
    return raw.r == 250;
}

bool decodeTimer(sampler2D tex, ivec2 pixel, out vec2 uv, out int minutes, out int seconds, out int strikes) {
    uv = vec2(0.0);
    minutes = 0;
    seconds = 0;
    strikes = 0;

    int own;
    if (!readTimerPixel(tex, pixel, own)) {
        return false;
    }

    ivec2 maxPixel = textureSize(tex, 0) - 1;
    const ivec2 NEIGHBOURS[4] = ivec2[](ivec2(1, 0), ivec2(-1, 0), ivec2(0, 1), ivec2(0, -1));
    int other = -1;
    for (int i = 0; i < 4; i++) {
        ivec2 neighbour = pixel + NEIGHBOURS[i];
        if (any(lessThan(neighbour, ivec2(0))) || any(greaterThan(neighbour, maxPixel))) {
            continue;
        }
        int value;
        if (readTimerPixel(tex, neighbour, value)) {
            other = value;
            break;
        }
    }
    if (other < 0) {
        return false;
    }

    bool ownIsEven = ((pixel.x + pixel.y) & 1) == 0;
    int evenWord = ownIsEven ? own : other;
    int oddWord = ownIsEven ? other : own;

    uv = vec2(float(evenWord >> 6) / 1023.0, float(oddWord >> 8) / 255.0);
    strikes = clamp((oddWord >> 6) & 3, 0, 3);
    minutes = clamp(oddWord & 63, 0, 63);
    seconds = clamp(evenWord & 63, 0, 59);
    return true;
}

#endif
