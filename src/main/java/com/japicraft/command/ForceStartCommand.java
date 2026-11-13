package com.japicraft.command;

import com.japicraft.game.GameStageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.entity.Player;

public final class ForceStartCommand extends Command {
    public ForceStartCommand() {
        super("forcestart");
        setDefaultExecutor((sender, _) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Run /forcestart in-game in the lobby you want to start.", NamedTextColor.RED));
                return;
            }
            boolean started = GameStageManager.forceStart(player);
            sender.sendMessage(Component.text(started ? "Game force started." : "You must be in a lobby with different roles selected for every player.", started ? NamedTextColor.GREEN : NamedTextColor.RED));
        });
    }
}
