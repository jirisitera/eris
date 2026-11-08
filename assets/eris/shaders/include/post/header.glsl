#define iResolution OutSize
#define iTime (GameTime * 1200.0)
#define iTimeDelta 0.0
#define iMouse vec4(0.0)
#define iFrame int(iTime * 60.0)
#define iChannel0 0
#define iChannel1 1

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(location = 0) out vec4 fragColor;
