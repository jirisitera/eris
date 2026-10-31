package com.japicraft.bettermodel;

import com.japicraft.bettermodel.impl.MinestomBetterModel;
import java.io.File;

import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelPlatform;
import net.minestom.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BetterModelMinestom {
    private static final Logger log = LoggerFactory.getLogger("bettermodel");
    private static final String DATA_FOLDER_PROPERTY = "bettermodel.folder";
    private static @Nullable MinestomBetterModel platform;
    private static boolean started;

    private BetterModelMinestom() {
    }

    public static synchronized void register() {
        if (started) {
            throw new IllegalStateException("BetterModel can only be started once per JVM.");
        }
        started = true;
        MinestomBetterModel modelPlatform = new MinestomBetterModel(new File(System.getProperty(DATA_FOLDER_PROPERTY, "bettermodel")));
        modelPlatform.enable();
        platform = modelPlatform;
        MinecraftServer.getSchedulerManager().buildShutdownTask(BetterModelMinestom::shutdown);
        BetterModelPlatform.ReloadResult result = modelPlatform.reload();
        switch (result) {
            case BetterModelPlatform.ReloadResult.Success success -> {
                int total = BetterModel.models().size() + BetterModel.limbs().size();
                log.info("BetterModel {} loaded {} models in {} ms", modelPlatform.semver(), total, success.totalTime());
            }
            case BetterModelPlatform.ReloadResult.Failure(Throwable throwable) -> log.error("BetterModel failed to load its models", throwable);
            case BetterModelPlatform.ReloadResult.OnReload _ -> log.warn("BetterModel was already loading");
            default -> {
            }
        }
    }

    public static synchronized void shutdown() {
        MinestomBetterModel running = platform;
        if (running == null) {
            return;
        }
        platform = null;
        running.disable();
    }

    public static @NotNull BetterModelPlatform platform() {
        MinestomBetterModel running = platform;
        if (running == null) {
            throw new IllegalStateException("BetterModelMinestom.register() has not been called.");
        }
        return running;
    }
}
