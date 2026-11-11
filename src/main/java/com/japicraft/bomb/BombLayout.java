package com.japicraft.bomb;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Grid origin is the surface cursor's top-left; X runs across, Y runs down.
 */
public record BombLayout(List<Placement> placements) {
    public static final int COLUMNS = 12, ROWS = 8;

    public BombLayout {
        placements = List.copyOf(placements);
        for (int i = 0; i < placements.size(); i++) {
            Placement a = placements.get(i);
            for (int j = 0; j < i; j++) {
                Placement b = placements.get(j);
                if (a.x < b.x + b.width && a.x + a.width > b.x
                    && a.y < b.y + b.height && a.y + a.height > b.y)
                    throw new IllegalArgumentException("Overlapping module slots");
            }
        }
    }

    public record Placement(int x, int y, int width, int height,
                            Supplier<? extends BombModule.Definition> module) {
        public Placement {
            Objects.requireNonNull(module);
            if (x < 0 || y < 0 || width <= 0 || height <= 0
                || width > COLUMNS || height > ROWS || x > COLUMNS - width || y > ROWS - height)
                throw new IllegalArgumentException("Module slot must fit the 12 by 8 grid");
        }

        public Surface.Rect bounds() {
            return new Surface.Rect((double) x / COLUMNS, (double) y / ROWS,
                (double) width / COLUMNS, (double) height / ROWS);
        }
    }
}
