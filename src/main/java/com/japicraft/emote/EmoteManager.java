package com.japicraft.emote;

import com.japicraft.bettermodel.MinestomAdapter;
import com.japicraft.bettermodel.impl.MinestomPlayerModels;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.environment.AnimationManager;
import com.japicraft.environment.GameStageManager;
import com.japicraft.environment.InstanceManager;
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
     * Model-local translation: lobby avatars are visual-only, independent of the real players.
     */
    public static Vector3f modelOffset(Player player) {
        Vector3f offset = new Vector3f(0, -getYOffset(player), 0);
        if (CameraAnchor.isAttached(player) && !GameStageManager.isGame(player)) {
            var seat = InstanceManager.lobbyAvatarPosition(player);
            var body = CameraAnchor.bodyPosition(player);
            offset.add((float) (seat.x() - body.x()),
                (float) (seat.y() + CameraAnchor.CAMERA_HEIGHT - body.y()), (float) (seat.z() - body.z()));
            // Display entities rotate their translation by -yaw; convert the world offset to local space.
            offset.rotateY((float) Math.toRadians(seat.yaw()));
        }
        return offset;
    }

    public static void stop(Player player) {
        player.removeTag(BODY_YAW_TAG);
        AnimationManager.stop(player);
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

        // Re-use or obtain tracker WITHOUT closing the registry
        EntityTracker tracker = MinestomPlayerModels.getOrCreate(selectedLimb,
            MinestomAdapter.adapt(player),
            TrackerModifier.DEFAULT,
            created -> {
                created.hideOption(EntityHideOption.DEFAULT);
                applyValues(player, created);
            }
        );
        // Ensure defaultPosition and bodyRotator settings are applied to existing or newly created tracker
        applyValues(player, tracker);

        AnimationManager.animate(tracker, selectedLimb, selectedAnim, () -> {
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
