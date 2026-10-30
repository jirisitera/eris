#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <twentyone:pbr.glsl>
#include <twentyone:mesh.glsl>
// @formatter:on

#define _a 6
#define _b - 300
#define _c - 310
#define _d - 500
uniform sampler2D Sampler0;
#ifdef GLINT
uniform sampler2D GlintSampler;
#endif
#ifndef OIT_ALPHA_ONLY
layout(location = 0)in float sphericalVertexDistance;
layout(location = 1)in float cylindricalVertexDistance;
#endif
layout(location = 2)in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3)in vec4 lightMapColor;
layout(location = 4)in vec4 overlayColor;
#endif
layout(location = 5)in vec2 texCoord0;
#ifdef GLINT
layout(location = 6)in vec2 texCoordGlint;
#endif
#ifndef OIT_ALPHA_ONLY
layout(location = 0)out vec4 fragColor;
#endif
layout(location = 7)flat in int pbrMark;
layout(location = 8)in vec3 pbrPos;
layout(location = 9)in vec3 pbrNormal;
layout(location = 11)in vec3 meshLocal;
layout(location = 12)in vec2 faceUv;
#ifndef OIT_ALPHA_ONLY
vec4 j(vec4 A) {
    A.rgb = mix(overlayColor.rgb, A.rgb, overlayColor.a);
    A *= lightMapColor;
    #ifdef GLINT
    vec4 B = GlintAlpha * texture(GlintSampler, texCoordGlint);
    A.rgb += B.rgb * B.rgb;
    #endif
    #ifdef OIT_ACCUMULATE
    A = sampleColorForAccumulation(A);
    vec4 C = vec4(FogColor.rgb * A.a, FogColor.a);
    #else
    vec4 C = FogColor;
    #endif
    return apply_fog(A, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, C);
}
#endif
void main() {
    if (pbrMark == _o || pbrMark == _q) {
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        vec3 D = floor(Gd(Sampler0, texCoord0, meshLocal, GameTime, pbrMark == _q) * 254.0);
        D = vec3(D.r - mod(D.r, 2.0) + 1.0, D.g - mod(D.g, 2.0), D.b - mod(D.b, 2.0) + 1.0);
        fragColor = vec4(D / 255.0, 1.0);
        #endif
        return;
    }
    if (pbrMark == _d) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        fragColor = vec4(meshLocal, 1.0);
        return;
        #endif
    }
    if (pbrMark == _c) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        float E = smoothstep(0.0, 0.1, meshLocal.x) * smoothstep(1.0, 0.85, meshLocal.x) * (1.0 - abs(faceUv.y * 2.0 - 1.0) * 0.6);
        vec3 F = vec3(0.05, 0.056, 0.07) * (0.6 + 0.4 * meshLocal.z) + _k * Ca(GameTime) * 0.7;
        fragColor = vec4(F * E, 1.0);
        return;
        #endif
    }
    if (pbrMark == _b) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        vec2 D = faceUv * 2.0 - 1.0;
        float G = 0.75 + 0.25 * sin(atan(D.y, D.x) * 3.0 + meshLocal.z * 40.0);
        float I = length(D) / G;
        if (I > 1.0)discard;
        vec3 J = pbrPos + jb();
        float K = smoothstep(Ic[0].x, Ic[0].y, dot(normalize(J - Fc[0]), Gc[0])) * fc(GameTime);
        K *= smoothstep(0.0, 0.12, meshLocal.x) * smoothstep(1.0, 0.85, meshLocal.x);
        if (K < 0.03)discard;
        float L = 0.55 + 0.45 * sin(GameTime * 3600.0 * (0.6 + meshLocal.z) + meshLocal.z * 90.0);
        float M = meshLocal.y > 0.5?0.45:1.3 * (1.1 - I * I * 0.5);
        fragColor = vec4(min(vec3(1.0, 0.92, 0.78) * K * L * M, 1.0), 1.0);
        return;
        #endif
    }
    if (pbrMark <= -100) {
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        fragColor = vec4(clamp(ta(-100 - pbrMark, GameTime) * 1.6, 0.0, 1.0), 1.0);
        #endif
        return;
    }
    if (pbrMark == _a) {
        if (texture(Sampler0, texCoord0).a < 0.5)discard;
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        fragColor = vec4(Bc(Sampler0, texCoord0, pbrPos, normalize(pbrNormal), faceUv), 1.0);
        #endif
        return;
    }
    if (pbrMark == _p) {
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        fragColor = vec4(Ec(Sampler0, texCoord0, pbrPos, normalize(pbrNormal)), 1.0);
        #endif
        return;
    }
    if (pbrMark > 0) {
        if (texture(Sampler0, texCoord0).a < 0.5)discard;
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        fragColor = vec4(wc(Sampler0, texCoord0, exp2(float(pbrMark)), pbrPos, normalize(pbrNormal)), 1.0);
        #endif
        return;
    }
    vec4 A = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (A.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    A *= vertexColor * ColorModulator;
    #ifdef GLINT
    A.a = max(A.a, GlintAlpha);
    #endif
    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, A.a);
    #else
    fragColor = j(A);
    #endif
}
