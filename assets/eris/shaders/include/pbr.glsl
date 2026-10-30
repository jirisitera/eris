#ifndef ERIS_PBR_GLSL
#define ERIS_PBR_GLSL

// @formatter:off
#include <eris:pbr_scene.glsl>
// @formatter:on

const float PI = 3.14159265;

// Camera world offset based on view orientation
vec3 getCameraWorldOffset() {
    float viewForwardZ = -ModelViewMat[2][2];
    return viewForwardZ > 0.0
    ? CAMERA_OFFSET_NORTH
    : CAMERA_OFFSET_SOUTH;
}

// Color space conversions
vec3 srgbToLinear(vec3 color) {
    return pow(color, vec3(2.2));
}

vec3 linearToSrgb(vec3 color) {
    return pow(clamp(color, 0.0, 1.0), vec3(1.0 / 2.2));
}

// Cotangent frame (TBN matrix) reconstruction from derivatives
mat3 calculateTBN(vec3 normal, vec3 pos, vec2 uv) {
    vec3 dp1 = dFdx(pos);
    vec3 dp2 = dFdy(pos);
    vec2 duv1 = dFdx(uv);
    vec2 duv2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, normal);
    vec3 dp1perp = cross(normal, dp1);
    vec3 tangent = dp2perp * duv1.x + dp1perp * duv2.x;
    vec3 bitangent = dp2perp * duv1.y + dp1perp * duv2.y;
    float invMax = inversesqrt(max(max(dot(tangent, tangent), dot(bitangent, bitangent)), 1e-20));
    return mat3(tangent * invMax, bitangent * invMax, normal);
}

// Global parameters for optional wrap/rim lighting
float gDiffuseWrap = 0.0;
float gRimIntensity = 0.0;
vec3 gRimAxis = vec3(0.0, 1.0, 0.0);

// GGX / Trowbridge-Reitz Normal Distribution Function (NDF)
float distributionGGX(float NdotH, float roughnessSq) {
    float alphaSq = roughnessSq * roughnessSq;
    float denom = NdotH * NdotH * (alphaSq - 1.0) + 1.0;
    return alphaSq / (PI * denom * denom);
}

// Schlick-GGX Geometric Shadowing Function (Smith's method)
float geometrySmith(float NdotV, float NdotL, float roughness) {
    float k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
    float gV = NdotV / (NdotV * (1.0 - k) + k);
    float gL = NdotL / (NdotL * (1.0 - k) + k);
    return gV * gL;
}

// Fresnel-Schlick approximation
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(1.0 - cosTheta, 5.0);
}

// Ambient sky lighting based on direction and roughness
vec3 sampleAmbientSky(vec3 dir, float roughness) {
    float upwardFactor = smoothstep(mix(0.97, 0.6, roughness), 1.0, dir.y);
    return vec3(0.025, 0.022, 0.018) * upwardFactor * (1.0 - roughness);
}

// Cook-Torrance point light calculation
vec3 calculatePointLight(
    vec3 normal,
    vec3 viewDir,
    vec3 fragPos,
    vec3 lightPos,
    vec3 lightColor,
    vec3 albedo,
    float roughness,
    float metallic,
    vec3 F0
) {
    vec3 lightOffset = lightPos - fragPos;
    float distSq = dot(lightOffset, lightOffset);
    vec3 lightDir = lightOffset * inversesqrt(distSq);
    vec3 halfDir = normalize(lightDir + viewDir);

    float NdotL = max(dot(normal, lightDir), 0.0);
    float NdotV = max(dot(normal, viewDir), 1e-4);
    float NdotH = max(dot(normal, halfDir), 0.0);
    float VdotH = max(dot(viewDir, halfDir), 0.0);

    // Specular BRDF
    vec3 F = fresnelSchlick(VdotH, F0);
    float D = distributionGGX(NdotH, roughness * roughness);
    float G = geometrySmith(NdotV, NdotL, roughness);
    vec3 specular = (D * G * F) / (4.0 * NdotV * max(NdotL, 1e-4));

    // Diffuse energy conservation
    vec3 kD = (vec3(1.0) - F) * (1.0 - metallic);
    float wrappedNdotL = max((dot(normal, lightDir) + gDiffuseWrap) / (1.0 + gDiffuseWrap), 0.0);
    vec3 diffuse = kD * albedo / PI * wrappedNdotL;

    // Rim / backlight contribution
    float backlightFacing = smoothstep(-0.2, 0.5, dot(-viewDir, lightDir));
    float rimAngle = max(dot(gRimAxis, viewDir), 1e-4);
    float rimFactor = pow(1.0 - rimAngle, 2.2) * backlightFacing * smoothstep(-0.3, 0.2, dot(gRimAxis, lightDir) + 0.3);
    vec3 rimColor = mix(albedo, vec3(1.0), 0.5) * gRimIntensity * rimFactor;

    float attenuation = 1.0 / max(distSq, 0.06);
    return (diffuse + specular * NdotL + rimColor) * lightColor * attenuation;
}

// Spotlight calculation with smooth cone falloff
vec3 calculateSpotlight(
    vec3 normal,
    vec3 viewDir,
    vec3 fragPos,
    vec3 lightPos,
    vec3 spotlightDir,
    vec2 cutoffs,
    vec3 lightColor,
    vec3 albedo,
    float roughness,
    float metallic,
    vec3 F0
) {
    float spotlightAngle = dot(normalize(fragPos - lightPos), spotlightDir);
    float spotlightIntensity = smoothstep(cutoffs.x, cutoffs.y, spotlightAngle);
    return spotlightIntensity <= 0.0
    ? vec3(0.0)
    : calculatePointLight(normal, viewDir, fragPos, lightPos, lightColor * spotlightIntensity, albedo, roughness, metallic, F0);
}

