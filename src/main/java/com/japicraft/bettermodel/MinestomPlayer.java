package com.japicraft.bettermodel;

import java.util.UUID;
import kr.toxicity.model.api.platform.PlatformLocation;
import kr.toxicity.model.api.platform.PlatformPlayer;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

public record MinestomPlayer(@NotNull Player source) implements PlatformPlayer {
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

    @Override
    public @NotNull String name() {
        return this.source.getUsername();
    }
}
