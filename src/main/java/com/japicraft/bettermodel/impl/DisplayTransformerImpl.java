package com.japicraft.bettermodel.impl;

import java.util.HashMap;
import java.util.Map;
import kr.toxicity.model.api.nms.AnimationBundler;
import kr.toxicity.model.api.nms.DisplayTransformer;
import kr.toxicity.model.api.nms.PacketBundler;
import kr.toxicity.model.api.util.MathUtil;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class DisplayTransformerImpl implements DisplayTransformer {
    private final int id;
    private final Vector3f translation = new Vector3f();
    private final Vector3f scale = new Vector3f();
    private final Quaternionf rotation = new Quaternionf();
    private int duration;
    private boolean translationDirty;
    private boolean scaleDirty;
    private boolean rotationDirty;

    DisplayTransformerImpl(int id) {
        this.id = id;
    }

    @Override
    public synchronized void transform(int duration, @NotNull Vector3f position, @NotNull Vector3f scale, @NotNull Quaternionf rotation, @NotNull AnimationBundler bundler) {
        this.duration = duration;
        if (!MathUtil.isSimilar(this.translation, position)) {
            this.translation.set(position);
            this.translationDirty = true;
        }
        if (!MathUtil.isSimilar(this.scale, scale)) {
            this.scale.set(scale);
            this.scaleDirty = true;
        }
        if (!MathUtil.isSimilar(this.rotation, rotation)) {
            this.rotation.set(rotation);
            this.rotationDirty = true;
        }
        if (!this.translationDirty && !this.scaleDirty && !this.rotationDirty) {
            return;
        }
        Map<Integer, Metadata.Entry<?>> entries = new HashMap<>();
        entries.put(MetadataDef.Display.INTERPOLATION_DELAY.index(), Metadata.VarInt(0));
        if (this.translationDirty) {
            entries.put(MetadataDef.Display.TRANSLATION.index(), this.translationEntry());
        }
        if (this.rotationDirty) {
            entries.put(MetadataDef.Display.ROTATION_LEFT.index(), this.rotationEntry());
        }
        if (this.scaleDirty) {
            entries.put(MetadataDef.Display.SCALE.index(), this.scaleEntry());
        }
        entries.put(MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index(), Metadata.VarInt(duration));
        this.rotationDirty = false;
        this.scaleDirty = false;
        this.translationDirty = false;
        Bundlers.add(bundler.standard(), new EntityMetaDataPacket(this.id, entries));
    }

    @Override
    public synchronized void sendTransformation(@NotNull PacketBundler bundler) {
        Map<Integer, Metadata.Entry<?>> entries = new HashMap<>();
        entries.put(MetadataDef.Display.INTERPOLATION_DELAY.index(), Metadata.VarInt(0));
        entries.put(MetadataDef.Display.TRANSFORMATION_INTERPOLATION_DURATION.index(), Metadata.VarInt(this.duration));
        entries.put(MetadataDef.Display.TRANSLATION.index(), this.translationEntry());
        entries.put(MetadataDef.Display.SCALE.index(), this.scaleEntry());
        entries.put(MetadataDef.Display.ROTATION_LEFT.index(), this.rotationEntry());
        Bundlers.add(bundler, new EntityMetaDataPacket(this.id, entries));
    }

    private @NotNull Metadata.Entry<?> translationEntry() {
        return Metadata.Vector3(new Vec(this.translation.x, this.translation.y, this.translation.z));
    }

    private @NotNull Metadata.Entry<?> scaleEntry() {
        return Metadata.Vector3(new Vec(this.scale.x, this.scale.y, this.scale.z));
    }

    private @NotNull Metadata.Entry<?> rotationEntry() {
        return Metadata.Quaternion(new float[]{this.rotation.x, this.rotation.y, this.rotation.z, this.rotation.w});
    }
}
