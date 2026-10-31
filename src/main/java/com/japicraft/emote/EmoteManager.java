package com.japicraft.emote;

import com.japicraft.bettermodel.MinestomAdapter;
import com.japicraft.bettermodel.impl.MinestomScheduler;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.environment.AnimationManager;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.animation.AnimationIterator;
import kr.toxicity.model.api.animation.AnimationModifier;
import kr.toxicity.model.api.bone.RenderedBone;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmoteManager {
    public static final float DEFAULT_Y_OFFSET = CameraAnchor.CAMERA_HEIGHT + 1.1F;
    public static final Tag<Float> Y_OFFSET_TAG = Tag.Transient("emoteYOffset");
    public static final Tag<Float> BODY_YAW_TAG = Tag.Transient("emoteBodyYaw");

    public static float getYOffset(@NotNull Player player) {
        Float custom = player.getTag(Y_OFFSET_TAG);
        if (custom != null) {
            return custom;
        }
        return CameraAnchor.isAttached(player) ? DEFAULT_Y_OFFSET : 0.0f;
    }

    public static void stop(Player player) {
        player.removeTag(BODY_YAW_TAG);
        AnimationManager.stop(player);
    }

    public static void play(Player player, String targetLimb, String animName) {
        if (animName == null || animName.isBlank()) {
            return;
        }

        ModelRenderer selectedLimb = null;
        BlueprintAnimation selectedAnim = null;

        Optional<ModelRenderer> optLimb = BetterModel.limb(targetLimb);
        if (optLimb.isPresent()) {
            selectedLimb = optLimb.get();
            selectedAnim = selectedLimb.animation(animName).orElse(null);
        }

        if (selectedLimb == null || selectedAnim == null) {
            return;
        }

        if (!player.hasTag(BODY_YAW_TAG)) {
            float fixedYaw = CameraAnchor.isAttached(player) ? CameraAnchor.baseYaw(player) : player.getPosition().yaw();
            player.setTag(BODY_YAW_TAG, fixedYaw);
        }

        // Apply downward offset so the emote plays at the player's feet
        float yOffset = getYOffset(player);

        // Re-use or obtain tracker WITHOUT closing the registry
        EntityTracker tracker = selectedLimb.getOrCreate(
            MinestomAdapter.adapt(player),
            TrackerModifier.DEFAULT,
            created -> {
                created.hideOption(EntityHideOption.DEFAULT);
                applyValues(yOffset, created);
            }
        );
        // Ensure defaultPosition and bodyRotator settings are applied to existing or newly created tracker
        applyValues(yOffset, tracker);

        Optional<BlueprintAnimation> idleOpt = selectedLimb.animation("idle");
        boolean isIdle = "idle".equalsIgnoreCase(selectedAnim.name());

        if (isIdle) {
            // Stop any other active animations on the limb to cleanly return to idle
            for (String otherAnim : selectedLimb.animations().keySet()) {
                if (!otherAnim.equalsIgnoreCase("idle")) {
                    tracker.stopAnimation(otherAnim);
                }
            }
            tracker.animate(selectedAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            return;
        }

        // If an idle animation exists on the limb, ensure idle is queued as the persistent base loop
        if (idleOpt.isPresent()) {
            BlueprintAnimation idleAnim = idleOpt.get();
            boolean idleAlreadyQueued = tracker.bones().stream()
                .map(RenderedBone::runningAnimation)
                .anyMatch(a -> a != null && "idle".equalsIgnoreCase(a.name()));
            if (!idleAlreadyQueued) {
                tracker.animate(idleAnim, new AnimationModifier(0, 0, AnimationIterator.Type.LOOP));
            }
        }

        // Stop any conflicting non-idle emote animations
        for (String otherAnim : selectedLimb.animations().keySet()) {
            if (!otherAnim.equalsIgnoreCase("idle") && !otherAnim.equalsIgnoreCase(selectedAnim.name())) {
                tracker.stopAnimation(otherAnim);
            }
        }

        // Prepare the animation completion callback
        Runnable onEnd;
        if (selectedAnim.loop() == AnimationIterator.Type.PLAY_ONCE) {
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
                        player.removeTag(BODY_YAW_TAG);
                        tracker.close();
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

    private static void applyValues(float yOffset, EntityTracker tracker) {
        tracker.getPipeline().defaultPosition(pos -> pos.set(0, -yOffset, 0));
        tracker.bodyRotator().setValue(data -> {
            data.setPlayerMode(true);
            data.setHeadUneven(true);
            data.setMinHead(-180.0f);
            data.setMaxHead(180.0f);
        });
    }
}
