#ifdef IS_GUI
#moj_import < minecraft:globals.glsl >

#define DECEIV_CANVAS_SIZE 256.0
#define DECEIV_CANVAS_SIZE_FULL 512.0

#define DECEIV_HEART_SIZE_HALF 4.5
#define DECEIV_HEART_SIZE 9.0
#define DECEIV_HEART_SIZE_SHADOW 10.0
#define DECEIV_HEART_SIZE_SCALED 18.0
#define DECEIV_HEART_SIZE_FULL 36.0

#define DECEIV_PROGRESS_SIZE 40.0

in vec3 identifier;
flat in int effectGroup;
flat in int effectType;
in float localX;
in float localY;

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
    // left
    float fxl = topologize(tetraNoise(p + e.xy));
    // right
    float fxr = topologize(tetraNoise(p - e.xy));
    // up
    float fyu = topologize(tetraNoise(p + e.yx));
    // down
    float fyd = topologize(tetraNoise(p - e.yx));
    float weight = clamp((max(abs(fxl - fxr), abs(fyu - fyd)) - 0.01) * 12.0, 0.0, 1.0);
    vec3 highlight = vertexColor.xyz * max(drawStripes(p) * 0.5, 0.25);
    return mix(vec4(0, 0, 0, vertexColor.a), vec4(highlight, vertexColor.a), pow(weight, 8));
}
vec4 renderBackground(in vec2 fragCoord) {
    float time = GameTime * 5000.0;
    // wrap position and UV
    vec2 position = vec2(fragCoord.x, -fragCoord.y) / 8.0 + vec2(-time, time);
    vec2 wrappedPosition = vec2(1.0) + mod(position, vec2(16.0));
    vec2 wrappedUV = wrappedPosition / vec2(textureSize(Sampler0, 0));
    return texture(Sampler0, wrappedUV) * vertexColor * ColorModulator;
}
bool isWithinDotRadius(in vec2 fragCoord) {
    float dotSize = 40.0;
    vec2 dotCoord = roundToPixelGrid(fragCoord.xy, 4) / dotSize;
    vec2 cellUV = (floor(dotCoord) + 0.5) * dotSize / ScreenSize.xy;
    // calculate progress based on opacity
    float progress = vertexColor.a * 1.5 - 0.5;
    float diagonal = (cellUV.x + (1.0 - cellUV.y)) / 2.0;
    float waveProgress = (progress - diagonal + 0.5) * 2.0;
    // check dot radius bounds
    return length(fract(dotCoord) - 0.5) * 2.0 >= clamp(waveProgress * 1.5, 0.0, 1.5);
}
vec4 renderProgressBar(vec3 color, float opacity) {
    vec3 fill = texelFetch(Sampler0, ivec2(1, 0), 0).rgb;
    if (opacity < 0.1 || color == identifier || color == fill) {
        discard;
    }
    // protection against different text rendering order
    float currentLocalX = (abs(dFdx(localX)) > abs(dFdx(localY))) ? localX : localY;
    // fill based on progress
    bool isFilled = currentLocalX * DECEIV_CANVAS_SIZE < vertexColor.a * DECEIV_PROGRESS_SIZE;
    return vec4(isFilled ? fill : color, opacity * 0.75);
}
vec4 renderProgressBarReady(vec4 texColor, vec4 color) {
    if (texColor.a < 0.1 || texColor.rgb == identifier) {
        discard;
    }
    float pulse = 0.5 + 0.5 * sin(GameTime * 10000.0);
    return vec4(color.rgb * (0.85 + 0.15 * pulse), color.a * (0.75 + 0.25 * pulse));
}
float getHeartbeatScale(float speed, float base) {
    float time = fract(GameTime * 1200.0 * (speed / 60.0));
    // combine beat waves to simulate realistic heartbeat
    float p = exp(-pow((time - 0.15) * 20.0, 2.0)) * 0.15;
    float q = exp(-pow((time - 0.26) * 50.0, 2.0)) * 0.15;
    float r = exp(-pow((time - 0.30) * 50.0, 2.0)) * 0.5;
    float s = exp(-pow((time - 0.34) * 50.0, 2.0)) * 0.3;
    float t = exp(-pow((time - 0.55) * 15.0, 2.0)) * 0.3;
    // formula: f(x) = 1 + 0.2b * (0.15 * \exp(-((x-0.15)*20)^2) - 0.15 * \exp(-((x-0.26)*50)^2) + 0.5 * \exp(-((x-0.30)*50)^2) - 0.3 * \exp(-((x-0.34)*50)^2) + 0.3 * \exp(-((x-0.55)*15)^2))
    return 1.0 + (p - q + r - s + t) * 0.2 * base;
}
bool isShadowColor(vec4 color) {
    return color.r <= 0.25 && color.g <= 0.25 && color.b <= 0.25;
}
vec4 scaleHeartbeat(float scale, float offset) {
    // calculate local coordinates
    float currentX = abs(dFdx(localX));
    float currentY = abs(dFdx(localY));
    float rawLocalX = (currentX > currentY) ? localX : localY;
    float rawLocalY = (currentX > currentY) ? localY : localX;
    bool invertY = dFdy(rawLocalY) > 0.0;

    // texture bounds and center
    float texMinX = DECEIV_HEART_SIZE_SHADOW / DECEIV_CANVAS_SIZE;
    float texMaxX = (DECEIV_HEART_SIZE_SHADOW + DECEIV_HEART_SIZE) / DECEIV_CANVAS_SIZE;
    float texCenterX = (DECEIV_HEART_SIZE_SHADOW + DECEIV_HEART_SIZE_HALF) / DECEIV_CANVAS_SIZE;
    float texMinY = 0.0;
    float texMaxY = 0.5;
    float texCenterY = 0.25;

    // convert to coords
    float vTexMinY = invertY ? (1.0 - texMaxY) : texMinY;
    float vTexMaxY = invertY ? (1.0 - texMinY) : texMaxY;
    float vTexCenterY = invertY ? (1.0 - texCenterY) : texCenterY;
    float physCenterX = 0.5 + (5.0 / DECEIV_CANVAS_SIZE);
    float physCenterY = 0.5;
    float vPhysCenterY = invertY ? (1.0 - physCenterY) : physCenterY;

    // apply scaling
    vec2 textureLocal = vec2(
        rawLocalX + (texCenterX - physCenterX),
        rawLocalY + (vTexCenterY - vPhysCenterY)
    );
    vec2 scaledTextureLocal = vec2(texCenterX, vTexCenterY) + (textureLocal - vec2(texCenterX, vTexCenterY)) / scale;
    // check bounds
    if (scaledTextureLocal.x < texMinX || scaledTextureLocal.x > texMaxX || scaledTextureLocal.y < vTexMinY || scaledTextureLocal.y > vTexMaxY) {
        discard;
    }
    // shift uv
    float uvX = dFdx(texCoord0.x) / dFdx(rawLocalX);
    float uvY = dFdy(texCoord0.y) / dFdy(rawLocalY);
    vec2 localShift = scaledTextureLocal - vec2(rawLocalX, rawLocalY);
    vec2 uv = texCoord0 + vec2(localShift.x * uvX, localShift.y * uvY);
    // offset for different textures
    uv.x += (abs(uvX) / DECEIV_CANVAS_SIZE) * (offset * DECEIV_HEART_SIZE - DECEIV_HEART_SIZE_SHADOW);

    // check shadow color
    vec4 color = texture(Sampler0, uv);
    vec4 shadowColor = color * vertexColor;
    if (isShadowColor(vertexColor)) {
        color = shadowColor;
    }
    if (color.a < 0.1 || shadowColor.rgb == identifier) {
        discard;
    }
    return color;
}
vec4 renderShutter(in vec2 fragCoord) {
    float distance = length((fragCoord / ScreenSize.xy * 2.0 - 1.0) * vec2(0.75, 1.0));
    float radius = (1.0 - vertexColor.a) * 1.6;
    return vec4(0.0, 0.0, 0.0, smoothstep(radius - 0.3, radius, distance));
}
#endif
