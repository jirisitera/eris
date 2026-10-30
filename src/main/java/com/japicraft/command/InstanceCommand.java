package com.japicraft.command;

import com.japicraft.camera.Avatar;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.MapProbe;
import com.japicraft.game.Director;
import com.japicraft.game.Lobby;
import com.japicraft.environment.InstanceManager;
import com.japicraft.events.PlayerSetupManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.suggestion.SuggestionEntry;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.utils.entity.EntityFinder;

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
