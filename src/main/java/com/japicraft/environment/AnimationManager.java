package com.japicraft.environment;

import com.japicraft.bettermodel.MinestomAdapter;
import com.japicraft.bettermodel.impl.MinestomScheduler;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.*;
import net.minestom.server.entity.Entity;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class AnimationManager {
    private static final int TIMER_TAG_G = 253;
    private static final String DEFAULT_TIMER_MODEL = "case";
    private static final Tag<Task> TIMER_TASK_TAG = Tag.Transient("caseTimerTask");
    private static final Tag<Integer> TIMER_SECONDS_TAG = Tag.Transient("caseTimerSeconds");
    private static final Tag<Integer> TIMER_STRIKES_TAG = Tag.Transient("caseTimerStrikes");

    public static void stop(@NotNull Entity entity) {
        stopTimer(entity);
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUuid());
        if (registry != null) {
            registry.close();
        }
    }

    public static int encodeTimer(int minutes, int seconds, int strikes) {
        int clampedMinutes = Math.clamp(minutes, 0, 63);
        int clampedSeconds = Math.clamp(seconds, 0, 59);
        int clampedStrikes = Math.clamp(strikes, 0, 3);
        int r = (clampedStrikes << 6) | clampedMinutes;
        return (r << 16) | (TIMER_TAG_G << 8) | clampedSeconds;
    }

    public static int encodeTimer(int minutes, int seconds) {
        return encodeTimer(minutes, seconds, 0);
    }

    private static void syncTimerDisplay(@NotNull Entity entity) {
        EntityTracker tracker = resolveTracker(entity);
        if (tracker == null) {
            return;
        }
        int totalSeconds = Optional.ofNullable(entity.getTag(TIMER_SECONDS_TAG)).orElse(0);
        int strikes = Optional.ofNullable(entity.getTag(TIMER_STRIKES_TAG)).orElse(0);
        int rgb = encodeTimer(totalSeconds / 60, totalSeconds % 60, strikes);
        TrackerUpdateAction tintAction = TrackerUpdateAction.tint(rgb);
        tracker.update(tintAction);
    }

    public static void setTimer(@NotNull Entity entity, int minutes, int seconds) {
        int clampedMinutes = Math.clamp(minutes, 0, 63);
        int clampedSeconds = Math.clamp(seconds, 0, 59);
        entity.setTag(TIMER_SECONDS_TAG, clampedMinutes * 60 + clampedSeconds);
        syncTimerDisplay(entity);
    }

    public static void setTimer(@NotNull Entity entity, int totalSeconds) {
        int clamped = Math.clamp(totalSeconds, 0, 63 * 60 + 59);
        setTimer(entity, clamped / 60, clamped % 60);
    }

    public static void setStrikes(@NotNull Entity entity, int strikes) {
        entity.setTag(TIMER_STRIKES_TAG, Math.clamp(strikes, 0, 3));
        syncTimerDisplay(entity);
    }

    public static int getStrikes(@NotNull Entity entity) {
        return Optional.ofNullable(entity.getTag(TIMER_STRIKES_TAG)).orElse(0);
    }

    public static int addStrike(@NotNull Entity entity) {
        int next = Math.clamp(getStrikes(entity) + 1, 0, 3);
        setStrikes(entity, next);
        return next;
    }

    public static void startTimer(@NotNull Entity entity, int minutes, int seconds, @Nullable Runnable onComplete) {
        stopTimer(entity);
        int initialSeconds = Math.clamp(minutes, 0, 63) * 60 + Math.clamp(seconds, 0, 59);
        AtomicInteger remaining = new AtomicInteger(initialSeconds);
        setTimer(entity, remaining.get());

        if (initialSeconds <= 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        Task task = entity.scheduler().buildTask(() -> {
            int next = remaining.decrementAndGet();
            setTimer(entity, Math.max(0, next));
            if (next <= 0) {
                stopTimer(entity);
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        }).delay(TaskSchedule.tick(20)).repeat(TaskSchedule.tick(20)).schedule();
        entity.setTag(TIMER_TASK_TAG, task);
    }
    public static void stopTimer(@NotNull Entity entity) {
        Task existing = entity.getTag(TIMER_TASK_TAG);
        if (existing != null) {
            existing.cancel();
            entity.removeTag(TIMER_TASK_TAG);
        }
    }
    private static @Nullable EntityTracker resolveTracker(@NotNull Entity entity) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUuid());
        if (registry != null) {
            EntityTracker tracker = registry.tracker(AnimationManager.DEFAULT_TIMER_MODEL);
            if (tracker != null) {
                return tracker;
            }
            EntityTracker first = registry.first();
            if (first != null) {
                return first;
            }
        }
        Optional<ModelRenderer> optModel = BetterModel.model(AnimationManager.DEFAULT_TIMER_MODEL);
        return optModel.map(modelRenderer -> modelRenderer.getOrCreate(
            MinestomAdapter.adapt(entity),
            TrackerModifier.DEFAULT,
            created -> created.hideOption(EntityHideOption.DEFAULT)
        )).orElse(null);
    }

    public static void spawn(@NotNull Entity entity, @NotNull String targetModel) {
        if (targetModel.isBlank()) {
            return;
        }

        Optional<ModelRenderer> optModel = BetterModel.model(targetModel);
        if (optModel.isEmpty()) {
            return;
        }

        ModelRenderer selectedModel = optModel.get();

        EntityTracker tracker = selectedModel.getOrCreate(
            MinestomAdapter.adapt(entity),
            TrackerModifier.DEFAULT,
            created -> created.hideOption(EntityHideOption.DEFAULT)
        );

        selectedModel.animation("idle").ifPresent(idleAnim -> {
            boolean idleAlreadyQueued = tracker.bones().stream()
                .map(RenderedBone::runningAnimation)
                .anyMatch(a -> a != null && "idle".equalsIgnoreCase(a.name()));
            if (!idleAlreadyQueued) {
                tracker.animate(idleAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            }
        });

    }

    public static void play(@NotNull Entity entity, @NotNull String targetModel, @NotNull String animName) {
        if (animName.isBlank()) {
            return;
        }

        ModelRenderer selectedModel = null;
        BlueprintAnimation selectedAnim = null;

        Optional<ModelRenderer> optModel = BetterModel.model(targetModel);
        if (optModel.isPresent()) {
            selectedModel = optModel.get();
            selectedAnim = selectedModel.animation(animName).orElse(null);
        }

        if (selectedModel == null || selectedAnim == null) {
            return;
        }

        // Re-use or obtain tracker WITHOUT closing the registry
        EntityTracker tracker = selectedModel.getOrCreate(
            MinestomAdapter.adapt(entity),
            TrackerModifier.DEFAULT,
            created -> {
                created.hideOption(EntityHideOption.DEFAULT);
            }
        );

        Optional<BlueprintAnimation> idleOpt = selectedModel.animation("idle");
        boolean isIdle = "idle".equalsIgnoreCase(selectedAnim.name());

        if (isIdle) {
            // Stop any other active animations on the model to cleanly return to idle
            for (String otherAnim : selectedModel.animations().keySet()) {
                if (!otherAnim.equalsIgnoreCase("idle")) {
                    tracker.stopAnimation(otherAnim);
                }
            }
            tracker.animate(selectedAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            return;
        }

        // If an idle animation exists on the model, ensure idle is queued as the persistent base loop
        if (idleOpt.isPresent()) {
            BlueprintAnimation idleAnim = idleOpt.get();
            boolean idleAlreadyQueued = tracker.bones().stream()
                .map(RenderedBone::runningAnimation)
                .anyMatch(a -> a != null && "idle".equalsIgnoreCase(a.name()));
            if (!idleAlreadyQueued) {
                tracker.animate(idleAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            }
        }

        // Stop any conflicting non-idle animations
        for (String otherAnim : selectedModel.animations().keySet()) {
            if (!otherAnim.equalsIgnoreCase("idle") && !otherAnim.equalsIgnoreCase(selectedAnim.name())) {
                tracker.stopAnimation(otherAnim);
            }
        }

        // Prepare the animation completion callback
        Runnable onEnd;
        if (selectedAnim.loop() == AnimationIterator.Type.PLAY_ONCE) {
            String finishedAnimName = selectedAnim.name();
            AtomicBoolean ended = new AtomicBoolean(false);
            onEnd = () -> {
                if (!ended.compareAndSet(false, true)) {
                    return;
                }
                // Run on next tick outside the bone state iteration to avoid ConcurrentModificationException
                MinestomScheduler.INSTANCE.task(() -> {
                    if (tracker.isClosed()) {
                        return;
                    }
                    if (idleOpt.isPresent()) {
                        // Ensure idle is resumed if no other animation is active
                        boolean anyRunning = tracker.bones().stream().anyMatch(b -> b.runningAnimation() != null);
                        if (!anyRunning) {
                            tracker.animate(idleOpt.get(), new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
                        }
                    } else {
                        tracker.stopAnimation(finishedAnimName);
                    }
                });
            };
        } else {
            onEnd = () -> {
            };
        }

        AnimationModifier modifier = new AnimationModifier(0, 0, selectedAnim.loop());
        tracker.animate(selectedAnim, modifier, onEnd);
    }
}
