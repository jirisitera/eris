package com.japicraft.controller;

import com.japicraft.camera.MapProbe;
import com.japicraft.server.PostEffect;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;

public final class DoorController {
    public static final Point LEFT_DOOR_LOWER = new Pos(5, 11, 2);
    public static final Point LEFT_DOOR_UPPER = new Pos(5, 12, 2);
    public static final Point RIGHT_DOOR_LOWER = new Pos(11, 11, 2);
    public static final Point RIGHT_DOOR_UPPER = new Pos(11, 12, 2);

    private static final Sound SOUND_DOOR_OPEN = Sound.sound(Key.key("minecraft:block.iron_door.open"), Sound.Source.BLOCK, 1.0F, 1.0F);
    private static final Sound SOUND_DOOR_CLOSE = Sound.sound(Key.key("minecraft:block.iron_door.close"), Sound.Source.BLOCK, 1.0F, 1.0F);

    private boolean leftClosed = true;
    private boolean rightClosed = true;

    private float energy = 100.0F;
    private int energyTick = 0;

    public boolean isLeftClosed() {
        return leftClosed;
    }

    public boolean isRightClosed() {
        return rightClosed;
    }

    public boolean isClosed(boolean isLeft) {
        return isLeft ? leftClosed : rightClosed;
    }

    public void reset(InstanceContainer instance) {
        leftClosed = true;
        rightClosed = true;
        energy = 100.0F;
        energyTick = 0;
        applyDoorState(instance, LEFT_DOOR_LOWER, LEFT_DOOR_UPPER, false);
        applyDoorState(instance, RIGHT_DOOR_LOWER, RIGHT_DOOR_UPPER, false);
    }

    public void toggleLeft(Player player, InstanceContainer instance) {
        if (energy <= 0.0F) return;
        leftClosed = !leftClosed;
        applyDoorState(instance, LEFT_DOOR_LOWER, LEFT_DOOR_UPPER, !leftClosed); // Minecraft "open" property: open=true means open, open=false means closed
        player.playSound(leftClosed ? SOUND_DOOR_CLOSE : SOUND_DOOR_OPEN, LEFT_DOOR_LOWER.x(), LEFT_DOOR_LOWER.y(), LEFT_DOOR_LOWER.z());
        syncEnergyDisplay(player);
    }

    public void toggleRight(Player player, InstanceContainer instance) {
        if (energy <= 0.0F) return;
        rightClosed = !rightClosed;
        applyDoorState(instance, RIGHT_DOOR_LOWER, RIGHT_DOOR_UPPER, !rightClosed);
        player.playSound(rightClosed ? SOUND_DOOR_CLOSE : SOUND_DOOR_OPEN, RIGHT_DOOR_LOWER.x(), RIGHT_DOOR_LOWER.y(), RIGHT_DOOR_LOWER.z());
        syncEnergyDisplay(player);
    }

    public void tick(Player player, InstanceContainer instance) {
        energyTick++;
        if (energyTick % 20 == 0) {
            int usage = currentUsage();
            // 1 usage drains 0.1% per sec, 2 usage drains 0.25%, 3 usage drains 0.5%
            float drain = switch (usage) {
                case 1 -> 0.12F;
                case 2 -> 0.28F;
                case 3 -> 0.55F;
                default -> 0.1F;
            };
            energy = Math.max(0.0F, energy - drain);
            if (energy <= 0.0F) {
                if (leftClosed || rightClosed) {
                    // blackout: doors fail open
                    leftClosed = false;
                    rightClosed = false;
                    applyDoorState(instance, LEFT_DOOR_LOWER, LEFT_DOOR_UPPER, true);
                    applyDoorState(instance, RIGHT_DOOR_LOWER, RIGHT_DOOR_UPPER, true);
                    player.playSound(SOUND_DOOR_OPEN, LEFT_DOOR_LOWER.x(), LEFT_DOOR_LOWER.y(), LEFT_DOOR_LOWER.z());
                    player.playSound(SOUND_DOOR_OPEN, RIGHT_DOOR_LOWER.x(), RIGHT_DOOR_LOWER.y(), RIGHT_DOOR_LOWER.z());
                }
                if (!PostEffect.isBlackout(player)) {
                    PostEffect.applyBlackout(player);
                }
            } else if (PostEffect.isBlackout(player)) {
                PostEffect.removeBlackout(player);
            }
            syncEnergyDisplay(player);
        }
    }

    public int currentUsage() {
        int usage = 1; // base monitor consumption
        if (leftClosed) usage++;
        if (rightClosed) usage++;
        return usage;
    }

    public void syncEnergyDisplay(Player player) {
        MapProbe.setEnergy(player, Math.round(energy), currentUsage());
    }

    private void applyDoorState(InstanceContainer instance, Point lower, Point upper, boolean open) {
        Block lowerBlock = instance.getBlock(lower);
        Block upperBlock = instance.getBlock(upper);
        if (lowerBlock.compare(Block.IRON_DOOR)) {
            instance.setBlock(lower, lowerBlock.withProperty("open", String.valueOf(open)));
        }
        if (upperBlock.compare(Block.IRON_DOOR)) {
            instance.setBlock(upper, upperBlock.withProperty("open", String.valueOf(open)));
        }
    }
}
