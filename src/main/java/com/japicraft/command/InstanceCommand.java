package com.japicraft.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.util.*;

public class InstanceCommand extends Command {
    public InstanceCommand() {
        super("instance", "instances");
        setCondition((sender, _) -> !(sender instanceof Player player) || player.getPermissionLevel() >= 4);

        setDefaultExecutor((sender, _) -> {
            Collection<Instance> instances = MinecraftServer.getInstanceManager().getInstances();

            sender.sendMessage(Component.text("Loaded instances: " + instances.size(), NamedTextColor.GRAY));
            for (Instance instance : instances) {
                String uuid = instance.getUuid().toString();
                long entityCount = instance.getEntities().stream().filter(entity -> !entity.getEntityType().equals(EntityType.PLAYER)).count();
                int playerCount = instance.getPlayers().size();
                boolean isCurrent = sender instanceof Player p && p.getInstance() == instance;

                Component line = Component.text("- ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(uuid, isCurrent ? NamedTextColor.GREEN : NamedTextColor.AQUA))
                    .append(Component.text(String.format(" (entities: %d, players: %d)", entityCount, playerCount), NamedTextColor.GRAY));

                sender.sendMessage(line);
            }
        });
    }
}
