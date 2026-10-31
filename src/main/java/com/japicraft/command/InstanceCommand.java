package com.japicraft.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;

import java.util.Collection;

public class InstanceCommand extends Command {
    public InstanceCommand() {
        super("instance");
        setCondition((sender, _) -> !(sender instanceof Player player) || player.getPermissionLevel() >= 4);

        setDefaultExecutor((sender, _) -> {
            Collection<Instance> instances = MinecraftServer.getInstanceManager().getInstances();

            sender.sendMessage(Component.text("Loaded instances: " + instances.size(), NamedTextColor.GRAY));
            for (Instance instance : instances) {
                int playerCount = instance.getPlayers().size();
                long entityCount = instance.getEntities().size() - playerCount;

                sender.sendMessage(Component.text("- ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(instance.getUuid().toString()).color(NamedTextColor.AQUA))
                    .append(Component.text(String.format(" (players: %d, non-player entities: %d)", playerCount, entityCount)).color(NamedTextColor.GRAY))
                );
            }
        });
    }
}
