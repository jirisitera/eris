package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.io.File;
import java.util.function.Supplier;
import kr.toxicity.model.api.BetterModelConfig;
import kr.toxicity.model.api.config.DebugConfig;
import kr.toxicity.model.api.config.IndicatorConfig;
import kr.toxicity.model.api.config.ModuleConfig;
import kr.toxicity.model.api.config.PackConfig;
import kr.toxicity.model.api.mount.MountController;
import kr.toxicity.model.api.mount.MountControllers;
import kr.toxicity.model.api.platform.PlatformItemStack;
import kr.toxicity.model.api.util.EntityUtil;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.jetbrains.annotations.NotNull;

final class MinestomConfig implements BetterModelConfig {
    private final DebugConfig debug = DebugConfig.from(_ -> false);
    private final IndicatorConfig indicator = IndicatorConfig.from(_ -> false);
    private final ModuleConfig module = new ModuleConfig(false, true, false);
    private final PackConfig pack = new PackConfig(false);
    private final Material itemMaterial = Material.LEATHER_HORSE_ARMOR;
    private final double maxSight = EntityUtil.renderDistance();
    private final BetterModelConfig.PackType packType = BetterModelConfig.PackType.NONE;
    private final MountController defaultMountController = MountControllers.WALK;

    MinestomConfig() {
    }

    @Override public @NotNull DebugConfig debug() { return this.debug; }
    @Override public @NotNull IndicatorConfig indicator() { return this.indicator; }
    @Override public @NotNull ModuleConfig module() { return this.module; }
    @Override public @NotNull PackConfig pack() { return this.pack; }
    @Override public boolean metrics() { return false; }
    @Override public boolean sightTrace() { return true; }
    @Override public boolean mergeWithExternalResources() { return false; }
    @Override public @NotNull Supplier<PlatformItemStack> item() { return () -> MinestomAdapter.adapt(ItemStack.of(this.itemMaterial)); }
    @Override public double maxSight() { return this.maxSight; }
    @Override public double minSight() { return 5.0; }
    @Override public @NotNull String namespace() { return "bettermodel"; }
    @Override public @NotNull BetterModelConfig.PackType packType() { return this.packType; }
    @Override public @NotNull String buildFolderLocation() { return "bettermodel" + File.separator + "build"; }
    @Override public boolean followMobInvisibility() { return true; }
    @Override public boolean usePurpurAfk() { return false; }
    @Override public boolean versionCheck() { return false; }
    @Override public @NotNull MountController defaultMountController() { return this.defaultMountController; }
    @Override public int lerpFrameTime() { return 3; }
    @Override public boolean cancelPlayerModelInventory() { return false; }
    @Override public long playerHideDelay() { return 3L; }
    @Override public int packetBundlingSize() { return 16; }
    @Override public boolean enableStrictLoading() { return false; }
}
