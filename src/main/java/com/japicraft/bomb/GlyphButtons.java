package com.japicraft.bomb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * four shuffled SGA glyphs, solved in alphabetical order of the underlying letters
 */
public final class GlyphButtons {
    private GlyphButtons() {
    }

    public static Definition randomDefinition() {
        List<String> glyphs = new ArrayList<>(List.of("a", "b", "c", "d"));
        Collections.shuffle(glyphs);
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        order.sort(Comparator.comparing(glyphs::get));
        return new Definition(glyphs, order);
    }

    public record Definition(List<String> glyphs, List<Integer> order) implements BombModule.Definition {
        public Definition {
            glyphs = List.copyOf(glyphs);
            order = List.copyOf(order);
            if (glyphs.size() != 4 || order.size() != 4 || glyphs.stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Expected four glyph labels and four ordered indices");
            new ButtonSequence(order);
        }

        @Override
        public BombModule mount(ModuleContext context) {
            return new ReagentPanel(context, this);
        }
    }
}
