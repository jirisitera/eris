package com.japicraft.bettermodel.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import kr.toxicity.model.api.nms.Identifiable;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EquipmentSlot;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.SetPassengersPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;

final class Packets {
    static final int FLAGS_INDEX = MetadataDef.ENTITY_FLAGS.index();

    private Packets() {
    }

    static @NotNull SetPassengersPacket mountPacket(@NotNull EntityTrackerRegistry registry, @NotNull Entity entity, @NotNull List<Integer> otherPassengers) {
        List<Integer> passengers = new ArrayList<>();
        registry.displays().mapToInt(Identifiable::id).forEach(passengers::add);
        passengers.addAll(otherPassengers);
        return new SetPassengersPacket(entity.getEntityId(), passengers);
    }

    static @NotNull SetPassengersPacket mountPacket(@NotNull EntityTrackerRegistry registry, @NotNull Entity entity) {
        List<Integer> others = new ArrayList<>();
        for (Entity passenger : entity.getPassengers()) {
            if (EntityTrackerRegistry.registry(passenger.getUuid()) != null) {
                continue;
            }
            others.add(passenger.getEntityId());
        }
        return Packets.mountPacket(registry, entity, others);
    }

    static byte entityFlags(@NotNull EntityTrackerRegistry registry, @NotNull UUID viewer, byte flags) {
        EntityHideOption option = registry.hideOption(viewer);
        int value = flags;
        if (option.fire()) {
            value &= ~1;
        }
        if (option.visibility()) {
            value |= 0x20;
        }
        if (option.glowing()) {
            value &= ~0x40;
        }
        return (byte) value;
    }

    static byte currentFlags(@NotNull Entity entity) {
        Metadata.Entry<?> entry = flagsEntry(entity);
        if (entry != null && entry.value() instanceof Byte b) {
            return b;
        }
        return 0;
    }

    private static Metadata.@Nullable Entry<?> flagsEntry(@NotNull Entity entity) {
        EntityMetaDataPacket packet = entity.getMetadataPacket();
        return packet.entries().get(FLAGS_INDEX);
    }

    static @Nullable EntityMetaDataPacket rewriteFlags(@NotNull EntityMetaDataPacket packet, @NotNull EntityTrackerRegistry registry, @NotNull UUID viewer) {
        Metadata.Entry<?> flags = packet.entries().get(FLAGS_INDEX);
        if (flags == null || !(flags.value() instanceof Byte value)) {
            return null;
        }
        byte rewritten = Packets.entityFlags(registry, viewer, value);
        if (rewritten == value) {
            return null;
        }
        Map<Integer, Metadata.Entry<?>> entries = new HashMap<>(packet.entries());
        entries.put(FLAGS_INDEX, Metadata.Byte(rewritten));
        return new EntityMetaDataPacket(packet.entityId(), entries);
    }

    static @NonNull EntityEquipmentPacket equipmentPacket(@NotNull LivingEntity entity, @NotNull Function<EquipmentSlot, ItemStack> items) {
        EnumMap<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot == EquipmentSlot.BODY || slot == EquipmentSlot.SADDLE) {
                continue;
            }
            equipment.put(slot, items.apply(slot));
        }
        return new EntityEquipmentPacket(entity.getEntityId(), equipment);
    }
}
