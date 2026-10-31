package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.nms.DisplayTransformer;
import kr.toxicity.model.api.nms.ModelDisplay;
import kr.toxicity.model.api.nms.PacketBundler;
import kr.toxicity.model.api.platform.PlatformBillboard;
import kr.toxicity.model.api.platform.PlatformItemStack;
import kr.toxicity.model.api.platform.PlatformItemTransform;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.tracker.ModelRotation;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityRotationPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.jetbrains.annotations.NotNull;

final class ModelDisplayImpl implements ModelDisplay {
    private static final Set<Integer> SENT = Set.of(
        MetadataDef.ENTITY_FLAGS.index(),
        MetadataDef.Display.POSITION_ROTATION_INTERPOLATION_DURATION.index(),
        MetadataDef.Display.BILLBOARD_CONSTRAINTS.index(),
        MetadataDef.Display.BRIGHTNESS_OVERRIDE.index(),
        MetadataDef.Display.VIEW_RANGE.index(),
        MetadataDef.Display.SHADOW_RADIUS.index(),
        MetadataDef.Display.SHADOW_STRENGTH.index(),
        MetadataDef.Display.WIDTH.index(),
        MetadataDef.Display.HEIGHT.index(),
        MetadataDef.Display.GLOW_COLOR_OVERRIDE.index(),
        MetadataDef.ItemDisplay.DISPLAYED_ITEM.index(),
        MetadataDef.ItemDisplay.DISPLAY_TYPE.index()
    );
    private static final int ITEM_INDEX = MetadataDef.ItemDisplay.DISPLAYED_ITEM.index();

    private final int id = Entity.generateId();
    private final UUID uuid = UUID.randomUUID();
    private final double yOffset;
    private final EntityData data = new EntityData();

    private double x, y, z;
    private double oldX, oldY, oldZ;
    private float yaw, pitch;
    private byte flags;
    private ItemStack item = ItemStack.AIR;
    private boolean forceGlow;
    private boolean forceInvisible;

    ModelDisplayImpl(@NotNull PlatformLocation location, double yOffset) {
        this.x = this.oldX = location.x();
        this.y = this.oldY = location.y();
        this.z = this.oldZ = location.z();
        this.yaw = location.yaw();
        this.yOffset = yOffset;
        this.data.set(MetadataDef.Display.BILLBOARD_CONSTRAINTS, Metadata.Byte((byte) PlatformBillboard.FIXED.ordinal()));
        this.data.set(MetadataDef.Display.POSITION_ROTATION_INTERPOLATION_DURATION, Metadata.VarInt(3));
        this.data.set(MetadataDef.ItemDisplay.DISPLAY_TYPE, Metadata.Byte((byte) PlatformItemTransform.FIXED.ordinal()));
        this.data.set(MetadataDef.ItemDisplay.DISPLAYED_ITEM, Metadata.ItemStack(this.item));
    }

    @Override
    public int id() {
        return this.id;
    }

    @Override
    public @NotNull UUID uuid() {
        return this.uuid;
    }

    @Override
    public synchronized boolean invisible() {
        return (this.flags & 0x20) != 0 || this.forceInvisible || this.item.isAir();
    }

    @Override
    public synchronized void invisible(boolean invisible) {
        if (this.forceInvisible == invisible) {
            return;
        }
        this.forceInvisible = invisible;
        this.data.markDirty();
    }

    @Override
    public synchronized void rotate(@NotNull ModelRotation rotation, @NotNull PacketBundler bundler) {
        this.pitch = rotation.x();
        this.yaw = rotation.y();
        Bundlers.add(bundler, new EntityRotationPacket(this.id, this.yaw, this.pitch, false));
    }

    @Override
    public synchronized void syncPotionEffect(@NotNull BaseEntity entity) {
        this.setFlag((byte) 64, entity.glow() || this.forceGlow);
        boolean invisible = entity.invisible();
        if (BetterModel.config().followMobInvisibility() && ((this.flags & 0x20) == 0) == invisible) {
            this.setFlag((byte) 32, invisible);
            this.data.markDirty();
        }
    }

    private void setFlag(byte flag, boolean on) {
        byte updated = on ? (byte) (this.flags | flag) : (byte) (this.flags & ~flag);
        if (updated == this.flags) {
            return;
        }
        this.flags = updated;
        this.data.set(MetadataDef.ENTITY_FLAGS, Metadata.Byte(this.flags));
    }

    @Override
    public synchronized void syncPosition(@NotNull PlatformLocation location) {
        this.oldX = this.x;
        this.oldY = this.y;
        this.oldZ = this.z;
        this.x = location.x();
        this.y = location.y();
        this.z = location.z();
    }

