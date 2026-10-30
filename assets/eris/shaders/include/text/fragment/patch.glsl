#ifdef IS_GUI

if (renderNotification > 1.5) {
    // clip text to the panel
    if (any(lessThan(notificationPos, vec2(0.0))) || any(greaterThanEqual(notificationPos, NOTIFICATION_SIZE))) {
        discard;
    }

    // text shows up once the cell under it has (mostly) popped in
    vec2 cell = floor(notificationPos / NOTIFICATION_CELL);
    if (notificationPop(cell, notificationProgress) < NOTIFICATION_TEXT_POP) {
        discard;
    }

} else if (renderNotification > 0.5) {
    vec2 cell = floor(notificationPos / NOTIFICATION_CELL);
    float pop = notificationPop(cell, notificationProgress);

    // grow the cell from its center in whole gui pixels
    vec2 fromCenter = abs(notificationPos - (cell + 0.5) * NOTIFICATION_CELL);
    float extent = pop * NOTIFICATION_CELL * 0.5;
    if (max(fromCenter.x, fromCenter.y) >= extent) {
        discard;
    }

    vec4 notificationColor = texture(Sampler0, texCoord0);
    if (notificationColor.a < 0.1) {
        discard;
    }

    // freshly popped cells flash white and settle into the tint
    vec3 tint = mix(vec3(1.0), vertexColor.rgb, smoothstep(0.4, 1.0, pop));
    fragColor = vec4(notificationColor.rgb * tint, notificationColor.a) * ColorModulator;
}
#endif
