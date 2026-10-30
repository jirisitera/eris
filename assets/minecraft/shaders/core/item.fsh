#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:globals.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:oit.glsl>
#include <eris:pbr.glsl>
// @formatter:on

const int PBR_MARK_MONITOR_DATA = -500;
const int PBR_MARK_DUST_PARTICLES = -300;

uniform sampler2D Sampler0;
#ifdef GLINT
uniform sampler2D GlintSampler;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) in float sphericalVertexDistance;
layout(location = 1) in float cylindricalVertexDistance;
#endif

layout(location = 2) in vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) in vec4 lightMapColor;
layout(location = 4) in vec4 overlayColor;
#endif
layout(location = 5) in vec2 texCoord0;
#ifdef GLINT
layout(location = 6) in vec2 texCoordGlint;
#endif

#ifndef OIT_ALPHA_ONLY
layout(location = 0) out vec4 fragColor;
#endif

layout(location = 7) flat in int pbrMark;
layout(location = 8) in vec3 pbrPos;
layout(location = 9) in vec3 pbrNormal;
layout(location = 11) in vec3 meshLocal;
layout(location = 12) in vec2 faceUv;

#ifndef OIT_ALPHA_ONLY
vec4 applyVanillaFogAndLight(vec4 baseColor) {
    baseColor.rgb = mix(overlayColor.rgb, baseColor.rgb, overlayColor.a);
    baseColor *= lightMapColor;
    #ifdef GLINT
    vec4 glintColor = GlintAlpha * texture(GlintSampler, texCoordGlint);
    baseColor.rgb += glintColor.rgb * glintColor.rgb;
    #endif
    #ifdef OIT_ACCUMULATE
    baseColor = sampleColorForAccumulation(baseColor);
    vec4 fogColor = vec4(FogColor.rgb * baseColor.a, FogColor.a);
    #else
    vec4 fogColor = FogColor;
    #endif
    return apply_fog(baseColor, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, fogColor);
}
#endif

void main() {
    // Write monitor data pixels directly to frame buffer
    if (pbrMark == PBR_MARK_MONITOR_DATA) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        fragColor = vec4(meshLocal, 1.0);
        return;
        #endif
    }

    // Monitor screen rendering (red channel encodes monitor ID: 250, 251, 252).
    // The screen UV is stored at 16-bit precision in G/B, U on even and V on odd checkerboard pixels
    // (decoded by eris:monitor_data.glsl in the post chain).
    if (pbrMark <= -600 && pbrMark >= -602) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        int monitorIndex = -600 - pbrMark;
        vec2 screenUv = clamp(faceUv, 0.0, 1.0);
        bool storesU = ((int(gl_FragCoord.x) + int(gl_FragCoord.y)) & 1) == 0;
        int encoded = int(round((storesU ? screenUv.x : screenUv.y) * 65535.0));
        fragColor = vec4(float(250 + monitorIndex), float(encoded >> 8), float(encoded & 255), 255.0) / 255.0;
        return;
        #endif
    }

    // Floating dust particles within the main spotlight beam
    if (pbrMark == PBR_MARK_DUST_PARTICLES) {
        #ifdef OIT_ALPHA_ONLY
        discard;
        #else
        vec2 centeredUv = faceUv * 2.0 - 1.0;
        float particleShape = 0.75 + 0.25 * sin(atan(centeredUv.y, centeredUv.x) * 3.0 + meshLocal.z * 40.0);
        float radius = length(centeredUv) / particleShape;
        if (radius > 1.0) {
            discard;
        }

        vec3 worldPos = pbrPos + getCameraWorldOffset();
        vec3 toLight = normalize(worldPos - SPOTLIGHT_POSITIONS[0]);
        float spotlightAttenuation = smoothstep(SPOTLIGHT_CUTOFFS[0].x, SPOTLIGHT_CUTOFFS[0].y, dot(toLight, SPOTLIGHT_DIRECTIONS[0])) * calculateLightFlicker(GameTime);
        spotlightAttenuation *= smoothstep(0.0, 0.12, meshLocal.x) * smoothstep(1.0, 0.85, meshLocal.x);
        if (spotlightAttenuation < 0.03) {
            discard;
        }

        float shimmer = 0.55 + 0.45 * sin(GameTime * 3600.0 * (0.6 + meshLocal.z) + meshLocal.z * 90.0);
        float particleBrightness = meshLocal.y > 0.5 ? 0.45 : 1.3 * (1.1 - radius * radius * 0.5);
        fragColor = vec4(min(vec3(1.0, 0.92, 0.78) * spotlightAttenuation * shimmer * particleBrightness, 249.0 / 255.0), 1.0);
        return;
        #endif
    }

    // PBR-shaded custom models
    if (pbrMark > 0) {
        if (texture(Sampler0, texCoord0).a < 0.5) {
            discard;
        }
        #ifdef OIT_ALPHA_ONLY
        executeAlphaOnlyPhase(gl_FragCoord.z, 1.0);
        #else
        vec3 pbrColor = shadePackedPBR(Sampler0, texCoord0, exp2(float(pbrMark)), pbrPos, normalize(pbrNormal));
        fragColor = vec4(min(pbrColor, vec3(249.0 / 255.0, 1.0, 1.0)), 1.0);
        #endif
        return;
    }

    // Standard Minecraft item rendering
    vec4 baseColor = texture(Sampler0, texCoord0);
    #ifdef ALPHA_CUTOUT
    if (baseColor.a < ALPHA_CUTOUT) {
        discard;
    }
    #endif
    baseColor *= vertexColor * ColorModulator;
    #ifdef GLINT
    baseColor.a = max(baseColor.a, GlintAlpha);
    #endif

    #ifdef OIT_ALPHA_ONLY
    executeAlphaOnlyPhase(gl_FragCoord.z, baseColor.a);
    #else
    fragColor = applyVanillaFogAndLight(baseColor);
    fragColor.r = min(fragColor.r, 249.0 / 255.0);
    #endif
}
