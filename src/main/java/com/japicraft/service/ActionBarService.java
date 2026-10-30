package com.japicraft.service;

import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

public class ActionBarService {
    private final Tag<Component> TEXT_TAG = Tag.Transient("actionBar");
    public void schedule(Player player) {
        player.scheduler().scheduleTask(() -> player.sendActionBar(player.getTag(TEXT_TAG.defaultValue(Component.empty()))), TaskSchedule.nextTick(), TaskSchedule.tick(30));
    }
    public void setText(Player player, Component text) {
        player.setTag(TEXT_TAG, text);
    }
}
