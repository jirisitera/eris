package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import kr.toxicity.model.BetterModelEvaluatorImpl;
import kr.toxicity.model.BetterModelEventBusImpl;
import kr.toxicity.model.BetterModelPlatformImpl;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.BetterModelConfig;
import kr.toxicity.model.api.BetterModelEvaluator;
import kr.toxicity.model.api.BetterModelEventBus;
import kr.toxicity.model.api.BetterModelLogger;
import kr.toxicity.model.api.BetterModelPlatform;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.event.ModelAssetsEvent;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.event.PluginStartReloadEvent;
import kr.toxicity.model.api.manager.Manager;
import kr.toxicity.model.api.manager.ModelManager;
import kr.toxicity.model.api.manager.PlayerManager;
import kr.toxicity.model.api.manager.ProfileManager;
import kr.toxicity.model.api.manager.ReloadInfo;
import kr.toxicity.model.api.manager.ScriptManager;
import kr.toxicity.model.api.manager.SkinManager;
import kr.toxicity.model.api.nms.NMS;
import kr.toxicity.model.api.pack.PackResult;
import kr.toxicity.model.api.pack.PackZipper;
import kr.toxicity.model.api.platform.PlatformAdapter;
import kr.toxicity.model.api.scheduler.ModelScheduler;
import kr.toxicity.model.api.util.EventUtil;
import kr.toxicity.model.api.version.MinecraftVersion;
import kr.toxicity.model.manager.GlobalManager;
import kr.toxicity.model.manager.ModelManagerImpl;
import kr.toxicity.model.manager.ProfileManagerImpl;
import kr.toxicity.model.manager.ReloadPipeline;
import kr.toxicity.model.manager.ScriptManagerImpl;
import kr.toxicity.model.manager.SkinManagerImpl;
import kr.toxicity.model.util.IndicatorsKt;
import kr.toxicity.model.util.PacksKt;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minestom.server.MinecraftServer;
import net.minestom.server.event.GlobalEventHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NonNull;
import org.semver4j.Semver;

public final class MinestomBetterModel implements BetterModelPlatformImpl {
    private static final String RESOURCES = "bettermodel/";
    private final File dataFolder;
    private final Semver semver = Semver.coerce(bundledVersion());
    private final MinecraftVersion version = MinecraftVersion.parse("26.2");
    private final MinestomNMS nms = new MinestomNMS();
    private final BetterModelLogger logger = new Logger();
    private final BetterModelEvaluator evaluator = new BetterModelEvaluatorImpl();
    private final BetterModelEventBus eventBus = new BetterModelEventBusImpl();
    private final PlayerManagerImpl playerManager = new PlayerManagerImpl();
    private final EntityManager entityManager = new EntityManager();
    private final Map<Class<?>, GlobalManager> managers = new LinkedHashMap<>();
    private final AtomicBoolean reloading = new AtomicBoolean();
    private final AtomicBoolean firstLoad = new AtomicBoolean();
    private volatile MinestomConfig config;
    private volatile boolean enabled = true;
    private Consumer<PackZipper> reloadStart = zipper -> EventUtil.call(PluginStartReloadEvent.class, () -> new PluginStartReloadEvent(zipper));
    private Consumer<BetterModelPlatform.ReloadResult> reloadEnd = result -> EventUtil.call(PluginEndReloadEvent.class, () -> new PluginEndReloadEvent(result));

    public MinestomBetterModel(@NotNull File dataFolder) {
        this.dataFolder = dataFolder.getAbsoluteFile();
    }

    public void enable() {
        BetterModel.register(this);
        this.config = new MinestomConfig();
        this.managers.put(ProfileManager.class, ProfileManagerImpl.INSTANCE);
        this.managers.put(SkinManager.class, SkinManagerImpl.INSTANCE);
        this.managers.put(ModelManager.class, ModelManagerImpl.INSTANCE);
        this.managers.put(PlayerManager.class, this.playerManager);
        this.managers.put(EntityManager.class, this.entityManager);
        this.managers.put(ScriptManager.class, ScriptManagerImpl.INSTANCE);

        for (GlobalManager manager : this.managers.values()) {
            if (manager == ProfileManagerImpl.INSTANCE) {
                ProfileManagerImpl.INSTANCE.supplier(new MinestomProfileSupplier());
                continue;
            }
            manager.start();
        }

        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();
        events.addChild(this.playerManager.events());
        events.addChild(this.entityManager.events());

        this.eventBus.subscribe(this, ModelAssetsEvent.class, event -> {
            if (event.type() == ModelRenderer.Type.PLAYER) {
                byte[] bytes = loadSteveModelBytes();
                if (bytes != null) {
                    event.addAsset(ModelAsset.of("steve", bytes));
                }
            }
        });
    }

    private static byte @Nullable [] loadSteveModelBytes() {
        File devFile = new File("src/main/resources/bettermodel/players/steve.bbmodel");
        if (devFile.exists() && devFile.isFile()) {
            try {
                return Files.readAllBytes(devFile.toPath());
            } catch (IOException ignored) {}
        }
        try (InputStream in = MinestomBetterModel.class.getClassLoader().getResourceAsStream("bettermodel/players/steve.bbmodel")) {
            if (in != null) {
                return in.readAllBytes();
            }
        } catch (IOException ignored) {}
        return null;
    }

    public void disable() {
        this.enabled = false;
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();
        events.removeChild(this.playerManager.events());
        events.removeChild(this.entityManager.events());
        this.managers.values().forEach(GlobalManager::end);
        MinestomScheduler.INSTANCE.shutdown();
    }

