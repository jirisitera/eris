#ifdef IS_GUI
int corner = gl_VertexID % 4;
localX = (corner == 2 || corner == 3) ? 1.0 : 0.0;
localY = (corner == 1 || corner == 2) ? 1.0 : 0.0;
// get identification pixel
identifier = texelFetch(Sampler0, ivec2(0, 0), 0).rgb;
if (identifier == vec3(1.0)) {
    identifier = Color.rgb;
    if (abs(identifier.r - (DECEIV_IDENTIFIER * 0.25)) < 0.01) {
        identifier *= 4.0;
    }
}
// determine effect
if (abs(identifier.r - DECEIV_IDENTIFIER) < 0.01) {
    effectGroup = int(round(identifier.g * 255.0)) / 4;
    effectType = int(round(identifier.b * 255.0)) / 4;
} else {
    effectGroup = 0;
    effectType = 0;
}
// vertex changes for seperate effect groups
switch (effectGroup) {
case 1:
// transitions
gl_Position = vec4(2.0 * vec2(UV0.x, 1.0 - UV0.y) - 1.0, 1.0, 1.0);
break;
case 2:
// moods
gl_Position = ProjMat * ModelViewMat * vec4(Position.xy - vec2(DECEIV_HEART_SIZE_SHADOW), Position.z, 1.0);
break;
}
#endif
