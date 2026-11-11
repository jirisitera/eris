package com.japicraft.bomb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class BrewingStand {
    private BrewingStand() {
    }

    public static Definition randomDefinition() {
        var random = ThreadLocalRandom.current();
        List<Reagent> reagents = new ArrayList<>(List.of(Reagent.WATER, Reagent.WART,
            random.nextBoolean() ? Reagent.SUGAR : Reagent.MAGMA,
            random.nextBoolean() ? Reagent.REDSTONE : Reagent.GLOWSTONE));
        Collections.shuffle(reagents);
        return new Definition(reagents);
    }

    public enum Reagent {
        WATER("H2O", 0x285E99), WART("WART", 0x922C43),
        SUGAR("SUG", 0x777777), MAGMA("MAG", 0xB25C23),
        REDSTONE("RED", 0x9C3434), GLOWSTONE("GLOW", 0x9C822F);
        final String label;
        final int color;

        Reagent(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public record Definition(List<Reagent> reagents) implements BombModule.Definition {
        public Definition {
            reagents = List.copyOf(reagents);
            if (reagents.size() != 4 || Collections.frequency(reagents, Reagent.WATER) != 1
                || Collections.frequency(reagents, Reagent.WART) != 1
                || reagents.stream().filter(r -> r == Reagent.SUGAR || r == Reagent.MAGMA).count() != 1
                || reagents.stream().filter(r -> r == Reagent.REDSTONE || r == Reagent.GLOWSTONE).count() != 1)
                throw new IllegalArgumentException("Expected water, wart, one ingredient and one modifier");
        }

        public List<Integer> order() {
            Reagent ingredient = reagents.contains(Reagent.SUGAR) ? Reagent.SUGAR : Reagent.MAGMA;
            Reagent modifier = reagents.contains(Reagent.REDSTONE) ? Reagent.REDSTONE : Reagent.GLOWSTONE;
            return List.of(reagents.indexOf(Reagent.WATER), reagents.indexOf(Reagent.WART),
                reagents.indexOf(ingredient), reagents.indexOf(modifier));
        }

        @Override
        public BombModule mount(ModuleContext context) {
            return new ReagentPanel(context, this);
        }
    }
}
