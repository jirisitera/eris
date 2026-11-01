package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomPlayer;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.entity.BasePlayer;
import kr.toxicity.model.api.nms.Identifiable;
import kr.toxicity.model.api.nms.PlayerChannelHandler;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.tracker.TrackerUpdateAction;
import net.minestom.server.entity.*;
import net.minestom.server.event.player.PlayerPacketEvent;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

final class PlayerChannelHandlerImpl implements PlayerChannelHandler {
    private static final Set<Integer> EQUIPMENT_SLOTS = Set.of(5, 6, 7, 8);
    private final Player player;
    private final BasePlayer base;
    private volatile boolean closed;

    PlayerChannelHandlerImpl(@NotNull Player player) {
        this.player = player;
        this.base = new BasePlayerImpl(player);
    }

    @Override
    public @NotNull BasePlayer base() {
        return this.base;
    }

    @Override
    public boolean isModEnabled() {
        return false;
    }

    @Override
    public void close() {
        this.closed = true;
    }

    @Override
    public void sendEntityData(@NotNull EntityTrackerRegistry registry) {
        if (!(registry.entity().handle() instanceof Entity entity)) {
            return;
        }
        Bundlers.Simple bundler = Bundlers.of(3);
        bundler.add(new SetPassengersPacket(entity.getEntityId(), entity.getPassengers().stream().map(Entity::getEntityId).toList()));
        bundler.add(new EntityMetaDataPacket(entity.getEntityId(), Map.of(Packets.FLAGS_INDEX, Metadata.Byte(Packets.currentFlags(entity)))));
        if (entity instanceof LivingEntity living) {
            bundler.add(Packets.equipmentPacket(living, living::getEquipment));
        }
        bundler.send(this.player);
    }

    private @Nullable EntityTrackerRegistry registryOf(int entityId) {
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(entityId);
        return registry != null && registry.isSpawned(this.player.getUuid()) ? registry : null;
    }

    private @Nullable EntityTrackerRegistry ownModel() {
        return this.registryOf(this.player.getEntityId());
    }

    void outgoing(@NotNull PlayerPacketOutEvent event) {
        if (this.closed) {
            return;
        }
        ServerPacket packet = event.getPacket();
        ServerPacket replacement = null;

        switch (packet) {
            case SpawnEntityPacket spawn -> this.spawned(spawn.entityId());
            case DestroyEntitiesPacket(List<Integer> entityIds) -> {
                for (int id : entityIds) {
                    EntityTrackerRegistry registry = this.registryOf(id);
                    if (registry != null) {
                        registry.remove(new MinestomPlayer(this.player));
                    }
                }
            }
            case SetPassengersPacket passengers -> replacement = this.passengers(passengers);
            case EntityMetaDataPacket data -> {
                EntityTrackerRegistry registry = this.registryOf(data.entityId());
                if (registry != null) {
                    replacement = Packets.rewriteFlags(data, registry, this.player.getUuid());
                }
            }
            case EntityEquipmentPacket equipment -> replacement = this.equipment(equipment);
            case RespawnPacket _ -> {
                EntityTrackerRegistry registry = this.ownModel();
                if (registry != null) {
                    MinestomScheduler.INSTANCE.taskLater(1L, () -> this.player.sendPacket(Packets.mountPacket(registry, this.player)));
                }
            }
            case SetSlotPacket slot -> {
                if (slot.windowId() == 0 && this.hidesOwnEquipment()) {
                    replacement = this.slot(slot);
                }
            }
            case WindowItemsPacket items -> {
                if (items.windowId() == 0 && this.hidesOwnEquipment()) {
                    replacement = this.items(items);
                }
            }
            default -> {
            }
        }

        if (replacement != null) {
            event.setCancelled(true);
            this.player.sendPacket(replacement);
        }
    }

    void spawnViewed() {
        Instance instance = this.player.getInstance();
        if (instance == null) {
            return;
        }
        for (Entity entity : instance.getEntities()) {
            if (entity == this.player || !entity.isViewer(this.player)) {
                continue;
            }
            this.spawned(entity.getEntityId());
        }
    }

