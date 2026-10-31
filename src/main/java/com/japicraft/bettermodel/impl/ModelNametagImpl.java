package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.bone.BoneMovement;
import kr.toxicity.model.api.bone.BonePosition;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.nms.ModelNametag;
import kr.toxicity.model.api.nms.PacketBundler;
import kr.toxicity.model.api.platform.PlatformBillboard;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.util.EntityUtil;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.network.packet.server.play.DestroyEntitiesPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import net.minestom.server.network.packet.server.play.EntityTeleportPacket;
import net.minestom.server.network.packet.server.play.SpawnEntityPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

final class ModelNametagImpl implements ModelNametag {
    private static final Vector3f NO_OFFSET = new Vector3f();
    private static final Vec NAME_OFFSET = new Vec(-0.025, -0.225, 0.0);
    private final RenderedBone bone;
    private final int id = Entity.generateId();
    private final UUID uuid = UUID.randomUUID();
    private final EntityData data = new EntityData();
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final BoneMovement movement = new BoneMovement();
    private volatile @Nullable Component text;
    private volatile boolean alwaysVisible;
    private volatile @NotNull PlatformLocation location = BetterModel.platform().adapter().zero();

    ModelNametagImpl(@NotNull RenderedBone bone) {
        this.bone = bone;
        this.data.set(MetadataDef.Display.POSITION_ROTATION_INTERPOLATION_DURATION, Metadata.VarInt(3));
        this.data.set(MetadataDef.Display.TRANSLATION, Metadata.Vector3(NAME_OFFSET));
        this.data.set(MetadataDef.Display.BILLBOARD_CONSTRAINTS, Metadata.Byte((byte) PlatformBillboard.CENTER.ordinal()));
    }

    @Override
    public void alwaysVisible(boolean alwaysVisible) {
        this.alwaysVisible = alwaysVisible;
    }

    @Override
    public void component(@Nullable Component component) {
        this.text = component;
        this.data.set(MetadataDef.TextDisplay.TEXT, Metadata.Component(component == null ? Component.empty() : component));
    }

    @Override
    public void teleport(@NotNull PlatformLocation location) {
        this.location = location;
    }

    @Override
    public void send(@NotNull PlatformPlayer player) {
        Component text = this.text;
        if (text == null || Component.empty().equals(text)) {
            return;
        }
        Vector3f offset;
        synchronized (this.movement) {
            offset = new Vector3f(this.bone.worldPosition(new BonePosition(NO_OFFSET, this.bone.getGroup().getHitBoxPoint(), player.uuid()), this.movement));
        }
        Pos pos = new Pos(this.location.x() + (double) offset.x, this.location.y() + (double) offset.y, this.location.z() + (double) offset.z);
        boolean visible = this.alwaysVisible || EntityUtil.isCustomNameVisible(player.location(), this.location);
        Bundlers.Simple bundler = Bundlers.of(2);
        if (this.viewers.add(player.uuid())) {
            if (visible) {
                bundler.add(new SpawnEntityPacket(this.id, this.uuid, EntityType.TEXT_DISPLAY, pos, 0.0f, 0, Vec.ZERO));
                Map<Integer, Metadata.Entry<?>> all = this.data.all(_ -> true);
                if (!all.isEmpty()) {
                    bundler.add(new EntityMetaDataPacket(this.id, all));
                }
            }
        } else if (visible) {
            bundler.add(new EntityTeleportPacket(this.id, pos, Vec.ZERO, 0, false));
            Map<Integer, Metadata.Entry<?>> dirty = this.data.takeDirty(_ -> true);
            if (!dirty.isEmpty()) {
                bundler.add(new EntityMetaDataPacket(this.id, dirty));
            }
        } else if (this.viewers.remove(player.uuid())) {
            bundler.add(new DestroyEntitiesPacket(this.id));
        }
        if (!bundler.isEmpty()) {
            bundler.send(MinestomAdapter.player(player));
        }
    }

    @Override
    public void remove(@NotNull PacketBundler bundler) {
        Bundlers.add(bundler, new DestroyEntitiesPacket(this.id));
        this.viewers.clear();
    }
}
