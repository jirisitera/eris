package com.japicraft.bomb;

import com.japicraft.constants.Font;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.color.Color;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * reusable controls for glyphs, wires and brewing; only their puzzle data changes per round
 */
final class ReagentPanel implements BombModule {
    private static final ItemStack GREEN = item(0x40BA60);
    private final ModuleContext context;
    private final List<Surface.Rect> bounds = new ArrayList<>();
    private final List<Entity> buttons = new ArrayList<>(), labels = new ArrayList<>();
    private final ItemStack[] normal = new ItemStack[4], hover = new ItemStack[4], shown = new ItemStack[4];
    private ButtonSequence sequence;

    ReagentPanel(ModuleContext context, BombModule.Definition definition) {
        this.context = context;
        Configuration config = configuration(definition);
        boolean glyphs = config.title == null;
        if (!glyphs) context.label(context.rect(.08, .02, .84, .10), text(config.title));
        for (int i = 0; i < 4; i++) {
            double x = glyphs ? .025 + i % 2 * .5 : config.wires ? .08 : .06 + i % 2 * .48;
            double y = glyphs ? .025 + (i / 2) * .5 : config.wires ? .20 + i * .19 : .20 + (i / 2) * .39;
            double w = glyphs ? .45 : config.wires ? .84 : .40;
            double h = glyphs ? .45 : config.wires ? .12 : .32;
            Surface.Rect rect = context.rect(x, y, w, h);
            bounds.add(rect);
            buttons.add(context.button(rect, ItemStack.AIR));
            Surface.Rect label = glyphs ? rect : context.rect(x + .02, y + h / 2 - .035, w - .04, .07);
            labels.add(context.label(label, Component.empty()));
        }
        reset(definition);
    }

    private static Component text(String text) {
        return Component.text(text, NamedTextColor.WHITE);
    }

    private static Configuration configuration(BombModule.Definition definition) {
        return switch (definition) {
            case GlyphButtons.Definition glyphs -> new Configuration(null,
                glyphs.glyphs().stream().map(s -> text(s).font(Font.ALT)).toList(),
                List.of(0xB83232, 0xB83232, 0xB83232, 0xB83232), glyphs.order(), false);
            case RedstoneWires.Definition wires -> new Configuration("WIRES",
                IntStream.range(0, 4).mapToObj(i -> text((i + 1) + " " + wires.wires().get(i).label)).toList(),
                wires.wires().stream().map(w -> w.color).toList(), List.of(wires.target()), true);
            case BrewingStand.Definition brew -> new Configuration("BREW",
                brew.reagents().stream().map(r -> text(r.label)).toList(),
                brew.reagents().stream().map(r -> r.color).toList(), brew.order(), false);
            default -> throw new IllegalArgumentException("Unsupported module definition");
        };
    }

    private static ItemStack item(int color) {
        return ItemStack.of(Material.WHITE_CONCRETE).withItemModel("eris:module_button")
            .withCustomModelData(List.of(), List.of(), List.of(), List.of(new Color(color)));
    }

    @Override
    public void reset(BombModule.Definition definition) {
        Configuration config = configuration(definition);
        sequence = new ButtonSequence(4, config.order);
        for (int i = 0; i < 4; i++) {
            int c = config.colors.get(i);
            normal[i] = item(c);
            hover[i] = item(config.title == null ? 0xED6262 : (((c >> 16 & 255) + 255) / 2 << 16)
                | (((c >> 8 & 255) + 255) / 2 << 8) | ((c & 255) + 255) / 2);
            Component label = config.labels.get(i);
            labels.get(i).editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(label));
        }
        hover(null);
    }

    @Override
    public void hover(Surface.Point cursor) {
        for (int i = 0; i < 4; i++) {
            ItemStack next = sequence.accepted(i) ? GREEN
                : !solved() && cursor != null && bounds.get(i).contains(cursor) ? hover[i] : normal[i];
            if (shown[i] == next) continue;
            shown[i] = next;
            buttons.get(i).editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(next));
        }
    }

    @Override
    public void press(Surface.Point cursor) {
        if (cursor == null || solved()) return;
        for (int i = 0; i < 4; i++) {
            if (!bounds.get(i).contains(cursor)) continue;
            ButtonSequence.Result result = sequence.press(i);
            hover(cursor);
            if (result == ButtonSequence.Result.STRIKE) context.strike();
            return;
        }
    }

    @Override
    public boolean solved() {
        return sequence.solved();
    }

    @Override
    public void close() {
        context.close();
    }

    private record Configuration(String title, List<Component> labels, List<Integer> colors,
                                 List<Integer> order, boolean wires) {
    }
}
