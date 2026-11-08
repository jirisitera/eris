package com.japicraft.bomb;

import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Instance-owned assembly and input routing; reconnecting players share the same puzzle.
 */
public final class BombModules implements AutoCloseable {
    private static final Tag<BombModules> TAG = Tag.Transient("bombModules");
    private final Instance instance;
    private final Runnable strike;
    private final List<BombModule> modules = new ArrayList<>();

    private BombModules(Instance instance, Runnable strike) {
        this.instance = instance;
        this.strike = strike;
    }

    public static BombModules get(Instance instance) {
        return instance.getTag(TAG);
    }

    public static void create(Instance instance, Runnable strike) {
        if (get(instance) != null) return;
        BombModules bomb = new BombModules(instance, strike);
        instance.setTag(TAG, bomb);
        bomb.add(GlyphButtons.randomDefinition(), new Surface.Rect(0, 0, 1, 1));
    }

    public synchronized void add(BombModule.Definition definition, Surface.Rect slot) {
        ModuleContext context = new ModuleContext(instance, slot, strike);
        try {
            modules.add(definition.mount(context));
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    public synchronized void hover(Surface.Point cursor) {
        modules.forEach(module -> module.hover(cursor));
    }

    public synchronized void press(Surface.Point cursor) {
        modules.forEach(module -> module.press(cursor));
    }

    public synchronized boolean solved() {
        return !modules.isEmpty() && modules.stream().allMatch(BombModule::solved);
    }

    @Override
    public synchronized void close() {
        modules.forEach(BombModule::close);
        modules.clear();
        instance.removeTag(TAG);
    }
}
