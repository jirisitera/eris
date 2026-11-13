package com.japicraft;

import com.japicraft.command.ForceStartCommand;
import com.japicraft.command.InstanceCommand;
import com.japicraft.command.StopCommand;
import com.japicraft.environment.InstanceManager;
import com.japicraft.integration.bettermodel.BetterModelMinestom;
import com.japicraft.integration.voicechat.VoiceChatManager;
import com.japicraft.player.PlayerInputManager;
import com.japicraft.player.PlayerSetupManager;
import com.japicraft.player.PlayerUpdateManager;
import com.japicraft.server.GlobalResourcePack;
import com.japicraft.server.ServerListManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.command.CommandSender;

import java.util.Scanner;

public final class Eris {
    public static final String NAMESPACE = "eris";
    public static final ComponentLogger LOGGER = ComponentLogger.logger(Eris.class);
    public static CommandSender CONSOLE;

    void main() {
        MinecraftServer server = MinecraftServer.init(new Auth.Offline());

        prepareEnvironment();
        applyGlobalProperties();
        registerCommands();
        registerManagers();
        initializeConsole();

        server.start("0.0.0.0", 25565);
    }

    private void prepareEnvironment() {
        Runtime.getRuntime().addShutdownHook(new Thread(MinecraftServer::stopCleanly));
        LOGGER.atInfo().log(Component.text("Loaded JVM environment."));
    }

    private void applyGlobalProperties() {
        MinecraftServer.setBrandName("Eris");
        LOGGER.atInfo().log(Component.text("Loaded server properties."));
    }

    private void initializeConsole() {
        new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (scanner.hasNextLine()) {
                MinecraftServer.getCommandManager().execute(Eris.CONSOLE, scanner.nextLine());
            }
        }).start();
    }

    private void registerCommands() {
        CommandManager commandManager = MinecraftServer.getCommandManager();

        commandManager.register(new InstanceCommand());
        commandManager.register(new ForceStartCommand());
        commandManager.register(new StopCommand());

        Eris.CONSOLE = commandManager.getConsoleSender();
        Eris.LOGGER.atInfo().log(Component.text("Loaded console commands."));
    }

    private void registerManagers() {
        PlayerSetupManager.register();
        PlayerUpdateManager.register();
        PlayerInputManager.register();

        GlobalResourcePack.register();

        new InstanceManager().register();
        ServerListManager.register();

        BetterModelMinestom.register();
        LOGGER.atInfo().log(Component.text("Loaded BetterModel integration."));

        VoiceChatManager.register();
        LOGGER.atInfo().log(Component.text("Loaded Simple Voice Chat integration."));
    }
}
