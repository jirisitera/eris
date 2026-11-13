package com.japicraft.bomb;

import net.minestom.server.instance.Instance;
import net.minestom.server.tag.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * instance-owned puzzle assembly and input shared by reconnecting players
 */
public final class BombModules implements AutoCloseable {
    private static final Tag<BombModules> TAG = Tag.Transient("bombModules");
    private final Instance instance;
    private final Runnable strike;
    private final List<Mounted> modules = new ArrayList<>();
    private boolean active;

    private BombModules(Instance instance, Runnable strike) {
        this.instance = instance;
        this.strike = strike;
    }

    public static BombModules get(Instance instance) {
        return instance.getTag(TAG);
    }

    public static void create(Instance instance, Runnable strike) {
        create(instance, strike, BombLayouts.FULL);
    }

    public static void create(Instance instance, Runnable strike, BombLayout layout) {
        if (get(instance) != null) return;
        BombModules bomb = new BombModules(instance, strike);
        try {
            for (BombLayout.Placement placement : layout.placements()) {
                bomb.add(placement.module(), placement.bounds());
            }
            instance.setTag(TAG, bomb);
        } catch (RuntimeException e) {
            bomb.close();
            throw e;
        }
    }

    private void add(Supplier<? extends BombModule.Definition> definition, Surface.Rect slot) {
        ModuleContext context = new ModuleContext(instance, slot, () -> {
            if (active) strike.run();
        });
        try {
            modules.add(new Mounted(definition.get().mount(context), definition));
        } catch (RuntimeException e) {
            context.close();
            throw e;
        }
    }

    public synchronized void hover(Surface.Point cursor) {
        modules.forEach(mounted -> mounted.module.hover(active ? cursor : null));
    }

    public synchronized void press(Surface.Point cursor) {
        if (!active || cursor == null) return;
        modules.forEach(mounted -> mounted.module.press(cursor));
    }

    public synchronized void start() {
        active = true;
    }

    public synchronized void reset() {
        active = false;
        modules.forEach(mounted -> mounted.module.reset(mounted.definition.get()));
    }

    public synchronized int solvedCount() {
        return (int) modules.stream().filter(mounted -> mounted.module.solved()).count();
    }

    public synchronized int totalCount() {
        return modules.size();
    }

    @Override
    public synchronized void close() {
        modules.forEach(mounted -> mounted.module.close());
        modules.clear();
        active = false;
        instance.removeTag(TAG);
    }

    private record Mounted(BombModule module, Supplier<? extends BombModule.Definition> definition) {
    }
}
