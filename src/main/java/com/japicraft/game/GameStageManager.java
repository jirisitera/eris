package com.japicraft.game;

import com.japicraft.camera.BombView;
import com.japicraft.camera.CameraAnchor;
import com.japicraft.camera.CursorProbe;
import com.japicraft.constants.PostEffect;
import com.japicraft.emote.EmoteManager;
import com.japicraft.emote.EmoteWheel;
import com.japicraft.environment.StructureLoader;
import com.japicraft.integration.voicechat.VoiceChatManager;
import com.japicraft.lobby.LobbyRoleLabel;
import com.japicraft.lobby.LobbySelection;
import com.japicraft.player.PlayerRole;
import com.japicraft.service.ActionBarService;
import com.japicraft.service.TransitionService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.dialog.*;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.Instance;
import net.minestom.server.network.packet.server.common.ClearDialogPacket;
import net.minestom.server.network.packet.server.common.PostEffectsPacket;
import net.minestom.server.network.packet.server.common.ShowDialogPacket;
import net.minestom.server.scoreboard.Sidebar;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.TaskSchedule;

import java.util.*;

/**
 * instance-owned lobby state retained across reconnects
 */
public final class GameStageManager {
    private static final int REQUIRED_PLAYERS = 3;
    private static final Tag<State> STATE = Tag.Transient("gameStage");
    private static final Tag<Sidebar> SIDEBAR = Tag.Transient("gameSidebar");

    private static State state(Instance instance) {
        State state = instance.getTag(STATE);
        if (state == null) {
            state = new State();
            instance.setTag(STATE, state);
        }
        return state;
    }

    private static State lobbyFor(Player player) {
        if (player.getInstance() == null) return null;
        State state = state(player.getInstance());
        return state.stage == Stage.LOBBY && state.players.get(player.getUuid()) == player ? state : null;
    }

    public static synchronized Stage stage(Player player) {
        return player.getInstance() == null ? Stage.LOBBY : state(player.getInstance()).stage;
    }

    public static synchronized Stage stage(Instance instance) {
        return state(instance).stage;
    }

    public static boolean isGame(Player player) {
        return stage(player) == Stage.GAME;
    }

    public static synchronized void join(Player player) {
        State state = state(player.getInstance());
        Player previous = state.players.put(player.getUuid(), player);
        if (previous != player) {
            state.selection.unready(player.getUuid());
            if (previous != null) removeSidebar(previous);
        }
        player.setTag(PlayerRole.TAG, state.selection.role(player.getUuid()));
        applyStage(player, state.stage);
        refresh(state);
    }

    public static synchronized void leave(Player player) {
        if (player.getInstance() == null) return;
        State state = state(player.getInstance());
        if (!state.players.remove(player.getUuid(), player)) return;
        state.selection.unready(player.getUuid());
        LobbyRoleLabel.detach(player);
        removeSidebar(player);
        refresh(state);
    }

    public static synchronized void toggleReady(Player player) {
        State state = lobbyFor(player);
        if (state == null) return;
        state.selection.toggleReady(player.getUuid());
        if (state.selection.canStart(state.players.keySet())) {
            start(player.getInstance(), state);
        }
        refresh(state);
    }

    public static synchronized void selectRole(Player player, PlayerRole role) {
        State state = lobbyFor(player);
        if (state == null) return;
        state.selection.select(player.getUuid(), role);
        player.setTag(PlayerRole.TAG, role);
        refresh(state);
    }

    /**
     * probe pixel eight RGB payload: active, selected role, ready/conflict/player-count flags
     */
    public static synchronized int lobbyUiData(Player player) {
        State state = lobbyFor(player);
        if (state == null) return 0;
        int flags = (state.selection.isReady(player.getUuid()) ? 1 : 0)
            | (state.selection.uniqueRoles(state.players.keySet()) ? 0 : 2)
            | (state.players.size() < REQUIRED_PLAYERS ? 4 : 0);
        return 0xFF0000 | (state.selection.role(player.getUuid()).ordinal() << 8) | flags;
    }

