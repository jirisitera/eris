package com.japicraft.bettermodel;

import com.japicraft.bettermodel.impl.MinestomScheduler;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformWorld;
import kr.toxicity.model.api.scheduler.ModelTask;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record MinestomLocation(@Nullable Instance instance, double x, double y, double z, float pitch, float yaw) implements PlatformLocation {
    public static @NotNull MinestomLocation of(@Nullable Instance instance, @NotNull Pos pos) {
        return new MinestomLocation(instance, pos.x(), pos.y(), pos.z(), pos.pitch(), pos.yaw());
    }

    public static @NotNull MinestomLocation of(@NotNull Entity entity) {
        return MinestomLocation.of(entity.getInstance(), entity.getPosition());
    }

    public static @NotNull MinestomLocation ofEye(@NotNull Entity entity) {
        Pos pos = entity.getPosition();
        return new MinestomLocation(entity.getInstance(), pos.x(), pos.y() + entity.getEyeHeight(), pos.z(), pos.pitch(), pos.yaw());
    }

    @Override
    public @NotNull PlatformWorld world() {
        if (this.instance == null) {
            throw new IllegalStateException("The location has no instance");
        }
        return MinestomAdapter.adapt(this.instance);
    }

    @Override
    public @NotNull PlatformLocation add(double x, double y, double z) {
        return new MinestomLocation(this.instance, this.x + x, this.y + y, this.z + z, this.pitch, this.yaw);
    }

    @Override
    public @Nullable ModelTask task(@NotNull Runnable runnable) {
        return MinestomScheduler.INSTANCE.task(runnable);
    }

    @Override
    public @Nullable ModelTask taskLater(long delay, @NotNull Runnable runnable) {
        return MinestomScheduler.INSTANCE.taskLater(delay, runnable);
    }
}