    @Override
    public void moveDuration(int duration) {
        this.data.set(MetadataDef.Display.POSITION_ROTATION_INTERPOLATION_DURATION, Metadata.VarInt(duration));
    }

    @Override
    public void display(@NotNull PlatformItemTransform transform) {
        this.data.set(MetadataDef.ItemDisplay.DISPLAY_TYPE, Metadata.Byte((byte) transform.ordinal()));
    }

    @Override
    public synchronized void spawn(boolean showItem, @NotNull PacketBundler bundler) {
        Bundlers.add(bundler, new SpawnEntityPacket(this.id, this.uuid, EntityType.ITEM_DISPLAY, new Pos(this.x, this.y + this.yOffset, this.z, this.yaw, this.pitch), this.yaw, 0, Vec.ZERO));
    }

    @Override
    public void remove(@NotNull PacketBundler bundler) {
        Bundlers.add(bundler, new DestroyEntitiesPacket(this.id));
    }

    @Override
    public synchronized void teleport(@NotNull PlatformLocation location, @NotNull PacketBundler bundler) {
        this.x = location.x();
        this.y = location.y();
        this.z = location.z();
        this.yaw = location.yaw();
        this.pitch = 0.0f;
        Bundlers.add(bundler, new EntityTeleportPacket(this.id, new Pos(this.x, this.y, this.z, this.yaw, this.pitch), Vec.ZERO, 0, false));
    }

    @Override
    public synchronized void item(@NotNull PlatformItemStack itemStack) {
        this.item = MinestomAdapter.itemStack(itemStack);
        this.data.set(MetadataDef.ItemDisplay.DISPLAYED_ITEM, Metadata.ItemStack(this.item));
    }

    @Override
    public @NotNull DisplayTransformer createTransformer() {
        return new DisplayTransformerImpl(this.id);
    }

    @Override
    public void sendDirtyEntityData(@NotNull PacketBundler bundler) {
        Map<Integer, Metadata.Entry<?>> dirty = this.data.takeDirty(SENT::contains);
        if (dirty.isEmpty()) {
            return;
        }
        Bundlers.add(bundler, new EntityMetaDataPacket(this.id, this.markVisible(dirty, !this.invisible())));
    }

    @Override
    public void sendEntityData(boolean showItem, @NotNull PacketBundler bundler) {
        Map<Integer, Metadata.Entry<?>> all = this.data.all(SENT::contains);
        if (all.isEmpty()) {
            return;
        }
        Bundlers.add(bundler, new EntityMetaDataPacket(this.id, this.markVisible(all, showItem && !this.invisible())));
    }

    private @NotNull Map<Integer, Metadata.Entry<?>> markVisible(@NotNull Map<Integer, Metadata.Entry<?>> entries, boolean showItem) {
        if (entries.containsKey(ITEM_INDEX)) {
            entries.put(ITEM_INDEX, Metadata.ItemStack(showItem ? this.item : ItemStack.AIR));
        }
        return entries;
    }

    @Override
    public void brightness(int block, int sky) {
        int packed = block < 0 && sky < 0 ? -1 : Math.max(block, 0) << 4 | Math.max(sky, 0) << 20;
        this.data.set(MetadataDef.Display.BRIGHTNESS_OVERRIDE, Metadata.VarInt(packed));
    }

    @Override
    public void viewRange(float range) {
        this.data.set(MetadataDef.Display.VIEW_RANGE, Metadata.Float(range));
    }

    @Override
    public void shadowRadius(float radius) {
        this.data.set(MetadataDef.Display.SHADOW_RADIUS, Metadata.Float(radius));
    }

    @Override
    public synchronized void sendPosition(@NotNull BaseEntity adapter, @NotNull PacketBundler bundler) {
        double dx = this.oldX - this.x;
        double dy = this.oldY - this.y;
        double dz = this.oldZ - this.z;
        if (dx * dx + dy * dy + dz * dz < 1.0E-8) {
            return;
        }
        Pos position = MinestomAdapter.entity(adapter.platform()).getPosition();
        Bundlers.add(bundler, new EntityTeleportPacket(this.id, position, Vec.ZERO, 0, adapter.ground()));
    }

    @Override
    public synchronized void glow(boolean glow) {
        if (this.forceGlow == glow) {
            return;
        }
        this.forceGlow = glow;
        this.setFlag((byte) 64, (this.flags & 0x40) != 0 || glow);
    }

    @Override
    public void glowColor(int glowColor) {
        this.data.set(MetadataDef.Display.GLOW_COLOR_OVERRIDE, Metadata.VarInt(glowColor));
    }

    @Override
    public void billboard(@NotNull PlatformBillboard billboard) {
        this.data.set(MetadataDef.Display.BILLBOARD_CONSTRAINTS, Metadata.Byte((byte) billboard.ordinal()));
    }
}
