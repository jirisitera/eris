if (vertexColor.r >= 0.1 || vertexColor.g >= 0.1 || vertexColor.b >= 0.1) {
    if (vertexColor.b > 0.7 && vertexColor.r < 0.3) {
        color = vec4(0.4, 0.0, 0.0, color.a);
    } else if (vertexColor.r > 0.7 && vertexColor.b < 0.3) {
        color = vec4(1.0, 0.6, 0.0, color.a);
    } else {
        color = vec4(1.0, 0.15, 0.0, color.a);
    }
}
