package com.japicraft.bomb;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

public final class RedstoneWires {
    private RedstoneWires() {
    }

    public static Definition randomDefinition() {
        return new Definition(IntStream.range(0, 4)
            .mapToObj(i -> Wire.values()[ThreadLocalRandom.current().nextInt(4)]).toList());
    }

    public enum Wire {
        RED("R", 0xAD3434), BLUE("B", 0x315DA8), YELLOW("Y", 0xA68120), WHITE("W", 0x777777);
        final String label;
        final int color;

        Wire(String label, int color) {
            this.label = label;
            this.color = color;
        }
    }

    public record Definition(List<Wire> wires) implements BombModule.Definition {
        public Definition {
            wires = List.copyOf(wires);
            if (wires.size() != 4) throw new IllegalArgumentException("Expected four wires");
        }

        public int target() {
            if (wires.stream().filter(w -> w == Wire.BLUE).count() == 1) return wires.indexOf(Wire.BLUE);
            int red = wires.lastIndexOf(Wire.RED);
            return red >= 0 ? red : 0;
        }

        @Override
        public BombModule mount(ModuleContext context) {
            return new ReagentPanel(context, this);
        }
    }
}
