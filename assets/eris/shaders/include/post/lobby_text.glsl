// normalized top-left lobby coordinates shared with LobbyInterface.java
// effect font labels independent of GUI scale
const int LOBBY_TEXT_OFFSETS[10] = int[](0, 16, 28, 41, 55, 60, 72, 94, 113, 121);
const int LOBBY_TEXT_LENGTHS[10] = int[](16, 12, 13, 14, 5, 12, 22, 19, 8, 13);
const int LOBBY_GLYPHS[134] = int[](
    14, 19, 26, 26, 30, 16, -1, 36, 26, 32, 29, -1, 29, 26, 23, 16,
    13, 26, 24, 13, -1, 15, 16, 17, 32, 30, 16, 29,
    24, 12, 25, 32, 12, 23, -1, 16, 35, 27, 16, 29, 31,
    15, 16, 17, 32, 30, 12, 23, -1, 19, 16, 23, 27, 16, 29,
    29, 16, 12, 15, 36,
    14, 12, 25, 14, 16, 23, -1, 29, 16, 12, 15, 36,
    14, 19, 26, 26, 30, 16, -1, 15, 20, 17, 17, 16, 29, 16, 25, 31, -1, 29, 26, 23, 16, 30,
    34, 12, 20, 31, 20, 25, 18, -1, 17, 26, 29, -1, 27, 23, 12, 36, 16, 29, 30,
    29, 16, 12, 15, 36, -1, 32, 27,
    36, 26, 32, -1, 12, 29, 16, -1, 29, 16, 12, 15, 36
);

bool lobbyInside(vec2 point, vec4 bounds) {
    return all(greaterThanEqual(point, bounds.xy)) && all(lessThanEqual(point, bounds.zw));
}

const vec4 LOBBY_PREVIOUS_ROLE_BOUNDS = vec4(0.24, 0.755, 0.31, 0.835);
const vec4 LOBBY_NEXT_ROLE_BOUNDS = vec4(0.69, 0.755, 0.76, 0.835);
const vec4 LOBBY_READY_BOUNDS = vec4(0.35, 0.87, 0.65, 0.935);

float lobbyText(vec2 point, vec2 center, float height, float maxWidth, float aspect, int label) {
    int count = LOBBY_TEXT_LENGTHS[label];
    height = min(height, maxWidth * aspect / (float(count) * 0.625));
    vec2 cell = vec2(height * 0.625 / aspect, height);
    vec2 rel = (point - center) / cell + vec2(float(count) * 0.5, 0.5);
    if (rel.x < 0.0 || rel.x >= float(count) || rel.y < 0.0 || rel.y >= 1.0) return 0.0;
    int glyph = LOBBY_GLYPHS[LOBBY_TEXT_OFFSETS[label] + int(floor(rel.x))];
    if (glyph < 0) return 0.0;
    vec2 texel = (vec2(glyph % 12, glyph / 12) + fract(rel)) * vec2(40.0, 64.0);
    return texture(FontSampler, texel / vec2(textureSize(FontSampler, 0))).r;
}

vec3 lobbyButton(vec3 scene, vec2 point, vec2 cursor, vec4 bounds, vec2 screenSize, bool selected, bool ready, int label) {
    if (!lobbyInside(point, bounds)) return scene;
    bool hovered = lobbyInside(cursor, bounds);
    vec3 accent = ready ? vec3(0.36, 0.90, 0.64) : vec3(0.38, 0.78, 1.0);
    vec3 fill = selected ? vec3(0.09, 0.22, 0.31) : vec3(0.065, 0.085, 0.13);
    if (hovered) fill += vec3(0.055, 0.085, 0.11);
    vec2 edge = min(point - bounds.xy, bounds.zw - point) * screenSize;
    float border = 1.0 - step(hovered || selected ? 2.0 : 1.0, min(edge.x, edge.y));
    fill = mix(fill, selected || hovered ? accent : vec3(0.24, 0.30, 0.39), border);
    float ink;
    if (label < 0) {
        // left/right chevrons from negative labels, independent of font glyphs
        vec2 arrow = (point - (bounds.xy + bounds.zw) * 0.5) / 0.014;
        arrow.x *= screenSize.x / screenSize.y;
        float direction = label == -1 ? -1.0 : 1.0;
        ink = (1.0 - step(0.16, abs(arrow.x - direction * (0.5 - abs(arrow.y))))) * (1.0 - step(1.0, abs(arrow.y)));
    } else {
        ink = lobbyText(point, (bounds.xy + bounds.zw) * 0.5, 0.026, bounds.z - bounds.x - 0.025,
            screenSize.x / screenSize.y, label);
    }
    return mix(fill, selected || hovered ? vec3(0.95) : vec3(0.75, 0.81, 0.87), ink);
}

vec3 renderLobby(vec3 scene, vec2 point, vec2 cursor, vec2 screenSize, ivec3 data) {
    if (!lobbyInside(point, vec4(0.045, 0.655, 0.955, 0.99))) return scene;
    float aspect = screenSize.x / screenSize.y;
    scene = mix(scene, vec3(0.025, 0.035, 0.06), 0.94);
    float heading = lobbyText(point, vec2(0.5, 0.702), 0.032, 0.75, aspect, 0);
    scene = mix(scene, vec3(0.87, 0.93, 1.0), heading);
    scene = lobbyButton(scene, point, cursor, LOBBY_PREVIOUS_ROLE_BOUNDS, screenSize, false, false, -1);
    float roleInk = lobbyText(point, vec2(0.5, 0.795), 0.026, 0.34, aspect, clamp(data.g, 0, 2) + 1);
    scene = mix(scene, vec3(0.95), roleInk);
    scene = lobbyButton(scene, point, cursor, LOBBY_NEXT_ROLE_BOUNDS, screenSize, false, false, -2);
    bool ready = (data.b & 1) != 0;
    bool conflict = (data.b & 2) != 0;
    bool waiting = (data.b & 4) != 0;
    scene = lobbyButton(scene, point, cursor, LOBBY_READY_BOUNDS, screenSize, ready, true, ready ? 5 : 4);
    int status = conflict ? 6 : waiting ? 7 : ready ? 9 : 8;
    float statusInk = lobbyText(point, vec2(0.5, 0.963), 0.017, 0.8, aspect, status);
    return mix(scene, conflict ? vec3(1.0, 0.75, 0.37) : vec3(0.63, 0.73, 0.83), statusInk);
}

