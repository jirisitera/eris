#ifndef ERIS_UI_DATA_GLSL
#define ERIS_UI_DATA_GLSL

// Raw underwater mouse angles, offhand flags, and world-surface projection corners.
// Intermediate post effects preserve this strip; the final cursor pass conceals it.
bool isUiDataPixel(ivec2 pixel) {
    return pixel.x >= 0 && pixel.x < 48 && pixel.y >= 14 && pixel.y <= 21;
}

vec2 withoutUiData(sampler2D image, vec2 uv) {
    vec2 size = vec2(textureSize(image, 0));
    uv = clamp(uv, 0.5 / size, 1.0 - 0.5 / size);
    if (isUiDataPixel(ivec2(uv * size))) uv.y += 9.0 / size.y;
    return clamp(uv, 0.5 / size, 1.0 - 0.5 / size);
}

#endif
