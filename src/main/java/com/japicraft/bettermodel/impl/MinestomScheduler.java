package com.japicraft.bettermodel.impl;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import kr.toxicity.model.api.scheduler.ModelScheduler;
import kr.toxicity.model.api.scheduler.ModelTask;
import kr.toxicity.model.api.util.LogUtil;
import net.minestom.server.MinecraftServer;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MinestomScheduler implements ModelScheduler {
    public static final MinestomScheduler INSTANCE = new MinestomScheduler();
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final ScheduledExecutorService pool;

    private MinestomScheduler() {
        AtomicInteger threads = new AtomicInteger();
        this.pool = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(), runnable -> {
            Thread thread = new Thread(runnable, "BetterModel-Async-Scheduler-" + threads.getAndIncrement());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler((t, e) -> LogUtil.handleException("Exception has occurred in " + t.getName(), e));
            return thread;
        });
    }

    @Override
    public @NotNull ModelTask asyncTask(@NotNull Runnable runnable) {
        return new AsyncTask(runnable, this.pool::submit);
    }

    @Override
    public @NotNull ModelTask asyncTaskLater(long delay, @NotNull Runnable runnable) {
        return new AsyncTask(runnable, task -> this.pool.schedule(task, delay * 50L, TimeUnit.MILLISECONDS));
    }

    @Override
    public @NotNull ModelTask asyncTaskTimer(long delay, long period, @NotNull Runnable runnable) {
        return new AsyncTask(runnable, task -> this.pool.scheduleAtFixedRate(task, delay * 50L, period * 50L, TimeUnit.MILLISECONDS));
    }

    public @Nullable ModelTask task(@NotNull Runnable runnable) {
        if (!this.enabled.get()) {
            return null;
        }
        return new SyncTask(MinecraftServer.getSchedulerManager().scheduleNextTick(this.guard(runnable)));
    }

    public @Nullable ModelTask taskLater(long delay, @NotNull Runnable runnable) {
        if (!this.enabled.get()) {
            return null;
        }
        if (delay <= 0L) {
            return this.task(runnable);
        }
        return new SyncTask(MinecraftServer.getSchedulerManager().buildTask(this.guard(runnable)).delay(TaskSchedule.tick((int) delay)).schedule());
    }

    private @NotNull Runnable guard(@NotNull Runnable runnable) {
        return () -> {
            if (this.enabled.get()) {
                runnable.run();
            }
        };
    }

    void shutdown() {
        this.enabled.set(false);
        this.pool.shutdownNow();
    }

    private final class AsyncTask implements Runnable, ModelTask {
        private final Runnable runnable;
        private final java.util.concurrent.Future<?> future;

        private AsyncTask(Runnable runnable, java.util.function.Function<Runnable, java.util.concurrent.Future<?>> schedule) {
            this.runnable = runnable;
            this.future = schedule.apply(this);
        }

        @Override
        public void run() {
            if (MinestomScheduler.this.enabled.get()) {
                this.runnable.run();
            } else {
                this.cancel();
            }
        }

        @Override
        public boolean isCancelled() {
            return this.future.isCancelled();
        }

        @Override
        public void cancel() {
            this.future.cancel(true);
        }
    }

    private record SyncTask(Task task) implements ModelTask {

        @Override
            public boolean isCancelled() {
                return !this.task.isAlive();
            }

            @Override
            public void cancel() {
                this.task.cancel();
            }
        }
}
