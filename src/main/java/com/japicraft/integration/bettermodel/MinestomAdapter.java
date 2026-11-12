package com.japicraft.bettermodel;

import kr.toxicity.model.api.platform.*;
import net.minestom.server.MinecraftServer;
import net.minestom.server.component.DataComponents;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.item.ItemStack;
import net.minestom.server.property.ServerProperties;
import net.minestom.server.thread.TickThread;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class MinestomAdapter implements PlatformAdapter {
    public static final MinestomAdapter INSTANCE = new MinestomAdapter();

    private MinestomAdapter() {
    }

    public static @NotNull PlatformEntity adapt(@NotNull Entity entity) {
        Objects.requireNonNull(entity);
        if (entity instanceof Player player) {
            return new MinestomPlayer(player);
        } else if (entity instanceof LivingEntity living) {
            return new MinestomLivingEntity(living);
        } else {
            return new MinestomEntity(entity);
        }
    }

    public static @NotNull PlatformPlayer adapt(@NotNull Player player) {
        return new MinestomPlayer(player);
    }

    public static @NotNull PlatformItemStack adapt(@NotNull ItemStack itemStack) {
        return new MinestomItemStack(itemStack);
    }

    public static @NotNull PlatformWorld adapt(@NotNull Instance instance) {
        return new MinestomWorld(instance);
    }

    public static @NotNull Entity entity(@NotNull PlatformEntity entity) {
        Objects.requireNonNull(entity);
        return switch (entity) {
            case MinestomPlayer player -> player.source();
            case MinestomLivingEntity living -> living.source();
            case MinestomEntity plain -> plain.source();
            default -> throw new IllegalArgumentException("Not a Minestom entity: " + entity);
        };
    }

    public static @NotNull Player player(@NotNull PlatformPlayer player) {
        return ((MinestomPlayer) player).source();
    }

    public static @NotNull ItemStack itemStack(@NotNull PlatformItemStack itemStack) {
        return ((MinestomItemStack) itemStack).source();
    }

    @Override
    public int serverViewDistance() {
        return ServerProperties.CHUNK_VIEW_DISTANCE.get();
    }

    @Override
    public boolean isTickThread() {
        return Thread.currentThread() instanceof TickThread;
    }

    @Override
    public boolean isRegionSafe() {
        return true;
    }

    @Override
    public @Nullable PlatformPlayer player(@NotNull UUID uuid) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        return player == null ? null : new MinestomPlayer(player);
    }

    @Override
    public @NotNull PlatformOfflinePlayer offlinePlayer(@NotNull UUID uuid) {
        Player player = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(uuid);
        return player != null ? new MinestomPlayer(player) : new MinestomOfflinePlayer(uuid, null);
    }

    @Override
    public @NotNull PlatformItemStack air() {
        return new MinestomItemStack(ItemStack.AIR);
    }

    @Override
    public @NotNull PlatformLocation zero() {
        return new MinestomLocation(null, 0.0, 0.0, 0.0, 0.0f, 0.0f);
    }

    // --- Embedded Platform Implementations ---

    public record MinestomEntity(@NotNull Entity source) implements PlatformEntity {
        @Override
        public @NotNull UUID uuid() {
            return this.source.getUuid();
        }

        @Override
        public @NotNull PlatformLocation location() {
            return MinestomLocation.of(this.source);
        }
    }

    public record MinestomLivingEntity(@NotNull LivingEntity source) implements PlatformLivingEntity {
        @Override
        public @NotNull UUID uuid() {
            return this.source.getUuid();
        }

        @Override
        public @NotNull PlatformLocation location() {
            return MinestomLocation.of(this.source);
        }

        @Override
        public @NotNull PlatformLocation eyeLocation() {
            return MinestomLocation.ofEye(this.source);
        }
    }

    public record MinestomOfflinePlayer(@NotNull UUID uuid, @Nullable String name) implements PlatformOfflinePlayer {
    }

    public record MinestomWorld(@NotNull Instance instance) implements PlatformWorld {
    }

    public static final class MinestomItemStack implements PlatformItemStack {
        private @NotNull ItemStack source;

        public MinestomItemStack(@NotNull ItemStack source) {
            this.source = source;
        }

        public @NotNull ItemStack source() {
            return this.source;
        }

        @Override
        public boolean isAir() {
            return this.source.isAir();
        }

        @Override
        public @NotNull PlatformItemStack enchant(boolean enchant) {
            this.source = this.source.with(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, enchant);
            return this;
        }

        @Override
        public @NotNull PlatformItemStack itemModel(@Nullable PlatformNamespace namespace) {
            this.source = namespace == null
                ? this.source.without(DataComponents.ITEM_MODEL)
                : this.source.with(DataComponents.ITEM_MODEL, namespace.namespace() + ":" + namespace.path());
            return this;
        }

        @SuppressWarnings("MethodDoesntCallSuperMethod")
        @Override
        public @NotNull PlatformItemStack clone() {
            return new MinestomItemStack(this.source);
        }
    }
}
