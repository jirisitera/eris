#version 330
#extension GL_ARB_separate_shader_objects : require

// @formatter:off
#include <minecraft:light.glsl>
#include <minecraft:fog.glsl>
#include <minecraft:dynamictransforms.glsl>
#include <minecraft:projection.glsl>
#include <minecraft:sample_lightmap.glsl>
#include <minecraft:globals.glsl>
// @formatter:on

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
#ifdef GLINT_SPECIAL
layout(location = 5) in vec2 UV3;
#endif
layout(location = 6) in vec3 Normal;

uniform sampler2D Sampler0;
#ifndef OIT_ALPHA_ONLY
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
layout(location = 0) out float sphericalVertexDistance;
layout(location = 1) out float cylindricalVertexDistance;
#endif

layout(location = 2) out vec4 vertexColor;
#ifndef OIT_ALPHA_ONLY
layout(location = 3) out vec4 lightMapColor;
layout(location = 4) out vec4 overlayColor;
#endif
layout(location = 5) out vec2 texCoord0;
#ifdef GLINT
layout(location = 6) out vec2 texCoordGlint;
#endif
layout(location = 7) flat out int pbrMark;
layout(location = 8) out vec3 pbrPos;
layout(location = 9) out vec3 pbrNormal;
layout(location = 11) out vec3 meshLocal;
layout(location = 12) out vec2 faceUv;

const int PBR_MARK_DUST_PARTICLES = -300;
const int PBR_MARK_MONITOR_DATA = -500;
const int MONITOR_ROW_BASE_Y = 19;
const vec3 ACTIVE_MARKER = vec3(91.0, 126.0, 209.0) / 255.0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ivec3 vertexTag = ivec3(round(Color.rgb * 255.0));

    pbrMark = (vertexTag.g == 254 && vertexTag.b == 1) ? vertexTag.r : 0;
    if (vertexTag.g == 254 && vertexTag.b == 3) {
        pbrMark = -100 - vertexTag.r;
    }

    pbrPos = Position;
    pbrNormal = Normal;
    vec2 customTexCoord = vec2(-1.0);
    meshLocal = vec3(0.0);

    const vec2 QUAD_UVS[4] = vec2[](vec2(0.0, 0.0), vec2(0.0, 1.0), vec2(1.0, 1.0), vec2(1.0, 0.0));
    faceUv = QUAD_UVS[gl_VertexIndex % 4];

    if (customTexCoord.x >= 0.0) {
        gl_Position = ProjMat * ModelViewMat * vec4(pbrPos, 1.0);
    }

    vec2 texelPos = UV0 * vec2(textureSize(Sampler0, 0));
    ivec4 texelTag = ivec4(round(texelFetch(Sampler0, ivec2(texelPos), 0) * 255.0));

    // Monitor screen and data pixel encoding
    if (texelTag.r == 252 && texelTag.g >= 99 && texelTag.g <= 101 && texelTag.a == 255) {
        int monitorIndex = texelTag.g - 99;
        if (texelTag.b == 255) {
            faceUv = (fract(texelPos) - 0.125) / 0.75;
            pbrMark = -600 - monitorIndex;
        } else if (texelTag.b < 44) {
            vec2 quadCorner = QUAD_UVS[3 - gl_VertexIndex % 4];
            int rowY = MONITOR_ROW_BASE_Y - monitorIndex;
            gl_Position = vec4((vec2(float(texelTag.b), float(rowY)) + quadCorner) / ScreenSize * 2.0 - 1.0, 0.999, 1.0);
            meshLocal = (texelTag.b == 40 || texelTag.b == 41) ? ACTIVE_MARKER : Color.rgb;
            pbrMark = PBR_MARK_MONITOR_DATA;
        }
    }

    // Floating dust particles within the spotlight beam
    if (vertexTag.g == 254 && vertexTag.b == 6) {
        vec3 noiseSample = texelFetch(Sampler0, ivec2(UV0 * vec2(textureSize(Sampler0, 0))), 0).rgb;
        float time = GameTime * 1200.0;
        vec3 noiseFreq = noiseSample * 40.0;
        float angle = noiseSample.r * 6.2831853;
        float radius = sqrt(noiseSample.g) * 0.85;

        vec3 turbulence = vec3(
            sin(time * 0.11 + noiseFreq.x) * 0.5 + sin(time * 0.27 + noiseFreq.y) * 0.3 + sin(time * 0.61 + noiseFreq.z) * 0.2,
            sin(time * 0.09 + noiseFreq.z) * 0.5 + sin(time * 0.23 + noiseFreq.x) * 0.3 + sin(time * 0.53 + noiseFreq.y) * 0.2,
            sin(time * 0.13 + noiseFreq.y) * 0.5 + sin(time * 0.31 + noiseFreq.z) * 0.3 + sin(time * 0.57 + noiseFreq.x) * 0.2
        ) * vec3(0.14, 0.09, 0.14);

        float heightInBeam = mod(noiseSample.b * 2.3 - time * 0.012, 2.3);
        vec3 displacement = vec3(cos(angle) * radius, heightInBeam + 0.05, sin(angle) * radius) + turbulence;
        vec3 particlePos = Position + displacement;

        float isGlintSpeck = step(0.85, fract(noiseSample.r * 7.0 + noiseSample.g * 3.0));
        float particleSize = mix(0.0055 + noiseSample.g * 0.0035, 0.012, isGlintSpeck);
        vec2 billboardOffset = vec2(1.0 - faceUv.x * 2.0, faceUv.y * 2.0 - 1.0);

        vec4 viewPos = ModelViewMat * vec4(particlePos, 1.0) + vec4(billboardOffset * particleSize, 0.0, 0.0);
        gl_Position = ProjMat * viewPos;
        pbrPos = particlePos;
        meshLocal = vec3(heightInBeam / 2.3, isGlintSpeck, noiseSample.b);
        pbrMark = PBR_MARK_DUST_PARTICLES;
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
    texCoord0 = customTexCoord.x >= 0.0 ? customTexCoord : UV0;

    #ifdef GLINT
    #ifdef GLINT_SPECIAL
    texCoordGlint = (TextureMat * vec4(UV3, 0.0, 1.0)).xy;
    #else
    texCoordGlint = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
    #endif
    #endif
}
