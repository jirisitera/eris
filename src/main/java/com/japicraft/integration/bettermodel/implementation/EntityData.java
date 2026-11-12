package com.japicraft.bettermodel.impl;

import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.IntPredicate;

final class EntityData {
    private final Map<Integer, Metadata.Entry<?>> values = new LinkedHashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    EntityData() {
    }

    synchronized <T> void set(@NotNull MetadataDef.Entry<T> def, @NotNull Metadata.Entry<?> value) {
        Metadata.Entry<?> previous = this.values.put(def.index(), value);
        if (previous == null || !previous.equals(value)) {
            this.dirty.add(def.index());
        }
    }

    synchronized void markDirty() {
        if (this.values.containsKey(MetadataDef.ItemDisplay.DISPLAYED_ITEM.index())) {
            this.dirty.add(MetadataDef.ItemDisplay.DISPLAYED_ITEM.index());
        }
    }

    synchronized @NotNull Map<Integer, Metadata.Entry<?>> all(@NotNull IntPredicate filter) {
        Map<Integer, Metadata.Entry<?>> result = new HashMap<>();
        this.values.forEach((index, value) -> {
            if (filter.test(index)) {
                result.put(index, value);
            }
        });
        return result;
    }

    synchronized @NotNull Map<Integer, Metadata.Entry<?>> takeDirty(@NotNull IntPredicate filter) {
        Map<Integer, Metadata.Entry<?>> result = new HashMap<>();
        Iterator<Integer> iterator = this.dirty.iterator();
        while (iterator.hasNext()) {
            int index = iterator.next();
            if (!filter.test(index)) {
                continue;
            }
            Metadata.Entry<?> value = this.values.get(index);
            if (value != null) {
                result.put(index, value);
            }
            iterator.remove();
        }
        return result;
    }
}