    private void spawned(int entityId) {
        EntityTrackerRegistry registry = EntityTrackerRegistry.registry(entityId);
        if (registry == null) {
            return;
        }
        MinestomScheduler.INSTANCE.taskLater(1L, () -> registry.spawnIfNotSpawned(new MinestomPlayer(this.player)));
    }

    private @Nullable ServerPacket passengers(@NotNull SetPassengersPacket packet) {
        EntityTrackerRegistry registry = this.registryOf(packet.vehicleEntityId());
        if (registry == null || !(registry.entity().handle() instanceof Entity entity)) {
            return null;
        }
        Set<Integer> displays = new HashSet<>();
        registry.displays().mapToInt(Identifiable::id).forEach(displays::add);
        List<Integer> others = new ArrayList<>();
        for (int id : packet.passengersId()) {
            if (!displays.contains(id)) {
                others.add(id);
            }
        }
        SetPassengersPacket mounted = Packets.mountPacket(registry, entity, others);
        return mounted.passengersId().equals(packet.passengersId()) ? null : mounted;
    }

    private @Nullable ServerPacket equipment(@NotNull EntityEquipmentPacket packet) {
        EntityTrackerRegistry registry = this.registryOf(packet.entityId());
        if (registry == null || !registry.hideOption(this.player.getUuid()).equipment()) {
            return null;
        }
        if (packet.equipments().values().stream().allMatch(ItemStack::isAir)) {
            return null;
        }
        EnumMap<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
        packet.equipments().forEach((slot, item) -> empty.put(slot, ItemStack.AIR));
        return new EntityEquipmentPacket(packet.entityId(), empty);
    }

    private boolean hidesOwnEquipment() {
        EntityTrackerRegistry registry = this.ownModel();
        return registry != null && registry.hideOption(this.player.getUuid()).equipment();
    }

    private boolean isNotEquipmentSlot(int slot) {
        return !EQUIPMENT_SLOTS.contains(slot) && slot != 36 + this.player.getHeldSlot();
    }

    private @Nullable ServerPacket slot(@NotNull SetSlotPacket packet) {
        if (this.isNotEquipmentSlot(packet.slot()) || packet.itemStack().isAir()) {
            return null;
        }
        return new SetSlotPacket(packet.windowId(), packet.stateId(), packet.slot(), ItemStack.AIR);
    }

    private @Nullable ServerPacket items(@NotNull WindowItemsPacket packet) {
        List<ItemStack> items = null;
        for (int slot = 0; slot < packet.items().size(); ++slot) {
            if (this.isNotEquipmentSlot(slot) || packet.items().get(slot).isAir()) {
                continue;
            }
            if (items == null) {
                items = new ArrayList<>(packet.items());
            }
            items.set(slot, ItemStack.AIR);
        }
        return items != null ? new WindowItemsPacket(packet.windowId(), packet.stateId(), items, packet.carriedItem()) : null;
    }

    void incoming(@NotNull PlayerPacketEvent event) {
        if (this.closed) {
            return;
        }
        if (!(event.getPacket() instanceof ClientHeldItemChangePacket) && !(event.getPacket() instanceof ClientPlayerActionPacket)) {
            return;
        }
        EntityTrackerRegistry registry = this.ownModel();
        if (registry == null || !registry.hideOption(this.player.getUuid()).equipment()) {
            return;
        }
        if (BetterModel.config().cancelPlayerModelInventory()) {
            event.setCancelled(true);
            if (event.getPacket() instanceof ClientHeldItemChangePacket) {
                this.player.sendPacket(new HeldItemChangePacket(this.player.getHeldSlot()));
            }
            return;
        }
        BetterModel.platform().scheduler().asyncTaskLater(1L, () -> {
            if (registry.isClosed()) {
                return;
            }
            this.player.getInventory().update();
            registry.trackers().forEach(tracker -> tracker.update(TrackerUpdateAction.itemMapping(), bone -> !bone.getItemMapper().fixed()));
        });
    }

    @Override
    public @NotNull String toString() {
        return "PlayerChannelHandler[" + this.player.getUsername() + "]";
    }
}