    /**
     * testing override for player count/readiness; duplicate roles still cannot start
     */
    public static synchronized boolean forceStart(Player player) {
        State state = lobbyFor(player);
        if (state == null) return false;
        if (!state.selection.uniqueRoles(state.players.keySet())) return false;
        start(player.getInstance(), state);
        refresh(state);
        return true;
    }

    private static void start(Instance instance, State state) {

        state.players.values().forEach(player -> TransitionService.show(player, TransitionService.Effect.LOADING, NamedTextColor.WHITE, 40, 40, 40));

        instance.scheduler().scheduleTask(() -> {
            state.stage = Stage.GAME;
            state.selection.resetReady();
            StructureLoader.startGame(instance);
            state.players.values().forEach(player -> applyStage(player, Stage.GAME));
        }, TaskSchedule.seconds(3), TaskSchedule.stop());

    }

    public static synchronized void lose(Instance instance, String message) {
        State state = state(instance);
        if (state.stage != Stage.GAME) {
            return;
        }
        RoundSummary summary = StructureLoader.summary(instance, message);
        StructureLoader.resetGame(instance);
        state.stage = Stage.LOBBY;
        state.selection.resetReady();
        state.players.values().forEach(player -> applyStage(player, Stage.LOBBY));
        refresh(state);
        state.players.values().forEach(player -> showSummary(player, summary));
    }

    private static void showSummary(Player player, RoundSummary summary) {
        Dialog dialog = new Dialog.Notice(new DialogMetadata(
            Component.text("Round lost", NamedTextColor.RED), null, true, false, DialogAfterAction.CLOSE,
            List.of(new DialogBody.PlainMessage(Component.text(summary.text()), 300)), List.of()),
            new DialogActionButton(Component.text("Close"), null, 200, null));
        player.sendPacket(new ShowDialogPacket(dialog));
    }

    private static void applyStage(Player player, Stage stage) {
        removeSidebar(player);
        boolean playing = stage == Stage.GAME;
        PlayerRole role = PlayerRole.get(player);

        CameraAnchor.exitLean(player);
        EmoteWheel.detach(player);
        BombView.reset(player);

        player.sendPacket(new ClearDialogPacket());

        CameraAnchor.attach(player);

        if (playing) {
            BombView.attach(player);
        }
        CursorProbe.forceUpdate(player);
        // body vehicle positioning before BetterModel location capture
        player.scheduler().scheduleNextTick(() -> {
            if (player.getInstance() == null || stage(player) != stage) return;
            CameraAnchor.syncBodyMounts(player);
            EmoteManager.resetPose(player, "steve", playing ? "idle" : "standing");
            CursorProbe.forceUpdate(player);
        });

        VoiceChatManager.setMuted(player, playing && role == PlayerRole.MANUAL_EXPERT);
        VoiceChatManager.setDeafened(player, playing && role == PlayerRole.DEFUSAL_HELPER);

        player.sendPacket(new PostEffectsPacket(!playing ? List.of(PostEffect.CURSOR) :
            role == PlayerRole.BOMB_DEFUSER ? List.of(PostEffect.TIMER, PostEffect.BLINDNESS, PostEffect.CURSOR) : List.of(PostEffect.TIMER, PostEffect.HUD, PostEffect.CURSOR)));

        if (playing) {
            Sidebar sidebar = createGameSidebar(role);
            player.setTag(SIDEBAR, sidebar);
            sidebar.addViewer(player);
        }
    }

    private static void removeSidebar(Player player) {
        Sidebar sidebar = player.getTag(SIDEBAR);
        player.removeTag(SIDEBAR);
        if (sidebar != null) sidebar.removeViewer(player);
    }

    private static void refresh(State state) {
        if (state.stage != Stage.LOBBY) return;
        for (Player player : state.players.values()) {
            ActionBarService.setText(player, Component.empty());
            LobbyRoleLabel.update(player, state.selection.isReady(player.getUuid()));
            CursorProbe.update(player);
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
        final LobbySelection selection = new LobbySelection();
        Stage stage = Stage.LOBBY;
    }
}
