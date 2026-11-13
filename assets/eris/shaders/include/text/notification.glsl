#define NOTIFICATION_MARGIN vec2(0.0, 20.0)
#define NOTIFICATION_SIZE vec2(140.0, 40.0)

// build animation: cell size in gui pixels, share of the fade each cell spends popping, randomness of the order
#define NOTIFICATION_CELL 4.0
#define NOTIFICATION_POP 0.25
#define NOTIFICATION_NOISE 0.35

// text: offset inside the panel, and when a cell's text shows up during its pop
#define NOTIFICATION_TEXT_OFFSET vec2(6.0, 6.0)
#define NOTIFICATION_TEXT_POP 0.75

// low 2 bits of each text color channel mark notification glyphs
#define NOTIFICATION_TEXT ivec3(1, 2, 1)
#define NOTIFICATION_HIDE ivec3(2, 1, 2)

// vanilla title layout: scale and y offset of the title line around the screen center
#define TITLE_SCALE 4.0
#define TITLE_Y - 10.0

float notificationPop(vec2 cell, float progress) {
    vec2 cells = ceil(NOTIFICATION_SIZE / NOTIFICATION_CELL);

    float sweep = (cell.x + cell.y) / max(1.0, cells.x + cells.y - 2.0);
    float noise = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);

    float threshold = mix(sweep, noise, NOTIFICATION_NOISE) * (1.0 - NOTIFICATION_POP);

    return clamp((progress - threshold) / NOTIFICATION_POP, 0.0, 1.0);
}
