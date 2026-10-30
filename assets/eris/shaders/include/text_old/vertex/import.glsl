#ifdef IS_GUI
#moj_import < minecraft:globals.glsl >

#define DECEIV_IDENTIFIER 0.8

#define DECEIV_CANVAS_SIZE 256.0

#define DECEIV_HEART_SIZE_SHADOW 10.0

out vec3 identifier;
flat out int effectGroup;
flat out int effectType;
out float localX;
out float localY;
#endif
