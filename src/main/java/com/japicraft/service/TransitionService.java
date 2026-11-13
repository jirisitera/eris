package com.japicraft.service;

import com.japicraft.Eris;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.minestom.server.entity.Player;

import java.util.Objects;

public final class TransitionService {
    private static final Key FONT = Key.key(Eris.NAMESPACE, "transition");
    public static void show(Player player, Effect effect, TextColor tint, int fadeIn, int stay, int fadeOut) {
        player.showTitle(title(effect, tint, fadeIn, stay, fadeOut));
    }
    public static Title title(Effect effect, TextColor tint, int fadeIn, int stay, int fadeOut) {
        return Title.title(Component.text(effect.glyph).font(FONT).color(tint).shadowColor(ShadowColor.none()), Component.empty(), fadeIn, stay, fadeOut);
    }

    public enum Effect {
        LOADING("="),
        BACKGROUND("|"),
        SHUTTER("-");

        private final String glyph;

        Effect(String glyph) {
            this.glyph = glyph;
        }
    }
}
