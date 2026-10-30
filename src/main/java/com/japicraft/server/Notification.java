package com.japicraft.server;

import com.japicraft.constants.Font;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;

public class Notification {
    private static final String BACKGROUND = "?";
    private static final TextColor HIDE_COLOR = TextColor.color(2, 1, 2);

    private static TextColor markTextColor(TextColor color) {
        int r = (color.red() & ~3) | 1;
        int g = (color.green() & ~3) | 2;
        int b = (color.blue() & ~3) | 1;
        return TextColor.color(r, g, b);
    }

    private static ShadowColor markShadowColor(TextColor color) {
        int r = ((color.red() / 4) & ~3) | 1;
        int g = ((color.green() / 4) & ~3) | 2;
        int b = ((color.blue() / 4) & ~3) | 1;
        return ShadowColor.shadowColor(r, g, b, 255);
    }

    private static TextColor safeBackgroundColor(TextColor color) {
        if ((color.red() & 3) == 2 && (color.green() & 3) == 1 && (color.blue() & 3) == 2) {
            return TextColor.color(color.red() & ~3, color.green(), color.blue());
        }
        return color;
    }

    public static void info(Player player, String message) {
        show(player, message, TextColor.fromHexString("#FFFFFF"), TextColor.fromHexString("#282C40"), 40, 20, 40);
    }

    public static void warn(Player player, String message) {
        show(player, message, TextColor.fromHexString("#FF5555"), TextColor.fromHexString("#402828"), 80, 30, 80);
    }

    public static void success(Player player, String message) {
        show(player, message, TextColor.fromHexString("#55FF55"), TextColor.fromHexString("#284028"), 40, 60, 60);
    }

    private static void show(Player player, String message, TextColor textColor, TextColor backgroundColor, int fadeIn, int stay, int fadeOut) {
        player.showTitle(Title.title(Component.empty().shadowColor(ShadowColor.none()).append(
                Component.text(Notification.BACKGROUND).font(Font.DEFAULT).color(Notification.safeBackgroundColor(backgroundColor)),
                Component.text(message).color(Notification.HIDE_COLOR),
                Component.text(message).color(Notification.markTextColor(textColor)).shadowColor(Notification.markShadowColor(textColor)),
                Component.text(Notification.BACKGROUND).font(Font.DEFAULT).color(Notification.HIDE_COLOR)
            ), Component.empty(), fadeIn, stay, fadeOut)
        );
    }
}
