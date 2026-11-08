package com.japicraft.bomb;

import java.util.List;

/** Named layouts: each entry specifies X, Y, width, height and a fresh puzzle factory. */
public final class BombLayouts {
    public static final BombLayout DEFAULT = new BombLayout(List.of(
        new BombLayout.Placement(0, 0, 4, 4, GlyphButtons::randomDefinition)
    ));
    public static final BombLayout FULL = new BombLayout(List.of(
        new BombLayout.Placement(0, 0, 4, 4, GlyphButtons::randomDefinition),
        new BombLayout.Placement(4, 0, 4, 4, GlyphButtons::randomDefinition),
        new BombLayout.Placement(8, 0, 4, 4, GlyphButtons::randomDefinition),
        new BombLayout.Placement(0, 4, 4, 4, GlyphButtons::randomDefinition),
        new BombLayout.Placement(4, 4, 4, 4, GlyphButtons::randomDefinition),
        new BombLayout.Placement(8, 4, 4, 4, GlyphButtons::randomDefinition)
    ));
}
