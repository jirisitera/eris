package com.japicraft.command;

import net.kyori.adventure.text.Component;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

public class StopCommand extends Command {
    private static final Component KICK_MESSAGE = Component.text("Eris is shutting down.").appendNewline().append(Component.text("Please wait a moment before reconnecting."));

    public StopCommand() {
        super("stop");
        setCondition((sender, _) -> !(sender instanceof Player player) || player.getPermissionLevel() >= 4);
        setDefaultExecutor((sender, _) -> {
            sender.sendMessage(Component.text("Shutting down gracefully..."));
            MinecraftServer.getConnectionManager().getOnlinePlayers().forEach(player -> player.kick(StopCommand.KICK_MESSAGE));
            MinecraftServer.stopCleanly();
            System.exit(0);
        });
    }
}
