package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.ModelBoundingBox;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.entity.BasePlayer;
import kr.toxicity.model.api.mount.MountController;
import kr.toxicity.model.api.nms.*;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.platform.PlatformItemStack;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.profile.ModelProfile;
import kr.toxicity.model.api.tracker.EntityTrackerRegistry;
import kr.toxicity.model.api.util.TransformedItemStack;
import net.kyori.adventure.util.RGBLike;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.color.Color;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Metadata;
import net.minestom.server.entity.Player;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.item.component.CustomModelData;
import net.minestom.server.network.packet.server.play.EntityEquipmentPacket;
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class MinestomNMS implements NMS {
    private static @NotNull Color multiply(@NotNull RGBLike color, int rgb) {
        return new Color(color.red() * (rgb >> 16 & 0xFF) / 255, color.green() * (rgb >> 8 & 0xFF) / 255, color.blue() * (rgb & 0xFF) / 255);
    }

    @Override
    public @NotNull ModelDisplay create(@NotNull PlatformLocation location, double yOffset, @NotNull Consumer<ModelDisplay> initialConsumer) {
        ModelDisplayImpl display = new ModelDisplayImpl(location, yOffset);
        initialConsumer.accept(display);
        return display;
    }

    @Override
    public @NotNull ModelNametag createNametag(@NotNull RenderedBone bone) {
        return new ModelNametagImpl(bone);
    }

    @Override
    @SuppressWarnings("resource")
    public @NotNull PlayerChannelHandler inject(@NotNull PlatformPlayer player) {
        return new PlayerChannelHandlerImpl(MinestomAdapter.player(player));
    }

    @Override
    public @NotNull PacketBundler createBundler(int initialCapacity) {
        return Bundlers.of(initialCapacity);
    }

    @Override
    public @NotNull PacketBundler createParallelBundler(int threshold) {
        return new Bundlers.Parallel(threshold);
    }

    @Override
    public @NotNull ModAnimationBundler createModAnimationBuilder(int initialCapacity) {
        return Bundlers.NoMod.INSTANCE;
    }

    @Override
    public @NotNull PlatformItemStack tint(@NotNull PlatformItemStack itemStack, int rgb) {
        ItemStack item = MinestomAdapter.itemStack(itemStack);
        CustomModelData data = item.get(DataComponents.CUSTOM_MODEL_DATA);
        List<RGBLike> colors = data == null || data.colors().isEmpty()
            ? List.of(new Color(rgb))
            : (rgb == 0xFFFFFF ? data.colors() : data.colors().stream().<RGBLike>map(color -> multiply(color, rgb)).toList());
        CustomModelData tinted = data == null
            ? new CustomModelData(List.of(), List.of(), List.of(), colors)
            : new CustomModelData(data.floats(), data.flags(), data.strings(), colors);
        return MinestomAdapter.adapt(item.with(DataComponents.CUSTOM_MODEL_DATA, tinted));
    }

    @Override
    public void mount(@NotNull EntityTrackerRegistry registry, @NotNull PacketBundler bundler) {
        if (registry.entity().handle() instanceof Entity entity) {
            Bundlers.add(bundler, Packets.mountPacket(registry, entity));
        }
    }

    @Override
    public void hide(@NotNull PlayerChannelHandler channel, @NotNull EntityTrackerRegistry registry) {
        if (!(registry.entity().handle() instanceof Entity target)) {
            return;
        }
        Bundlers.Simple bundler = Bundlers.of(2);
        EntityMetaDataPacket flags = new EntityMetaDataPacket(target.getEntityId(), Map.of(Packets.FLAGS_INDEX, Metadata.Byte(Packets.entityFlags(registry, channel.uuid(), Packets.currentFlags(target)))));
        bundler.add(flags);
        if (target instanceof LivingEntity living) {
            boolean hideEquipment = registry.hideOption(channel.uuid()).equipment();
            EntityEquipmentPacket packet = Packets.equipmentPacket(living, slot -> hideEquipment ? ItemStack.AIR : living.getEquipment(slot));
            if (packet != null) {
                bundler.add(packet);
            }
        }
        bundler.send(MinestomAdapter.player(channel.player()));
    }

    @Override
    public @Nullable HitBox createHitBox(@NotNull BaseEntity entity, @NotNull RenderedBone bone, @NotNull ModelBoundingBox boundingBox, @NotNull MountController controller, @NotNull HitBoxListener listener) {
        return null;
    }

    @Override
    public @NotNull NMSVersion version() {
        return NMSVersion.V26_R2;
    }

    @Override
    public @NotNull BaseEntity adapt(@NotNull PlatformEntity entity) {
        Entity handle = MinestomAdapter.entity(entity);
        return handle instanceof Player player ? new BasePlayerImpl(player) : new BaseEntityImpl(handle);
    }

    @Override
    public @NotNull BasePlayer adapt(@NotNull PlatformPlayer player) {
        return new BasePlayerImpl(MinestomAdapter.player(player));
    }

    @Override
    public @NotNull ModelProfile profile(@NotNull PlatformPlayer player) {
        return BasePlayerImpl.profileOf(MinestomAdapter.player(player));
    }

    @Override
    public @NotNull TransformedItemStack createSkinItem(@NotNull String model, @NotNull List<Float> floats, @NotNull List<Boolean> flags, @NotNull List<String> strings, @NotNull List<Integer> colors) {
        List<RGBLike> rgbColors = colors.stream().<RGBLike>map(Color::new).toList();
        ItemStack item = ItemStack.builder(Material.PLAYER_HEAD)
            .set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(floats, flags, strings, rgbColors))
            .set(DataComponents.ITEM_MODEL, model)
            .build();
        return TransformedItemStack.of(MinestomAdapter.adapt(item));
    }

    @Override
    public boolean isProxyOnlineMode() {
        return !(MinecraftServer.process().auth() instanceof Auth.Offline);
    }
}
