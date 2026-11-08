package com.japicraft.bomb;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.color.Color;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Four shuffled SGA glyphs; the expert's rule is alphabetical order of the underlying letters.
 */
public final class GlyphButtons implements BombModule {
    private static final ItemStack RED = item(0xB83232);
    private static final ItemStack HOVER = item(0xED6262);
    private static final ItemStack GREEN = item(0x40BA60);
    private final ModuleContext context;
    private final ButtonSequence sequence;
    private final List<Surface.Rect> bounds = new ArrayList<>();
    private final List<Entity> buttons = new ArrayList<>();
    private final ItemStack[] shown = new ItemStack[4];

    private GlyphButtons(ModuleContext context, Definition definition) {
        this.context = context;
        sequence = new ButtonSequence(definition.order());
        for (int i = 0; i < 4; i++) {
            Surface.Rect rect = context.rect(0.07 + (i % 2) * 0.5, 0.07 + (i / 2) * 0.5, 0.36, 0.36);
            bounds.add(rect);
            buttons.add(context.button(rect, RED));
            shown[i] = RED;
            context.label(rect, Component.text(definition.glyphs().get(i), NamedTextColor.WHITE).font(Key.key("minecraft:alt")));
        }
    }

    public static Definition randomDefinition() {
        List<String> glyphs = new ArrayList<>(List.of("a", "b", "c", "d"));
        Collections.shuffle(glyphs);
        List<Integer> order = new ArrayList<>(List.of(0, 1, 2, 3));
        order.sort(Comparator.comparing(glyphs::get));
        return new Definition(glyphs, order);
    }

    private static ItemStack item(int color) {
        return ItemStack.of(Material.WHITE_CONCRETE).withItemModel("eris:module_button")
            .withCustomModelData(List.of(), List.of(), List.of(), List.of(new Color(color)));
    }

    @Override
    public void hover(Surface.Point cursor) {
        for (int i = 0; i < buttons.size(); i++) {
            ItemStack next = sequence.accepted(i) ? GREEN : cursor != null && bounds.get(i).contains(cursor) ? HOVER : RED;
            if (shown[i] == next) continue;
            shown[i] = next;
            buttons.get(i).editEntityMeta(ItemDisplayMeta.class, meta -> meta.setItemStack(next));
        }
    }

    @Override
    public void press(Surface.Point cursor) {
        for (int i = 0; i < bounds.size(); i++) {
            if (!bounds.get(i).contains(cursor)) continue;
            if (sequence.press(i) == ButtonSequence.Result.STRIKE) context.strike();
            hover(cursor);
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
            return new GlyphButtons(context, this);
        }
    }
}
