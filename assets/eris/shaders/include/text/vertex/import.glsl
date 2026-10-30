#ifdef IS_GUI
#define NOTIFICATION_ID vec3(127.0, 216.0, 42.0) / 255.0

// @formatter:off
#include <minecraft:globals.glsl>
#include <eris:notification.glsl>
// @formatter:on

uniform sampler2D Sampler0;

layout(location = 6) out float renderNotification;
layout(location = 7) out vec2 notificationPos;
layout(location = 8) out float notificationProgress;
#endif
