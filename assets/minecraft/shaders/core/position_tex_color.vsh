#version 330
#extension GL_ARB_separate_shader_objects : require
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;
layout(location = 2) in vec4 Color;

layout(location = 0) out vec2 texCoord0;
layout(location = 1) out vec4 vertexColor;
layout(location = 2) flat out int lookMark;
layout(location = 3) flat out vec2 lookAngles;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    vertexColor = Color;
    lookMark = 0;
    lookAngles = vec2(0.0);

    ivec2 atlasSize = textureSize(Sampler0, 0);
    ivec4 markerColor = ivec4(round(texelFetch(
        Sampler0,
        ivec2(fract(UV0) * vec2(atlasSize)) % atlasSize,
        0
    ) * 255.0));

    if (markerColor == ivec4(254, 171, 33, 255)) {
        float yaw = UV0.x - (Position.x < 0.0 ? 4.0 : 0.0);
        float pitch = UV0.y - (Position.y < 0.0 ? 4.0 : 0.0);
        lookMark = 1;
        lookAngles = vec2(-yaw * 64.0, pitch * 64.0);
    }
}
