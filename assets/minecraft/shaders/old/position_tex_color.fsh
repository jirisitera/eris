#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec2 texCoord0;
layout(location = 1) in vec4 vertexColor;
layout(location = 2) flat in int lookMark;
layout(location = 3) flat in vec2 lookAngles;
layout(location = 0) out vec4 fragColor;

const int MARKER_Y = 16;
const int MARKER_X_MIN = 26;
const int MARKER_X_MAX = 28;
const ivec3 MARKER_COLOR = ivec3(91, 126, 209);

void main() {
    if (lookMark != 0) {
        ivec2 pixel = ivec2(gl_FragCoord.xy);

        // The post effect samples exactly three pixels from this row.
        if (pixel.y != MARKER_Y ||
            pixel.x < MARKER_X_MIN ||
            pixel.x > MARKER_X_MAX) {
            discard;
        }

        // Store signed yaw (-180..180 degrees) as an unsigned value.
        int encodedYaw = clamp(
            int(round((lookAngles.x + 180.0) / 360.0 * 65535.0)),
            0,
            65535
        );

        // Store pitch as an unsigned value spanning the complete -90..90
        // range.
        int encodedPitch = clamp(
            int(round((lookAngles.y + 90.0) / 180.0 * 65535.0)),
            0,
            65535
        );

        ivec3 marker = pixel.x == 26
        ? ivec3(encodedYaw >> 8, encodedYaw & 255, encodedPitch >> 8)
        : pixel.x == 27
        ? ivec3(encodedPitch & 255, 0, 0)
        : MARKER_COLOR;

        fragColor = vec4(vec3(marker) / 255.0, 1.0);
        return;
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a == 0.0) {
        discard;
    }

    fragColor = color * ColorModulator;
}
