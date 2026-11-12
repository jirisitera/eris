package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import kr.toxicity.model.BetterModelEvaluatorImpl;
import kr.toxicity.model.BetterModelEventBusImpl;
import kr.toxicity.model.BetterModelPlatformImpl;
import kr.toxicity.model.api.*;
import kr.toxicity.model.api.data.ModelAsset;
import kr.toxicity.model.api.event.ModelAssetsEvent;
import kr.toxicity.model.api.event.PluginEndReloadEvent;
import kr.toxicity.model.api.event.PluginStartReloadEvent;
import kr.toxicity.model.api.manager.*;
import kr.toxicity.model.api.nms.NMS;
import kr.toxicity.model.api.pack.PackResult;
import kr.toxicity.model.api.pack.PackZipper;
import kr.toxicity.model.api.platform.PlatformAdapter;
import kr.toxicity.model.api.scheduler.ModelScheduler;
import kr.toxicity.model.api.util.EventUtil;
import kr.toxicity.model.api.version.MinecraftVersion;
import kr.toxicity.model.manager.*;
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

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

    private static void loadBundledModels(@NotNull ModelAssetsEvent event) {
        String folder = switch (event.type()) {
            case GENERAL -> "models";
            case PLAYER -> "players";
        };
        String resourcePrefix = RESOURCES + folder;

        Path devDir = Path.of("src/main/resources", resourcePrefix);
        if (Files.isDirectory(devDir)) {
            loadModelsFromDirectory(devDir, event);
        }

        try {
            Enumeration<URL> resources = MinestomBetterModel.class.getClassLoader().getResources(resourcePrefix);
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                String protocol = url.getProtocol();
                if ("file".equalsIgnoreCase(protocol)) {
                    try {
                        Path dir = Path.of(url.toURI());
                        if (Files.isDirectory(dir)) {
                            loadModelsFromDirectory(dir, event);
                        }
                    } catch (URISyntaxException ignored) {
                    }
                } else if ("jar".equalsIgnoreCase(protocol) && url.openConnection() instanceof JarURLConnection connection) {
                    loadModelsFromJar(connection.getJarFile(), resourcePrefix + "/", event);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadModelsFromDirectory(@NotNull Path directory, @NotNull ModelAssetsEvent event) {
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(Files::isRegularFile)
                .filter(path -> isModelFile(path.getFileName().toString()))
                .forEach(path -> {
                    ModelAsset asset = ModelAsset.of(path);
                    if (!event.assets().contains(asset)) {
                        event.addAsset(asset);
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private static void loadModelsFromJar(@NotNull JarFile jar, @NotNull String prefix, @NotNull ModelAssetsEvent event) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (entry.isDirectory()) {
                continue;
            }
            String entryName = entry.getName();
            if (!entryName.startsWith(prefix)) {
                continue;
            }
            String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
            if (!isModelFile(fileName)) {
                continue;
            }
            String modelName = fileName.substring(0, fileName.lastIndexOf('.'));
            try (InputStream in = jar.getInputStream(entry)) {
                ModelAsset asset = ModelAsset.of(modelName, in.readAllBytes());
                if (!event.assets().contains(asset)) {
                    event.addAsset(asset);
                }
            } catch (IOException | IllegalArgumentException ignored) {
            }
        }
    }

    private static boolean isModelFile(@NotNull String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".bbmodel") || lower.endsWith(".ajmodel");
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

        this.eventBus.subscribe(this, ModelAssetsEvent.class, MinestomBetterModel::loadBundledModels);
    }

    public void disable() {
        this.enabled = false;
        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();
        events.removeChild(this.playerManager.events());
        events.removeChild(this.entityManager.events());
        this.managers.values().forEach(GlobalManager::end);
        MinestomScheduler.INSTANCE.shutdown();
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
                MinestomPlayerModels.loadDefaults();
                long assetsTime = System.currentTimeMillis() - start;
                pipeline.setStatus("Generating files...");
                pipeline.setGoal(zipper.size());
                PackResult pack = PacksKt.toGenerator(this.config.packType()).create(zipper, pipeline);
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

    @Override
    public @NotNull File dataFolder() {
        return this.dataFolder;
    }

    @Override
    public BetterModelPlatform.@NotNull JarType jarType() {
        return BetterModelPlatform.JarType.FABRIC;
    }

    @Override
    public boolean isSnapshot() {
        return false;
    }

    @Override
    public @NotNull BetterModelConfig config() {
        return this.config;
    }

    @Override
    public @NotNull MinecraftVersion version() {
        return this.version;
    }

    @Override
    public @NonNull Semver semver() {
        return Objects.requireNonNull(this.semver);
    }

    @Override
    public @NotNull NMS nms() {
        return this.nms;
    }

    @Override
    public <T extends Manager> @NotNull T manager(@NotNull Class<T> managerClass) {
        return managerClass.cast(this.managers.get(managerClass));
    }

    @Override
    public @NotNull ModelScheduler scheduler() {
        return MinestomScheduler.INSTANCE;
    }

    @Override
    public @NotNull PlatformAdapter adapter() {
        return MinestomAdapter.INSTANCE;
    }

    @Override
    public @NotNull BetterModelLogger logger() {
        return this.logger;
    }

    @Override
    public @NotNull BetterModelEvaluator evaluator() {
        return this.evaluator;
    }

    @Override
    public @NotNull BetterModelEventBus eventBus() {
        return this.eventBus;
    }

    @Override
    public boolean isEnabled() {
        return this.enabled;
    }

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
