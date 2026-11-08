package com.japicraft.bomb;

import com.japicraft.camera.BombView;
import com.japicraft.environment.StructureLoader;
import net.minestom.server.coordinate.Pos;

/**
 * The same corners and cursor footprint used by surface_cursor.json and cursor.fsh.
 */
public final class Surface {
    public static final double WIDTH = 0.75, DEPTH = 0.5, CURSOR_SIZE = 0.08;

    private Surface() {
    }

    /**
     * Mouse position denotes the cursor texture's top-left corner, exactly as in the shader.
     */
    public static Point cursor(double x, double y) {
        return new Point(Math.clamp(x, 0, 1) * (1 - CURSOR_SIZE / WIDTH),
            Math.clamp(y, 0, 1) * (1 - CURSOR_SIZE / DEPTH));
    }

    public static Pos world(Point point, double elevation) {
        // Item displays rotate their models 180 degrees around Y before rendering.
        // The shader's probe corners therefore run from +X/+Z towards -X/-Z.
        return StructureLoader.CASE_SPAWN.add((0.5 - point.u) * WIDTH,
            BombView.SURFACE_HEIGHT + elevation, (0.5 - point.v) * DEPTH).withView(0, 0);
    }

    public record Point(double u, double v) {
    }

    public record Rect(double u, double v, double width, double height) {
        public Rect {
            if (!Double.isFinite(u) || !Double.isFinite(v) || !Double.isFinite(width)
                || !Double.isFinite(height) || u < 0 || v < 0 || width <= 0 || height <= 0
                || u + width > 1 || v + height > 1) throw new IllegalArgumentException("Invalid surface rectangle");
        }

        public boolean contains(Point point) {
            return point.u >= u && point.u < u + width && point.v >= v && point.v < v + height;
        }

        public Point center() {
            return new Point(u + width / 2, v + height / 2);
        }
    }
}
