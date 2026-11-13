package com.japicraft.service;

import com.japicraft.integration.bettermodel.MinestomAdapter;
import com.japicraft.integration.bettermodel.implementation.MinestomPlayerModels;
import com.japicraft.integration.bettermodel.implementation.MinestomScheduler;
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

public class AnimationService {
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

    /**
     * pose reset with tracker and model displays retained
     */
    public static void stopAnimation(Entity entity, String animation) {
        EntityTrackerRegistry registry = BetterModel.registryOrNull(entity.getUuid());
        if (registry != null) registry.trackers().forEach(tracker -> tracker.stopAnimation(animation));
    }

    public static int encodeTimer(int minutes, int seconds, int strikes) {
        int clampedMinutes = Math.clamp(minutes, 0, 63);
        int clampedSeconds = Math.clamp(seconds, 0, 59);
        int clampedStrikes = Math.clamp(strikes, 0, 3);
        int r = (clampedStrikes << 6) | clampedMinutes;
        return (r << 16) | (TIMER_TAG_G << 8) | clampedSeconds;
    }

    private static void syncTimerDisplay(@NotNull Entity entity) {
        EntityTracker tracker = resolveTracker(entity);
        if (tracker == null) {
            return;
        }
        int totalSeconds = getTimerSeconds(entity);
        int strikes = getStrikes(entity);
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

    public static int getTimerSeconds(@NotNull Entity entity) {
        return Optional.ofNullable(entity.getTag(TIMER_SECONDS_TAG)).orElse(0);
    }

    public static void startTimer(@NotNull Entity entity, int minutes, int seconds, @Nullable Runnable onComplete) {
        stopTimer(entity);
        setTimer(entity, minutes, seconds);

        if (getTimerSeconds(entity) == 0) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }

        Task task = entity.scheduler().buildTask(() -> {
            int next = getTimerSeconds(entity) - 1;
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

    public static boolean isTimerRunning(@NotNull Entity entity) {
        Task task = entity.getTag(TIMER_TASK_TAG);
        return task != null && task.isAlive();
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
            EntityTracker tracker = registry.tracker(AnimationService.DEFAULT_TIMER_MODEL);
            if (tracker != null) {
                return tracker;
            }
            EntityTracker first = registry.first();
            if (first != null) {
                return first;
            }
        }
        Optional<ModelRenderer> optModel = BetterModel.model(AnimationService.DEFAULT_TIMER_MODEL);
        return optModel.map(modelRenderer -> MinestomPlayerModels.getOrCreate(modelRenderer,
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

        EntityTracker tracker = MinestomPlayerModels.getOrCreate(selectedModel,
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

        ModelRenderer selectedModel = BetterModel.model(targetModel).orElse(null);
        BlueprintAnimation selectedAnim = selectedModel == null ? null : selectedModel.animation(animName).orElse(null);

        if (selectedModel == null || selectedAnim == null) {
            return;
        }

        EntityTracker tracker = MinestomPlayerModels.getOrCreate(selectedModel,
            MinestomAdapter.adapt(entity),
            TrackerModifier.DEFAULT,
            created -> created.hideOption(EntityHideOption.DEFAULT)
        );

        animate(tracker, selectedModel, selectedAnim, () -> tracker.stopAnimation(selectedAnim.name()));
    }

    /**
     * replace the whole base pose, including bones absent from the new animation
     */
    public static void animateBasePose(EntityTracker tracker, ModelRenderer model, BlueprintAnimation pose) {
        for (String animation : model.animations().keySet()) {
            if (!animation.equalsIgnoreCase(pose.name())) tracker.stopAnimation(animation);
        }
        tracker.animate(pose, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
    }

    /**
     * shared idle/conflict/completion handling for scene models and player emotes
     */
    public static void animate(EntityTracker tracker, ModelRenderer selectedModel,
                               BlueprintAnimation selectedAnim, Runnable finishedWithoutIdle) {
        Optional<BlueprintAnimation> idleOpt = selectedModel.animation("idle");
        boolean isIdle = "idle".equalsIgnoreCase(selectedAnim.name());

        if (isIdle) {
            animateBasePose(tracker, selectedModel, selectedAnim);
            return;
        }

        // persistent base loop for models with an idle animation
        if (idleOpt.isPresent()) {
            BlueprintAnimation idleAnim = idleOpt.get();
            boolean idleAlreadyQueued = tracker.bones().stream()
                .map(RenderedBone::runningAnimation)
                .anyMatch(a -> a != null && "idle".equalsIgnoreCase(a.name()));
            if (!idleAlreadyQueued) {
                tracker.animate(idleAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            }
        }

        for (String otherAnim : selectedModel.animations().keySet()) {
            if (!otherAnim.equalsIgnoreCase("idle") && !otherAnim.equalsIgnoreCase(selectedAnim.name())) {
                tracker.stopAnimation(otherAnim);
            }
        }

        Runnable onEnd;
        if (selectedAnim.loop() == AnimationIterator.Type.PLAY_ONCE) {
            AtomicBoolean ended = new AtomicBoolean(false);
            onEnd = () -> {
                if (!ended.compareAndSet(false, true)) {
                    return;
                }
                // next-tick callback outside bone iteration to avoid ConcurrentModificationException
                MinestomScheduler.INSTANCE.task(() -> {
                    if (tracker.isClosed()) {
                        return;
                    }
                    if (idleOpt.isPresent()) {

                        boolean anyRunning = tracker.bones().stream().anyMatch(b -> b.runningAnimation() != null);
                        if (!anyRunning) {
                            tracker.animate(idleOpt.get(), new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
                        }
                    } else {
                        finishedWithoutIdle.run();
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
