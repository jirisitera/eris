#ifdef IS_GUI
// check for id pixel at 0,0
vec3 identifier = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;

renderNotification = identifier == NOTIFICATION_ID ? 1.0 : 0.0;

// notification glyphs are marked by the low 2 bits of their color (cursor shadow is raw data, so skip it)
ivec3 marker = ivec3(Color.rgb * 255.0 + 0.5) & 3;

if (marker == NOTIFICATION_HIDE) {
    // keep title centered
    gl_Position = vec4(10.0, 10.0, 0.0, 1.0);
    renderNotification = 0.0;

} else if (renderNotification > 0.5) {
    vec2 textureSize = vec2(textureSize(Sampler0, 0));
    vec2 currentPixel = UV0 * textureSize;

    // determine vertex corner
    bool isLeft = currentPixel.x < 128.0;
    bool isTop = currentPixel.y < 128.0;

    // corner position in gui pixels, relative to the panel
    vec2 corner = vec2(isLeft ? 0.0 : NOTIFICATION_SIZE.x, isTop ? 0.0 : NOTIFICATION_SIZE.y);

    // ProjMat maps gui pixels to clip space
    gl_Position.xy = (ProjMat * vec4(NOTIFICATION_MARGIN + corner, 0.0, 1.0)).xy;

    // fragment shader builds the panel cell by cell from these
    notificationPos = corner;
    notificationProgress = Color.a;

    // map to notification texture (from pixel 1,1 to 7,7), tinted by text color
    // alpha is kept opaque so text.fsh doesn't discard it, the fade is handled by the build instead
    texCoord0 = (vec2(1.0, 1.0) + corner) / textureSize;
    vertexColor = vec4(Color.rgb, 1.0);

} else if (marker == NOTIFICATION_TEXT) {
    renderNotification = 2.0;

    // position relative to the screen center in gui pixels (center is 0 in clip space)
    vec2 fromCenter = gl_Position.xy / vec2(ProjMat[0][0], ProjMat[1][1]);

    // the title is balanced so this text starts exactly at the center, so undoing the title scale gives its local position
    // +0.25 absorbs vanilla's integer division of the screen center
    vec2 local = floor(fromCenter / TITLE_SCALE - vec2(0.0, TITLE_Y) + 0.25);

    notificationPos = NOTIFICATION_TEXT_OFFSET + local;
    notificationProgress = Color.a;

    gl_Position.xy = (ProjMat * vec4(NOTIFICATION_MARGIN + notificationPos, 0.0, 1.0)).xy;
    vertexColor = vec4(Color.rgb, 1.0);
}

#endif
