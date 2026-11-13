#ifdef IS_GUI

#define NOTIFICATION_ID vec3(127.0, 216.0, 42.0) / 255.0

// @formatter:off
#include <minecraft:globals.glsl>
#include <eris:text/notification.glsl>
// @formatter:on

uniform sampler2D Sampler0;

layout(location = 6) out float renderNotification;
layout(location = 7) out vec2 notificationPos;
layout(location = 8) out float notificationProgress;
layout(location = 9) flat out vec3 effectIdentifier;
layout(location = 10) flat out int effectGroup;
layout(location = 11) flat out int effectType;
layout(location = 12) out float localX;
layout(location = 13) out float localY;

vec4 applyTextEffectVertex(vec4 position) {
    // font marker at (0, 0): R=204, G=group*4, B=type*4
    int cornerIndex = gl_VertexIndex % 4;
    localX = (cornerIndex == 2 || cornerIndex == 3) ? 1.0 : 0.0;
    localY = (cornerIndex == 1 || cornerIndex == 2) ? 1.0 : 0.0;
    effectIdentifier = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;
    // color-based effects on white atlases, including vanilla shadows
    if (effectIdentifier == vec3(1.0)) {
        effectIdentifier = Color.rgb;
        if (abs(effectIdentifier.r - 0.2) < 0.01) effectIdentifier *= 4.0;
    }
    effectGroup = 0;
    effectType = 0;
    if (abs(effectIdentifier.r - 0.8) < 0.01) {
        int group = int(round(effectIdentifier.g * 255.0)) / 4;
        int type = int(round(effectIdentifier.b * 255.0)) / 4;
        if ((group == 1 && type >= 1 && type <= 3) || (group == 3 && type >= 1 && type <= 2)) {
            effectGroup = group;
            effectType = type;
        }
    }
    if (effectGroup == 1) {
        position = vec4(2.0 * vec2(UV0.x, 1.0 - UV0.y) - 1.0, 1.0, 1.0);
    }
    return position;
}

vec4 applyNotificationVertex(vec4 position) {

    vec3 identifier = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;

    renderNotification = identifier == NOTIFICATION_ID ? 1.0 : 0.0;

    // notification markers in the low two color bits
    ivec3 marker = ivec3(Color.rgb * 255.0 + 0.5) & 3;

    if (effectGroup != 0) {
        renderNotification = 0.0;
    } else if (marker == NOTIFICATION_HIDE) {

        position = vec4(10.0, 10.0, 0.0, 1.0);
        renderNotification = 0.0;

    } else if (renderNotification > 0.5) {
        vec2 textureSize = vec2(textureSize(Sampler0, 0));
        vec2 currentPixel = UV0 * textureSize;

        bool isLeft = currentPixel.x < 128.0;
        bool isTop = currentPixel.y < 128.0;

        // corner position in gui pixels, relative to the panel
        vec2 corner = vec2(isLeft ? 0.0 : NOTIFICATION_SIZE.x, isTop ? 0.0 : NOTIFICATION_SIZE.y);

        position.xy = (ProjMat * vec4(NOTIFICATION_MARGIN + corner, 0.0, 1.0)).xy;

        notificationPos = corner;
        notificationProgress = Color.a;

        // notification texture from pixel (1, 1), tinted by text color
        // opaque alpha through the text cutoff, with fade handled by cell growth
        texCoord0 = (vec2(1.0, 1.0) + corner) / textureSize;
        vertexColor = vec4(Color.rgb, 1.0);

    } else if (marker == NOTIFICATION_TEXT) {
        renderNotification = 2.0;

        // position relative to the screen center in gui pixels (center is 0 in clip space)
        vec2 fromCenter = position.xy / vec2(ProjMat[0][0], ProjMat[1][1]);

        // local text position from centered title coordinates with title scale removed
        // quarter-pixel compensation for vanilla's integer-divided screen center
        vec2 local = floor(fromCenter / TITLE_SCALE - vec2(0.0, TITLE_Y) + 0.25);

        notificationPos = NOTIFICATION_TEXT_OFFSET + local;
        notificationProgress = Color.a;

        position.xy = (ProjMat * vec4(NOTIFICATION_MARGIN + notificationPos, 0.0, 1.0)).xy;
        vertexColor = vec4(Color.rgb, 1.0);
    }
    return position;
}
#endif
