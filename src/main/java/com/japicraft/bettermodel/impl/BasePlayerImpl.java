package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomPlayer;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.emote.EmoteManager;
import java.util.stream.Stream;
import kr.toxicity.model.api.armor.PlayerArmor;
import kr.toxicity.model.api.entity.BasePlayer;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.player.PlayerSkinParts;
import kr.toxicity.model.api.profile.ModelProfile;
import kr.toxicity.model.api.profile.ModelProfileInfo;
import kr.toxicity.model.api.profile.ModelProfileSkin;
import kr.toxicity.model.manager.ProfileManagerImpl;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerSkin;
import net.minestom.server.entity.vehicle.PlayerInputs;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class BasePlayerImpl extends BaseEntityImpl implements BasePlayer {
    private final Player player;
    private @Nullable PlayerSkin profileSkin;
    private ModelProfile profile;

    BasePlayerImpl(@NotNull Player player) {
        super(player);
        this.player = player;
        this.profile = BasePlayerImpl.profileOf(player);
        this.profileSkin = player.getSkin();
    }

    static @NotNull ModelProfile profileOf(@NotNull Player player) {
        ModelProfileInfo info = new ModelProfileInfo(player.getUuid(), player.getUsername());
        PlayerSkin skin = player.getSkin();
        if (skin == null) {
            return ModelProfile.of(info, ModelProfileSkin.EMPTY);
        }
        return ModelProfile.of(info, ProfileManagerImpl.INSTANCE.skin(skin.textures()));
    }

    @Override
    public @NotNull PlatformPlayer platform() {
        return new MinestomPlayer(this.player);
    }

    @Override
    public @NotNull Stream<PlatformPlayer> trackedBy() {
        return Stream.concat(Stream.of(this.platform()), super.trackedBy());
    }

    @Override
    public boolean onWalk() {
        if (CameraAnchor.isAttached(this.player)) {
            return false;
        }
        PlayerInputs inputs = this.player.inputs();
        return inputs.forward() != inputs.backward() || inputs.left() != inputs.right();
    }

    @Override
    public float bodyYaw() {
        Float fixedYaw = this.player.getTag(EmoteManager.BODY_YAW_TAG);
        if (fixedYaw != null) {
            return fixedYaw;
        }
        if (CameraAnchor.isAttached(this.player)) {
            return CameraAnchor.baseYaw(this.player);
        }
        PlayerInputs inputs = this.player.inputs();
        float strafe = inputs.left() == inputs.right() ? 0.0f : (inputs.left() ? 1.0f : -1.0f);
        float yaw = -45.0f * strafe;
        if (inputs.backward() && !inputs.forward()) {
            yaw *= -1.0f;
        }
        return BasePlayerImpl.wrapDegrees(this.player.getPosition().yaw() + yaw);
    }

    @Override
    public float headYaw() {
        if (CameraAnchor.isAttached(this.player)) {
            return CameraAnchor.viewYaw(this.player);
        }
        return this.player.getPosition().yaw();
    }

    @Override
    public float pitch() {
        if (CameraAnchor.isAttached(this.player)) {
            return CameraAnchor.viewPitch(this.player);
        }
        return this.player.getPosition().pitch();
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    @Override
    public void updateInventory() {
        this.player.getInventory().update();
    }

    @Override
    public synchronized @NotNull ModelProfile profile() {
        PlayerSkin skin = this.player.getSkin();
        if (skin != this.profileSkin) {
            this.profileSkin = skin;
            this.profile = BasePlayerImpl.profileOf(this.player);
        }
        return this.profile;
    }

    @Override
    public @NotNull PlayerArmor armors() {
        return PlayerArmor.EMPTY;
    }

    @Override
    public @NotNull PlayerSkinParts skinParts() {
        return new PlayerSkinParts(this.player.getSettings().displayedSkinParts() & 0xFF);
    }
}
