#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:globals.glsl>
#include <twentyone:mesh.glsl>
// @formatter:on

layout(location = 0)in vec3 Position;
layout(location = 1)in vec4 Color;
layout(location = 2)in vec2 UV0;
layout(location = 3)in ivec2 UV1;
layout(location = 4)in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5)in vec2 UV3;
#endif
layout(location = 6)in vec3 Normal;
uniform sampler2D Sampler0;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
layout(location = 0)out float sphericalVertexDistance;
layout(location = 1)out float cylindricalVertexDistance;
#endif
layout(location = 2)out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3)out vec4 lightMapColor;
layout(location = 4)out vec4 overlayColor;
#endif
layout(location = 5)out vec2 texCoord0;
#ifdef GLINT
layout(location = 6)out vec2 texCoordGlint;
#endif
layout(location = 7)flat out int pbrMark;
layout(location = 8)out vec3 pbrPos;
layout(location = 9)out vec3 pbrNormal;
layout(location = 11)out vec3 meshLocal;
layout(location = 12)out vec2 faceUv;
#define _b - 300
#define _c - 310
#define _d - 500
#define _e 19
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ivec3 N = ivec3(round(Color.rgb * 255.0));
    pbrMark = N.g == 254 && N.b == 1?N.r:0;
    if (N.g == 254 && N.b == 3)pbrMark = -100 - N.r;
    pbrPos = Position;
    pbrNormal = Normal;
    vec2 O = vec2(-1.0);
    meshLocal = vec3(0.0);
    const vec2 P[4] = vec2[](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));
    faceUv = P[gl_VertexIndex % 4];
    Ma(Sampler0, UV0, Position, Normal, Color, gl_VertexIndex % 4, pbrPos, pbrNormal, O, pbrMark, meshLocal);
    if (O.x >= 0.0)gl_Position = ProjMat * ModelViewMat * vec4(pbrPos, 1.0);
    ivec4 Q = ivec4(round(texelFetch(Sampler0, ivec2(UV0 * vec2(textureSize(Sampler0, 0))), 0) * 255.0));
    if (Q.r == 252 && Q.g == 99 && Q.b < 8 && Q.a == 255) {
        const vec2 R[4] = vec2[](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));
        vec2 S = R[3 - gl_VertexIndex % 4];
        gl_Position = vec4((vec2(float(Q.b), float(_e)) + S) / ScreenSize * 2.0 - 1.0, 0.999, 1.0);
        meshLocal = Color.rgb;
        pbrMark = _d;
    }
    if (N.g == 254 && N.b == 6) {
        vec3 T = texelFetch(Sampler0, ivec2(UV0 * vec2(textureSize(Sampler0, 0))), 0).rgb;
        float U = GameTime * 1200.0;
        vec3 V = T * 40.0;
        float X = T.r * 6.2831, Y = sqrt(T.g) * 0.85;
        vec3 Z = vec3(sin(U * 0.11 + V.x) * 0.5 + sin(U * 0.27 + V.y) * 0.3 + sin(U * 0.61 + V.z) * 0.2, sin(U * 0.09 + V.z) * 0.5 + sin(U * 0.23 + V.x) * 0.3 + sin(U * 0.53 + V.y) * 0.2, sin(U * 0.13 + V.y) * 0.5 + sin(U * 0.31 + V.z) * 0.3 + sin(U * 0.57 + V.x) * 0.2) * vec3(0.14, 0.09, 0.14);
        float aa = mod(T.b * 2.3 - U * 0.012, 2.3);
        vec3 ba = vec3(cos(X) * Y, aa + 0.05, sin(X) * Y) + Z;
        vec3 da = Position + ba;
        float ea = step(0.85, fract(T.r * 7.0 + T.g * 3.0));
        float ga = mix(0.0055 + T.g * 0.0035, 0.012, ea);
        vec2 S = vec2(1.0 - faceUv.x * 2.0, faceUv.y * 2.0 - 1.0);
        vec4 ha = ModelViewMat * vec4(da, 1.0) + vec4(S * ga, 0.0, 0.0);
        gl_Position = ProjMat * ha;
        pbrPos = da;
        meshLocal = vec3(aa / 2.3, ea, T.b);
        pbrMark = _b;
    }
    if (N.g == 254 && N.b == 7) {
        vec3 T = texelFetch(Sampler0, ivec2(UV0 * vec2(textureSize(Sampler0, 0))), 0).rgb;
        float U = GameTime * 1200.0;
        float ia = fract(T.g * 7.31);
        vec2 ja;
        if (T.r < 0.85)ja = vec2(T.g < 0.5?-mix(2.8, 4.8, ia):mix(2.4, 4.8, ia), mix(-8.3, 7.7, T.r / 0.85));
        else ja = vec2(mix(-2.0, 1.6, ia), T.g < 0.5?-mix(9.0, 10.5, fract(T.r * 13.7)):mix(8.4, 10.5, fract(T.r * 13.7)));
        float ka = 8.0 + T.b * 3.0;
        float la = 13.0 - mod(T.b * 97.0 + U * ka, 13.5);
        vec3 da = Position + vec3(ja.x, la, ja.y);
        vec3 ma = normalize(cross(vec3(0.0, 1.0, 0.0), -da));
        vec3 S = ma * (1.0 - faceUv.x * 2.0) * 0.008 + vec3(0.0, faceUv.y * 2.0 - 1.0, 0.0) * (0.22 + T.g * 0.12);
        gl_Position = ProjMat * ModelViewMat * vec4(da + S, 1.0);
        pbrPos = da;
        meshLocal = vec3(la / 13.0, T.r, T.b);
        pbrMark = _c;
    }
    #ifndef OIT_ALPHA_ONLY
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
    #endif
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, Color);
    #ifndef OIT_ALPHA_ONLY
    lightMapColor = sample_lightmap(Sampler2, UV2);
    overlayColor = texelFetch(Sampler1, UV1, 0);
    #endif
    texCoord0 = O.x >= 0.0?O:UV0;
    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif
}
