package com.japicraft.bomb;

import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.display.ItemDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

/**
 * module-owned entities with local 0..1 coordinates mapped onto the bomb
 */
public final class ModuleContext implements AutoCloseable {
    private final Instance instance;
    private final Surface.Rect bounds;
    private final Runnable strike;
    private final List<Entity> entities = new ArrayList<>();

    public ModuleContext(Instance instance, Surface.Rect bounds, Runnable strike) {
        this.instance = instance;
        this.bounds = bounds;
        this.strike = strike;
    }

    public Surface.Rect rect(double u, double v, double width, double height) {
        if (u < 0 || v < 0 || width <= 0 || height <= 0 || u + width > 1 || v + height > 1)
            throw new IllegalArgumentException("Module layout must fit its slot");
        return new Surface.Rect(bounds.u() + u * bounds.width(), bounds.v() + v * bounds.height(),
            width * bounds.width(), height * bounds.height());
    }

    public Entity button(Surface.Rect rect, ItemStack item) {
        Entity entity = new Entity(EntityType.ITEM_DISPLAY);
        entity.editEntityMeta(ItemDisplayMeta.class, meta -> {
            meta.setItemStack(item);
            meta.setDisplayContext(ItemDisplayMeta.DisplayContext.NONE);
            meta.setScale(new Vec(rect.width() * Surface.WIDTH, 0.025, rect.height() * Surface.DEPTH));
            meta.setBrightness(15, 15);
        });
        return spawn(entity, Surface.world(rect.center(), 0));
    }

    public Entity label(Surface.Rect rect, Component text) {
        double scale = Math.min(rect.width() * Surface.WIDTH, rect.height() * Surface.DEPTH) * 2;
        Entity entity = new Entity(EntityType.TEXT_DISPLAY);
        entity.editEntityMeta(TextDisplayMeta.class, meta -> {
            meta.setText(text);
            meta.setBackgroundColor(0);
            meta.setShadow(false);
            meta.setLineWidth(100);
            meta.setScale(new Vec(scale, scale, scale));
            // upward faces with glyph tops towards +Z, away from the defuser
            Quaternionf rotation = new Quaternionf().rotationYXZ((float) Math.PI, (float) -Math.PI / 2, 0);
            meta.setLeftRotation(new float[]{rotation.x, rotation.y, rotation.z, rotation.w});
            meta.setBrightness(15, 15);
        });
        // horizontal text centering with vertical alignment at the baseline
        return spawn(entity, Surface.world(rect.center(), 0.015).add(0, 0, -scale * 0.09));
    }

    private Entity spawn(Entity entity, Pos position) {
        entity.setNoGravity(true);
        entities.add(entity);
        // shared display visibility through default auto-viewing
        entity.setInstance(instance, position);
        return entity;
    }

    public void strike() {
        strike.run();
    }

    @Override
    public void close() {
        entities.forEach(Entity::remove);
        entities.clear();
    }
}
