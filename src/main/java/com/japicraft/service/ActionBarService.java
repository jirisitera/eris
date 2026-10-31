package com.japicraft.service;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

public class ActionBarService {
    private static final Tag<Component> TEXT_TAG = Tag.Transient("actionBar");

    public static void schedule(Player player) {
        player.scheduler().scheduleTask(() -> {
            if (!player.isOnline()) {
                return TaskSchedule.stop();
            }
            player.sendActionBar(player.getTag(TEXT_TAG.defaultValue(Component.empty())));
            return TaskSchedule.tick(30);
        }, TaskSchedule.nextTick());
    }

    public static void setText(Player player, Component text) {
        player.setTag(TEXT_TAG, text);
        player.sendActionBar(text);
    }
}
