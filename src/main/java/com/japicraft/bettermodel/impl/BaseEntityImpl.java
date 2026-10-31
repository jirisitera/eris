package com.japicraft.bettermodel.impl;

import com.japicraft.bettermodel.MinestomAdapter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import kr.toxicity.model.api.entity.BaseEntity;
import kr.toxicity.model.api.platform.PlatformEntity;
import kr.toxicity.model.api.platform.PlatformPlayer;
import kr.toxicity.model.api.util.TransformedItemStack;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.MetadataDef;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.potion.TimedPotion;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

class BaseEntityImpl implements BaseEntity {
    static final Tag<String> MODEL_DATA = Tag.String("bettermodel:model_data");
    private static final Map<UUID, Long> LAST_HURT = new ConcurrentHashMap<>();
    protected final @NotNull Entity entity;

    static void markHurt(@NotNull UUID uuid) {
        long now = System.currentTimeMillis();
        LAST_HURT.values().removeIf(hurt -> now - hurt >= 500L);
        LAST_HURT.put(uuid, now);
    }

    static void forget(@NotNull UUID uuid) {
        LAST_HURT.remove(uuid);
    }

    BaseEntityImpl(@NotNull Entity entity) {
        this.entity = entity;
    }

    @Override
    public @NotNull PlatformEntity platform() {
        return MinestomAdapter.adapt(this.entity);
    }

    @Override
    public @Nullable Component customName() {
        if (this.entity instanceof Player player) {
            Component name = player.getDisplayName();
            return name != null ? name : Component.text(player.getUsername());
        }
        var meta = this.entity.getEntityMeta();
        return meta.isCustomNameVisible() ? meta.get(MetadataDef.CUSTOM_NAME) : null;
    }

    @Override
    public @NotNull Object handle() {
        return this.entity;
    }

    @Override
    public int id() {
        return this.entity.getEntityId();
    }

    @Override
    public @NotNull UUID uuid() {
        return this.entity.getUuid();
    }

    @Override
    public boolean dead() {
        return this.entity.isRemoved() || (this.entity instanceof LivingEntity living && living.isDead());
    }

    @Override
    public boolean ground() {
        return this.entity.isOnGround();
    }

    @Override
    public boolean invisible() {
        return this.entity.isInvisible() || this.entity.hasEffect(PotionEffect.INVISIBILITY);
    }

    @Override
    public boolean glow() {
        return this.entity.isGlowing() || this.entity.hasEffect(PotionEffect.GLOWING);
    }

    @Override
    public boolean onWalk() {
        Pos now = this.entity.getPosition();
        Pos before = this.entity.getPreviousPosition();
        double dx = now.x() - before.x();
        double dz = now.z() - before.z();
        return dx * dx + dz * dz > 1.0E-6;
    }

    @Override
    public boolean fly() {
        if (this.entity instanceof Player player) {
            return player.isFlying();
        } else if (this.entity instanceof LivingEntity living) {
            return living.isFlyingWithElytra();
        }
        return false;
    }

    @Override
    public double scale() {
        return this.entity instanceof LivingEntity living ? living.getAttributeValue(Attribute.SCALE) : 1.0;
    }

    @Override
    public float pitch() {
        return this.entity.getPosition().pitch();
    }

    @Override
    public float bodyYaw() {
        return this.entity.getPosition().yaw();
    }

    @Override
    public float yaw() {
        return this.entity.getPosition().yaw();
    }

    @Override
    public float headYaw() {
        return this.entity.getPosition().yaw();
    }

    @Override
    public float damageTick() {
        if (!(this.entity instanceof LivingEntity living)) {
            return 0.0f;
        }
        Long hurt = LAST_HURT.get(this.entity.getUuid());
        if (hurt == null) {
            return 0.0f;
        }
        long elapsed = System.currentTimeMillis() - hurt;
        if (elapsed >= 500L) {
            return 0.0f;
        }
        float knockback = 1.0f - (float) living.getAttributeValue(Attribute.KNOCKBACK_RESISTANCE);
        return (1.0f - (float) elapsed / 500.0f) * knockback;
    }

    @Override
    public float walkSpeed() {
        if (!(this.entity instanceof LivingEntity)) {
            return 0.0f;
        }
        if (!this.entity.isOnGround()) {
            return 1.0f;
        }
        int speed = this.amplifier(PotionEffect.SPEED);
        int slow = this.amplifier(PotionEffect.SLOWNESS);
        return Math.clamp(1.0f + (float) (speed - slow) * 0.2f, 0.2f, 2.0f);
    }

    private int amplifier(@NotNull PotionEffect effect) {
        TimedPotion potion = this.entity.getEffect(effect);
        return potion == null ? 0 : potion.potion().amplifier();
    }

    @Override
    public @NotNull Vector3f passengerPosition(@NotNull Vector3f dest) {
        List<Vec> points = this.entity.getEntityType().entityAttachments("passenger");
        if (points == null || points.isEmpty()) {
            return dest.set(0.0f, (float) this.entity.getBoundingBox().height(), 0.0f);
        }
        Vec point = points.getFirst().mul(this.scale());
        return dest.set((float) point.x(), (float) point.y(), (float) point.z());
    }

    @Override
    public @NotNull Stream<PlatformPlayer> trackedBy() {
        return this.entity.getViewers().stream().map(MinestomAdapter::adapt);
    }

    @Override
    public @NotNull TransformedItemStack mainHand() {
        if (this.entity instanceof LivingEntity living) {
            return TransformedItemStack.of(MinestomAdapter.adapt(living.getItemInMainHand()));
        }
        return TransformedItemStack.empty();
    }

    @Override
    public @NotNull TransformedItemStack offHand() {
        if (this.entity instanceof LivingEntity living) {
            ItemStack item = living.getItemInOffHand();
            if (item.material() == Material.ECHO_SHARD) {
                return TransformedItemStack.empty();
            }
            return TransformedItemStack.of(MinestomAdapter.adapt(item));
        }
        return TransformedItemStack.empty();
    }

    @Override
    public @Nullable String modelData() {
        return this.entity.getTag(MODEL_DATA);
    }

    @Override
    public void modelData(@Nullable String modelData) {
        this.entity.setTag(MODEL_DATA, modelData);
    }
}
