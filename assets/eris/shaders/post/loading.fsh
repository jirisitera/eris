/**
 * Automatically generated file, DO NOT EDIT
 * (changes will be lost on next compilation)
 *
 * Effects Compiler created by japicraft
**/

#version 330
#extension GL_ARB_separate_shader_objects : require

#include <eris:post/header.glsl>
#include <eris:ui_data.glsl>
#include <minecraft:globals.glsl>

// attribution credit for shader source:
// https://www.shadertoy.com/view/llXfRl

const float pi = 3.14159265359;
const float triangleScale = 0.816497161855865; // ratio of edge length and height
const vec3 orange = vec3(0.937, 0.435, 0.0);

float rand(vec2 co) {
  return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
}
vec4 getTriangleCoords(vec2 uv) {
  uv.y /= triangleScale;
  uv.x -= uv.y / 2.0;
  vec2 center = floor(uv);
  vec2 local = fract(uv);
  center.x += center.y / 2.0;
  center.y *= triangleScale;
  if (local.x + local.y > 1.0) {
    local.x -= 1.0 - local.y;
    local.y = 1.0 - local.y;
    center.y += 0.586;
    center.x += 1.0;
  } else {
    center.y += 0.287;
    center.x += 0.5;
  }
  return vec4(center, local);
}
vec4 getLoader(vec4 triangle) {
  if (length(triangle.xy) > 1.6) {
    return vec4(0.0);
  }
  float angle = atan(triangle.x, triangle.y);
  float seed = rand(triangle.xy);
  float dst = min(triangle.z, min(triangle.w, 1.0 - triangle.z - triangle.w)) * 15.0;
  float glow = dst < pi ? pow(sin(dst), 1.5) : 0.0;
  return vec4(mix(orange, vec3(1.0), glow * 0.07), pow(0.5 + 0.5 * sin(angle - iTime * 6.0 + seed), 2.0));
}
float getBackground(vec4 triangle) {
  float dst = min(triangle.z, min(triangle.w, 1.0 - triangle.z - triangle.w)) - 0.05;
  if (triangle.y > 1.9 || triangle.y < -2.4 || dst < 0.0) {
    return 0.0;
  }
  float value = pow(0.5 + 0.5 * cos(-abs(triangle.x) * 0.4 + rand(triangle.xy) * 2.0 + iTime * 4.0), 2.0) * 0.08;
  return value * (dst > 0.05 ? 0.65 : 1.0);
}
vec3 getColor(vec2 fragCoord_offset) {
  vec2 texC = (fragCoord_offset + 0.5 * iResolution.xy) / iResolution.xy;
  vec3 gameScreen = texture(InSampler, texC).rgb;

  vec2 uv = fragCoord_offset * 2.0 / iResolution.y;

  vec4 bgTriangle = getTriangleCoords(uv * 6.0 - vec2(0.5, 0.3));

  bool inStrip = bgTriangle.y <= 1.9 && bgTriangle.y >= -2.4;
  float dst = min(bgTriangle.z, min(bgTriangle.w, 1.0 - bgTriangle.z - bgTriangle.w)) - 0.05;
  bool insideTriangle = inStrip && dst >= 0.0;

  float bgIntensity = getBackground(bgTriangle);

  vec3 background = gameScreen + vec3(bgIntensity);

  vec4 loader = getLoader(getTriangleCoords(uv * 11.0));

  vec3 color = mix(background, loader.rgb, loader.a);
  return color;
}
void originalMainImage(out vec4 fragColor, in vec2 fragCoord) {
  // The following cursor pass needs the encoded UI pixels unchanged.
  ivec2 pixel = ivec2(gl_FragCoord.xy);
  if (isUiDataPixel(pixel)) {
    fragColor = texelFetch(InSampler, pixel, 0);
    return;
  }
  fragCoord = fragCoord - 0.5 * iResolution.xy;
  fragColor.rgb = 0.25 * (getColor(fragCoord) + getColor(fragCoord + vec2(0.5, 0.0)) + getColor(fragCoord + vec2(0.5, 0.5)) + getColor(fragCoord + vec2(0.0, 0.5)));
  fragColor.a = 1.0;
}
#include <eris:post/fullscreen.glsl>
