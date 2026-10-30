#elif defined(IS_GUI)
switch (effectGroup) {
case 1:
// transitions
switch (effectType) {
case 1:
if (isWithinDotRadius(gl_FragCoord.xy) || color.a == 0.0) {
    discard;
}
fragColor = renderWorldEffect(gl_FragCoord.xy);
break;
case 2:
if (isWithinDotRadius(gl_FragCoord.xy) || color.a == 0.0) {
    discard;
}
fragColor = renderBackground(gl_FragCoord.xy);
break;
case 3:
fragColor = renderShutter(gl_FragCoord.xy);
break;
}
break;
case 2:
// moods
switch (effectType) {
case 1:
// calm
fragColor = scaleHeartbeat(getHeartbeatScale(40.0, 1.0), 0.0);
break;
case 2:
// nervous
fragColor = scaleHeartbeat(getHeartbeatScale(60.0, 1.05), 0.0);
break;
case 3:
// terrified
fragColor = scaleHeartbeat(getHeartbeatScale(120.0, 1.25), 0.0);
break;
case 4:
// poisoned
fragColor = scaleHeartbeat(getHeartbeatScale(60.0, 1.25), 2.0);
break;
}
break;
case 3:
// progress bars
switch (effectType) {
case 1:
fragColor = renderProgressBar(color.rgb, texColor.a * ColorModulator.a);
break;
case 2:
fragColor = renderProgressBarReady(texColor, color);
break;
}
break;
default:
if (color.a < 0.1) {
    discard;
}
fragColor = color;
}