// Subtle procedural light flicker (hum + occasional dips)
float calculateLightFlicker(float timeSeconds) {
    float timeTicks = timeSeconds * 1200.0;
    float continuousHum = 0.99 + 0.01 * sin(timeTicks * 23.0) * sin(timeTicks * 7.3);
    float steppedTime = floor(timeTicks * 8.0);
    float randomDip = fract(sin(steppedTime * 91.17) * 43758.5453) > 0.98 ? 0.94 : 1.0;
    return continuousHum * randomDip;
}

// Main PBR shading pass
vec3 shadePBR(vec3 albedo, vec3 normal, float roughness, float metallic, float aoFactor, vec3 viewPos) {
    vec3 viewDir = normalize(-viewPos);
    vec3 F0 = mix(vec3(0.04), albedo, metallic);
    vec3 cameraOffset = getCameraWorldOffset();
    vec3 totalRadiance = vec3(0.0);

    for (int i = 0; i < SPOTLIGHT_COUNT; i++) {
        float flicker = (i == 0) ? calculateLightFlicker(GameTime) : 1.0;
        totalRadiance += calculateSpotlight(
            normal,
            viewDir,
            viewPos,
            SPOTLIGHT_POSITIONS[i] - cameraOffset,
            SPOTLIGHT_DIRECTIONS[i],
            SPOTLIGHT_CUTOFFS[i],
            SPOTLIGHT_COLORS[i] * flicker,
            albedo,
            roughness,
            metallic,
            F0
        );
    }

    // Ambient irradiance & reflections
    float NdotV = max(dot(normal, viewDir), 0.0);
    vec3 fresnelRoughness = F0 + (max(vec3(1.0 - roughness), F0) - F0) * pow(1.0 - NdotV, 5.0);
    vec3 ambientDiffuse = sampleAmbientSky(normal, 1.0) * albedo * (1.0 - metallic) * (1.0 - fresnelRoughness);
    vec3 ambientSpecular = sampleAmbientSky(reflect(-viewDir, normal), roughness) * fresnelRoughness;
    vec3 ambient = ambientDiffuse + ambientSpecular;
    totalRadiance += ambient * aoFactor;

    // Reinhard tonemapping and contrast adjustment
    totalRadiance = totalRadiance / (1.0 + totalRadiance);
    totalRadiance = pow(totalRadiance, vec3(1.35));
    return linearToSrgb(totalRadiance);
}

// Shading with packed material atlas (Normal map + Roughness/Metallic/AO map)
vec3 shadePackedPBR(sampler2D atlasSampler, vec2 uv, float atlasSubpixelOffset, vec3 viewPos, vec3 geomNormal) {
    vec2 uvOffset = atlasSubpixelOffset / vec2(textureSize(atlasSampler, 0));
    vec3 albedo = srgbToLinear(texture(atlasSampler, uv).rgb);
    vec3 normalMap = texture(atlasSampler, uv + vec2(uvOffset.x, 0.0)).rgb * 2.0 - 1.0;
    vec3 materialMap = texture(atlasSampler, uv + vec2(0.0, uvOffset.y)).rgb;

    mat3 tbn = calculateTBN(geomNormal, viewPos, uv);
    vec3 normal = normalize(tbn * normalMap);

    float roughness = clamp(materialMap.r, 0.05, 1.0);
    float metallic = materialMap.g;
    float ao = mix(0.5, 1.0, materialMap.b);

    return shadePBR(albedo, normal, roughness, metallic, ao, viewPos);
}

// Shading for beveled/cushioned surfaces with custom face UVs
vec3 shadeBeveledSurface(sampler2D atlasSampler, vec2 uv, vec3 viewPos, vec3 geomNormal, vec2 faceUv) {
    vec3 albedo = srgbToLinear(texture(atlasSampler, uv).rgb);
    vec2 centeredFaceUv = faceUv * 2.0 - 1.0;
    vec2 bevelOffset = sign(centeredFaceUv) * smoothstep(0.5, 1.0, abs(centeredFaceUv)) * 0.36;
    vec3 normal = normalize(calculateTBN(geomNormal, viewPos, faceUv) * normalize(vec3(bevelOffset, 1.0)));

    gRimAxis = geomNormal;
    gDiffuseWrap = 0.45;
    gRimIntensity = 4.0;
    vec3 result = shadePBR(albedo, normal, 0.75, 0.0, 1.0, viewPos);
    gDiffuseWrap = 0.0;
    gRimIntensity = 0.0;
    gRimAxis = vec3(0.0, 1.0, 0.0);

    return result;
}

// Flat PBR shading without normal mapping
vec3 shadeFlatPBR(sampler2D atlasSampler, vec2 uv, vec3 viewPos, vec3 geomNormal) {
    vec3 albedo = srgbToLinear(texture(atlasSampler, uv).rgb);
    vec3 normal = dot(geomNormal, viewPos) > 0.0 ? -geomNormal : geomNormal;
    return shadePBR(albedo, normal, 0.9, 0.0, 1.0, viewPos);
}

// Backwards compatibility aliases
#define _t ERIS_PBR_GLSL
#define ib PI
#define jb getCameraWorldOffset
#define lb srgbToLinear
#define mb linearToSrgb
#define nb calculateTBN
#define yb distributionGGX
#define Bb geometrySmith
#define Fb fresnelSchlick
#define Hb sampleAmbientSky
#define Jb calculatePointLight
#define bc calculateSpotlight
#define fc calculateLightFlicker
#define jc shadePBR
#define wc shadePackedPBR
#define Bc shadeBeveledSurface
#define Ec shadeFlatPBR

#endif
