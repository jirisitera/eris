package com.japicraft.environment;

import com.japicraft.server.PostEffect;
import com.japicraft.service.ActionBarService;
import com.japicraft.voicechat.VoiceChatManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.tag.Tag;

import java.util.*;

/**
 * Lobby state lives on the instance, so reconnects retain an ongoing game.
 */
public final class GameStageManager {
    private static final int REQUIRED_PLAYERS = 3;
    private static final Tag<State> STATE = Tag.Transient("gameStage");

    private static State state(Instance instance) {
        State state = instance.getTag(STATE);
        if (state == null) {
            state = new State();
            instance.setTag(STATE, state);
        }
        return state;
    }

    public static synchronized Stage stage(Player player) {
        return player.getInstance() == null ? Stage.LOBBY : state(player.getInstance()).stage;
    }

    public static boolean isGame(Player player) {
        return stage(player) == Stage.GAME;
    }

    public static synchronized void join(Player player) {
        State state = state(player.getInstance());
        Player previous = state.players.put(player.getUuid(), player);
        if (previous != player) {
            state.ready.remove(player.getUuid());
            if (previous != null) removeSidebar(state, previous);
        }
        applyStage(player, state.stage);
        refresh(state);
    }

    public static synchronized void leave(Player player) {
        if (player.getInstance() == null) return;
        State state = state(player.getInstance());
        if (!state.players.remove(player.getUuid(), player)) return;
        state.ready.remove(player.getUuid());
        refresh(state);
    }

    public static synchronized void toggleReady(Player player) {
        if (player.getInstance() == null) return;
        State state = state(player.getInstance());
        if (state.stage != Stage.LOBBY || state.players.get(player.getUuid()) != player) return;
        if (!state.ready.add(player.getUuid())) state.ready.remove(player.getUuid());
        if (state.players.size() == REQUIRED_PLAYERS && state.ready.size() == state.players.size()) {
            state.stage = Stage.GAME;
            StructureLoader.startGame(player.getInstance());
            state.players.values().forEach(participant -> applyStage(participant, Stage.GAME));
        }
        refresh(state);
    }

    private static void applyStage(Player player, Stage stage) {
        removeSidebar(state(player.getInstance()), player);
        boolean playing = stage == Stage.GAME;
        PlayerRole role = PlayerRole.get(player);

        VoiceChatManager.setMuted(player, playing && role == PlayerRole.MANUAL_EXPERT);
        VoiceChatManager.setDeafened(player, playing && role == PlayerRole.DEFUSAL_HELPER);

        player.sendPacket(new PostEffectsPacket(!playing ? List.of(PostEffect.TIMER, PostEffect.HUD, PostEffect.LOADING, PostEffect.CURSOR) :
            role == PlayerRole.BOMB_DEFUSER ? List.of(PostEffect.TIMER, PostEffect.BLINDNESS, PostEffect.CURSOR) : List.of(PostEffect.TIMER, PostEffect.HUD, PostEffect.CURSOR)));
    }

    private static void removeSidebar(State state, Player player) {
        Sidebar sidebar = state.sidebars.remove(player.getUuid());
        if (sidebar != null) sidebar.removeViewer(player);
    }

