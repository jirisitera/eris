const float EMOTE_WHEEL_PI = 3.14159265;
const float EMOTE_WHEEL_DEADZONE = 0.12;

int getEmoteWheelSlice(float angle) {
    // Shift by half a slice (pi/8) so slice 0 is centered at 12 o'clock
    float shifted = mod(angle + 2.0 * EMOTE_WHEEL_PI + EMOTE_WHEEL_PI / 8.0, 2.0 * EMOTE_WHEEL_PI);
    return int(floor(shifted / (EMOTE_WHEEL_PI / 4.0))) % 8;
}

int getHoveredEmoteSlice(vec2 cursorOffset) {
    if (length(cursorOffset) < EMOTE_WHEEL_DEADZONE) {
        return -1;
    }
    return getEmoteWheelSlice(atan(cursorOffset.x, cursorOffset.y));
}

float roundedWedgeSDF(float dist, float innerRadius, float outerRadius, float dividerDist, float gapHalfWidth, float cornerRadius) {
    float radialDist = max(dist - outerRadius, innerRadius - dist) + cornerRadius;
    float sideDist = (gapHalfWidth - dividerDist) + cornerRadius;
    return length(max(vec2(radialDist, sideDist), 0.0)) + min(max(radialDist, sideDist), 0.0) - cornerRadius;
}

vec4 renderEmoteWheel(vec2 uv, int selectedSlice) {
    float dist = length(uv);
    float aa = max(fwidth(dist), 0.002);

    if (dist > 1.0) {
        return vec4(0.0);
    }

    // Angle clockwise from top (+Y is up in centered screen UV)
    float angle = atan(uv.x, uv.y);
    int slice = getEmoteWheelSlice(angle);
    bool hovered = (slice == selectedSlice);

    float innerRadius = hovered ? 0.34 : 0.36;
    float outerRadius = hovered ? 0.93 : 0.87;
    float sliceCenterAngle = float(slice) * (EMOTE_WHEEL_PI / 4.0);
    float angleFromCenter = abs(mod(angle - sliceCenterAngle + EMOTE_WHEEL_PI, 2.0 * EMOTE_WHEEL_PI) - EMOTE_WHEEL_PI);
    float dividerDist = dist * sin(max((EMOTE_WHEEL_PI / 8.0) - angleFromCenter, 0.0));

    float wedgeSDF = roundedWedgeSDF(dist, innerRadius, outerRadius, dividerDist, 0.020, 0.032);

    vec3 color = vec3(0.03, 0.04, 0.06);
    float alpha = smoothstep(0.98, 0.30, dist) * 0.22;

    // Center hub disc and ring
    float hubOuter = 0.255;
    float hubInner = 0.220;
    float hubDiscMask = 1.0 - smoothstep(-aa, aa, dist - hubOuter);
    color = mix(color, vec3(0.07, 0.09, 0.13), hubDiscMask);
    alpha = max(alpha, hubDiscMask * 0.78);

    float hubRingSDF = abs(dist - 0.5 * (hubOuter + hubInner)) - 0.5 * (hubOuter - hubInner);
    float hubRingMask = 1.0 - smoothstep(-aa, aa, hubRingSDF);
    vec3 hubRingColor = vec3(0.28, 0.33, 0.44);
    if (selectedSlice >= 0) {
        float selectedAngle = float(selectedSlice) * (EMOTE_WHEEL_PI / 4.0);
        float angleToSelected = abs(mod(angle - selectedAngle + EMOTE_WHEEL_PI, 2.0 * EMOTE_WHEEL_PI) - EMOTE_WHEEL_PI);
        float indicatorArc = 1.0 - smoothstep(EMOTE_WHEEL_PI / 9.0, EMOTE_WHEEL_PI / 7.0, angleToSelected);
        hubRingColor = mix(hubRingColor, vec3(0.45, 0.82, 1.0), indicatorArc);
    }
    color = mix(color, hubRingColor, hubRingMask);
    alpha = max(alpha, hubRingMask * 0.92);

    // Radial wedges
    float wedgeMask = 1.0 - smoothstep(-aa, aa, wedgeSDF);
    if (wedgeMask > 0.001) {
        float radialT = clamp((dist - innerRadius) / (outerRadius - innerRadius), 0.0, 1.0);
        vec3 baseFill = mix(vec3(0.09, 0.11, 0.16), vec3(0.14, 0.17, 0.24), radialT);
        vec3 hoverFill = mix(vec3(0.18, 0.42, 0.68), vec3(0.32, 0.68, 0.96), 1.0 - radialT * 0.5);
        vec3 fill = hovered ? hoverFill : baseFill;

        vec2 slotCenter = vec2(sin(sliceCenterAngle), cos(sliceCenterAngle)) * (0.5 * (innerRadius + outerRadius));
        float slotDist = length(uv - slotCenter);
        float slotDiscMask = 1.0 - smoothstep(-aa, aa, slotDist - 0.105);
        float slotRingMask = 1.0 - smoothstep(-aa, aa, abs(slotDist - 0.105) - 0.008);
        fill = mix(fill, hovered ? vec3(0.12, 0.28, 0.48) : vec3(0.06, 0.08, 0.12), slotDiscMask * 0.45);
        fill = mix(fill, hovered ? vec3(0.75, 0.92, 1.0) : vec3(0.24, 0.29, 0.39), slotRingMask * 0.65);

        float borderThickness = hovered ? 0.018 : 0.013;
        float borderMask = 1.0 - smoothstep(-aa, aa, abs(wedgeSDF + borderThickness * 0.5) - borderThickness * 0.5);
        vec3 borderColor = hovered ? vec3(0.88, 0.96, 1.0) : vec3(0.26, 0.31, 0.42);

        color = mix(color, mix(fill, borderColor, borderMask), wedgeMask);
        alpha = mix(alpha, hovered ? 0.94 : 0.82, wedgeMask);
    }

    return vec4(color, alpha);
}