    private static @NotNull String bundledVersion() {
        try (InputStream in = MinestomBetterModel.class.getClassLoader().getResourceAsStream("bettermodel/version.properties")) {
            if (in != null) {
                Properties properties = new Properties();
                properties.load(in);
                String version = properties.getProperty("version");
                if (version != null && !version.isBlank()) {
                    return version;
                }
            }
        } catch (IOException ignored) {
        }
        String implVersion = BetterModel.class.getPackage().getImplementationVersion();
        return implVersion != null ? implVersion : "0.0.0";
    }

    @Override
    public BetterModelPlatform.@NotNull ReloadResult reload(@NotNull ReloadInfo info) {
        if (!this.reloading.compareAndSet(false, true)) {
            return BetterModelPlatform.ReloadResult.OnReload.INSTANCE;
        }
        BetterModelPlatform.ReloadResult result;
        try {
            if (!info.skipConfig()) {
                this.config = new MinestomConfig();
            }
            PackZipper zipper = PackZipper.zipper();
            this.reloadStart.accept(zipper);
            try (ReloadPipeline pipeline = new ReloadPipeline(IndicatorsKt.toIndicator(this.config.indicator().options(), info))) {
                long start = System.currentTimeMillis();
                for (GlobalManager manager : this.managers.values()) {
                    manager.reload(pipeline, zipper);
                }
                long assetsTime = System.currentTimeMillis() - start;
                pipeline.setStatus("Generating files...");
                pipeline.setGoal(zipper.size());
                PackResult pack = PacksKt.toGenerator(this.config.packType()).create(zipper, pipeline);
                this.syncToGlobalAssets(pack);
                cleanEmptyDirectories(this.dataFolder);
                result = new BetterModelPlatform.ReloadResult.Success(this.firstLoad.compareAndSet(false, true), assetsTime, pack);
            }
        } catch (Throwable e) {
            result = new BetterModelPlatform.ReloadResult.Failure(e);
        } finally {
            this.reloading.set(false);
        }
        this.reloadEnd.accept(result);
        return result;
    }

    @Override
    public synchronized void addReloadStartHandler(@NotNull Consumer<PackZipper> consumer) {
        this.reloadStart = this.reloadStart.andThen(consumer);
    }

    @Override
    public synchronized void addReloadEndHandler(@NotNull Consumer<BetterModelPlatform.ReloadResult> consumer) {
        this.reloadEnd = this.reloadEnd.andThen(consumer);
    }

    @Override public @NotNull File dataFolder() { return this.dataFolder; }
    @Override public BetterModelPlatform.@NotNull JarType jarType() { return BetterModelPlatform.JarType.FABRIC; }
    @Override public boolean isSnapshot() { return false; }
    @Override public @NotNull BetterModelConfig config() { return this.config; }
    @Override public @NotNull MinecraftVersion version() { return this.version; }
    @Override public @NonNull Semver semver() { return Objects.requireNonNull(this.semver); }
    @Override public @NotNull NMS nms() { return this.nms; }
    @Override public <T extends Manager> @NotNull T manager(@NotNull Class<T> managerClass) { return managerClass.cast(this.managers.get(managerClass)); }
    @Override public @NotNull ModelScheduler scheduler() { return MinestomScheduler.INSTANCE; }
    @Override public @NotNull PlatformAdapter adapter() { return MinestomAdapter.INSTANCE; }
    @Override public @NotNull BetterModelLogger logger() { return this.logger; }
    @Override public @NotNull BetterModelEvaluator evaluator() { return this.evaluator; }
    @Override public @NotNull BetterModelEventBus eventBus() { return this.eventBus; }
    @Override public boolean isEnabled() { return this.enabled; }

    @Override
    public @Nullable InputStream getResource(@NotNull String path) {
        if (path.endsWith(".bbmodel")) {
            return null;
        }
        ClassLoader cl = MinestomBetterModel.class.getClassLoader();
        InputStream stream = cl.getResourceAsStream(path);
        if (stream != null) return stream;
        return path.startsWith(RESOURCES)
            ? cl.getResourceAsStream(path.substring(RESOURCES.length()))
            : cl.getResourceAsStream(RESOURCES + path);
    }

    @Override
    public void saveResource(@NotNull String resourcePath) {
    }

    @Override
    public void loadAssets(@NotNull ReloadPipeline pipeline, @NotNull String prefix, @NotNull BiConsumer<String, InputStream> consumer) {
    }

    private void syncToGlobalAssets(PackResult pack) {
        File rootDir = this.dataFolder.getParentFile();
        pack.stream().forEach(packByte -> {
            String path = packByte.path().path();
            if (!path.startsWith("assets/")) {
                return;
            }
            File target = new File(rootDir, path);
            if (target.exists() && target.length() == packByte.length()) {
                return;
            }
            try {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        return;
                    }
                }
                Files.write(target.toPath(), packByte.bytes());
            } catch (IOException e) {
                this.logger.warn(Component.text("Failed to write asset " + path + ": " + e.getMessage()));
            }
        });
    }

    private static void cleanEmptyDirectories(File dir) {
        if (!dir.exists() || !dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    cleanEmptyDirectories(child);
                }
            }
        }
        String[] remaining = dir.list();
        if (remaining != null && remaining.length == 0) {
            //noinspection ResultOfMethodCallIgnored
            dir.delete();
        }
    }

    private static final class Logger implements BetterModelLogger {
        private final ComponentLogger logger = ComponentLogger.logger("BetterModel");

        @Override
        public synchronized void info(Component @NotNull ... messages) {
            for (Component message : messages) {
                this.logger.info(message);
            }
        }

        @Override
        public synchronized void warn(Component @NotNull ... messages) {
            for (Component message : messages) {
                this.logger.warn(message);
            }
        }
    }
}