    private static Sidebar createSidebar() {
        Sidebar sidebar = new Sidebar(Component.text("Eris", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(" | Lobby", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
        String[] lines = {"space-top", "role", "space-role", "players", "space-players",
            "ready", "space-ready", "status", "space-status", "controls", "space-bottom"};
        for (int i = 0; i < lines.length; i++) {
            sidebar.createLine(new Sidebar.ScoreboardLine(lines[i], Component.empty(),
                lines.length - i, Sidebar.NumberFormat.blank()));
        }
        return sidebar;
    }

    private static void refresh(State state) {
        for (Player player : state.players.values()) {
            if (state.stage == Stage.GAME) {
                if (!state.sidebars.containsKey(player.getUuid())) {
                    Sidebar sidebar = createGameSidebar(PlayerRole.get(player));
                    state.sidebars.put(player.getUuid(), sidebar);
                    ActionBarService.setText(player, Component.empty());
                    sidebar.addViewer(player);
                }
                continue;
            }
            Sidebar sidebar = state.sidebars.get(player.getUuid());
            boolean newSidebar = sidebar == null;
            if (newSidebar) {
                sidebar = createSidebar();
                state.sidebars.put(player.getUuid(), sidebar);
                ActionBarService.setText(player, Component.empty());
            }
            PlayerRole role = PlayerRole.get(player);
            boolean ready = state.ready.contains(player.getUuid());
            sidebar.updateLineContent("role", Component.text("Role: ", NamedTextColor.GRAY)
                .append(Component.text(role == null ? "Unassigned" : role.displayName(), NamedTextColor.AQUA)));
            sidebar.updateLineContent("players", Component.text("Players: ", NamedTextColor.GRAY)
                .append(Component.text(state.players.size() + "/" + REQUIRED_PLAYERS,
                    state.players.size() == REQUIRED_PLAYERS ? NamedTextColor.GREEN : NamedTextColor.YELLOW)));
            sidebar.updateLineContent("ready", Component.text("Ready: ", NamedTextColor.GRAY)
                .append(Component.text(state.ready.size() + "/" + REQUIRED_PLAYERS, NamedTextColor.GREEN)));
            sidebar.updateLineContent("status", Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(ready ? "Ready" : "Not ready", ready ? NamedTextColor.GREEN : NamedTextColor.RED)));
            sidebar.updateLineContent("controls", Component.text("F", NamedTextColor.YELLOW, TextDecoration.BOLD)
                .append(Component.text(ready ? ": cancel ready" : ": ready up", NamedTextColor.WHITE)
                    .decoration(TextDecoration.BOLD, false)));
            if (newSidebar) sidebar.addViewer(player);
        }
    }

    private static Sidebar createGameSidebar(PlayerRole role) {
        Sidebar sidebar = new Sidebar(Component.text("Eris", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(" | Controls", NamedTextColor.YELLOW).decoration(TextDecoration.BOLD, false)));
        List<Component> lines = new ArrayList<>();
        lines.add(Component.empty());
        lines.add(Component.text(role == null ? "Unassigned" : role.displayName(), NamedTextColor.AQUA));
        lines.add(Component.empty());
        lines.add(control("Mouse", "look around / aim"));
        if (role != null) {
            lines.add(control("W", switch (role) {
                case BOMB_DEFUSER -> "inspect bomb";
                case MANUAL_EXPERT -> "read manual";
                case DEFUSAL_HELPER -> "lean over bomb";
            }));
            lines.add(control("S", "lean back"));
            if (role == PlayerRole.BOMB_DEFUSER) {
                lines.add(control("Left click", "press bomb button"));
            }
        }
        lines.add(Component.empty());
        lines.add(control("F", "toggle emote wheel"));
        lines.add(control("Left click", "select emote"));
        if (role == PlayerRole.BOMB_DEFUSER) {
            lines.add(Component.text("Emotes: lean back first", NamedTextColor.GRAY));
        }
        lines.add(Component.empty());
        for (int i = 0; i < lines.size(); i++) {
            sidebar.createLine(new Sidebar.ScoreboardLine("control-" + i, lines.get(i),
                lines.size() - i, Sidebar.NumberFormat.blank()));
        }
        return sidebar;
    }

    private static Component control(String key, String action) {
        return Component.text(key, NamedTextColor.YELLOW, TextDecoration.BOLD)
            .append(Component.text(": " + action, NamedTextColor.WHITE).decoration(TextDecoration.BOLD, false));
    }

    public enum Stage {LOBBY, GAME}

    private static final class State {
        final Map<UUID, Player> players = new HashMap<>();
        final Map<UUID, Sidebar> sidebars = new HashMap<>();
        final Set<UUID> ready = new HashSet<>();
        Stage stage = Stage.LOBBY;
    }
}
