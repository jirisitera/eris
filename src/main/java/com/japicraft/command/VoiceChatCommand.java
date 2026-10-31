package com.japicraft.command;

import com.japicraft.voicechat.VoiceChatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.minecraft.ArgumentEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.utils.entity.EntityFinder;

public class VoiceChatCommand extends Command {
    public VoiceChatCommand() {
        super("voicechat");

        ArgumentEntity targetArg = ArgumentType.Entity("player").onlyPlayers(true).singleEntity(true);

        addSyntax((sender, context) -> {
            EntityFinder finder = context.get(targetArg);
            Player target = finder.findFirstPlayer(sender);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            boolean muted = VoiceChatManager.toggleMuted(target);
            sender.sendMessage(Component.text(target.getUsername(), NamedTextColor.AQUA)
                .append(Component.text(muted ? " is now voice-muted." : " is no longer voice-muted.", NamedTextColor.GRAY)));
        }, ArgumentType.Literal("mute"), targetArg);

        addSyntax((sender, context) -> {
            EntityFinder finder = context.get(targetArg);
            Player target = finder.findFirstPlayer(sender);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.", NamedTextColor.RED));
                return;
            }
            boolean deafened = VoiceChatManager.toggleDeafened(target);
            sender.sendMessage(Component.text(target.getUsername(), NamedTextColor.AQUA)
                .append(Component.text(deafened ? " is now voice-deafened." : " is no longer voice-deafened.", NamedTextColor.GRAY)));
        }, ArgumentType.Literal("deafen"), targetArg);
    }
}
