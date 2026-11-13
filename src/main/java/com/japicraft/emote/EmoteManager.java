package com.japicraft.emote;

import com.japicraft.camera.CameraAnchor;
import com.japicraft.environment.InstanceManager;
import com.japicraft.game.GameStageManager;
import com.japicraft.integration.bettermodel.MinestomAdapter;
import com.japicraft.integration.bettermodel.implementation.MinestomPlayerModels;
import com.japicraft.service.AnimationService;
import kr.toxicity.model.api.BetterModel;
import kr.toxicity.model.api.data.blueprint.BlueprintAnimation;
import kr.toxicity.model.api.data.renderer.ModelRenderer;
import kr.toxicity.model.api.tracker.EntityHideOption;
import kr.toxicity.model.api.tracker.EntityTracker;
import kr.toxicity.model.api.tracker.TrackerModifier;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

public class EmoteManager {
    public static final float DEFAULT_Y_OFFSET = CameraAnchor.CAMERA_HEIGHT + 1.1F;
    public static final Tag<Float> BODY_YAW_TAG = Tag.Transient("emoteBodyYaw");

    public static float getYOffset(@NotNull Player player) {
        return CameraAnchor.isAttached(player) ? DEFAULT_Y_OFFSET : 0.0f;
    }

    /**
     * model-local translation: lobby avatars are visual-only, independent of the real players
     */
    public static Vector3f modelOffset(Player player) {
        Vector3f offset = new Vector3f(0, -getYOffset(player), 0);
        if (CameraAnchor.isAttached(player) && !GameStageManager.isGame(player)) {
            var seat = InstanceManager.lobbyAvatarPosition(player);
            var body = CameraAnchor.bodyPosition(player);
            offset.add((float) (seat.x() - body.x()),
                (float) (seat.y() + CameraAnchor.CAMERA_HEIGHT - body.y()), (float) (seat.z() - body.z()));
            // world-to-local offset conversion for display translation rotated by -yaw
            offset.rotateY((float) Math.toRadians(seat.yaw()));
        }
        return offset;
    }

    public static void stop(Player player) {
        player.removeTag(BODY_YAW_TAG);
        AnimationService.stop(player);
    }

    /**
     * rebuild cached bone transforms after the stage changes the avatar's offset
     */
    public static void resetPose(Player player, String targetLimb, String animName) {
        ModelRenderer limb = BetterModel.limb(targetLimb).orElse(null);
        if (limb == null || limb.animation(animName).isEmpty()) return;
        var registry = BetterModel.registryOrNull(player.getUuid());
        if (registry != null) {
            EntityTracker previous = registry.tracker(limb.name());
            if (previous != null) previous.close();
        }
        // Keep the stage's body yaw, assigned by CameraAnchor.attach.
        play(player, targetLimb, animName);
    }

    public static void play(Player player, String targetLimb, String animName) {
        if (animName == null || animName.isBlank()) {
            return;
        }

        ModelRenderer selectedLimb = BetterModel.limb(targetLimb).orElse(null);
        BlueprintAnimation selectedAnim = selectedLimb == null ? null : selectedLimb.animation(animName).orElse(null);

        if (selectedLimb == null || selectedAnim == null) {
            return;
        }

        if (!player.hasTag(BODY_YAW_TAG)) {
            float fixedYaw = CameraAnchor.isAttached(player) ? CameraAnchor.baseYaw(player) : player.getPosition().yaw();
            player.setTag(BODY_YAW_TAG, fixedYaw);
        }

        EntityTracker tracker = MinestomPlayerModels.getOrCreate(selectedLimb,
            MinestomAdapter.adapt(player),
            TrackerModifier.DEFAULT,
            created -> {
                created.hideOption(EntityHideOption.DEFAULT);
                applyValues(player, created);
            }
        );

        applyValues(player, tracker);

        if ("standing".equalsIgnoreCase(animName) || "idle".equalsIgnoreCase(animName)) {
            AnimationService.animateBasePose(tracker, selectedLimb, selectedAnim);
            return;
        }

        AnimationService.animate(tracker, selectedLimb, selectedAnim, () -> {
            player.removeTag(BODY_YAW_TAG);
            tracker.close();
        });
    }

    private static void applyValues(Player player, EntityTracker tracker) {
        tracker.getPipeline().defaultPosition(pos -> pos.set(modelOffset(player)));
        tracker.bodyRotator().setValue(data -> {
            data.setPlayerMode(true);
            data.setHeadUneven(true);
            data.setMinHead(-180.0f);
            data.setMaxHead(180.0f);
        });
    }
}
