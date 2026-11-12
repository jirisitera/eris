package com.japicraft.environment;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta;
import net.minestom.server.entity.metadata.display.TextDisplayMeta;
import net.minestom.server.tag.Tag;

/**
 * One lobby-only display per avatar, visible to every player in the lobby.
 */
final class LobbyRoleLabel {
    private static final Tag<Entity> LABEL = Tag.Transient("lobbyRoleLabel");

    static void update(Player player, boolean ready) {
        Entity label = player.getTag(LABEL);
        if (label == null || label.isRemoved()) {
            label = new Entity(EntityType.TEXT_DISPLAY);
            label.setNoGravity(true);
            label.editEntityMeta(TextDisplayMeta.class, meta -> {
                meta.setBillboardRenderConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
                meta.setScale(new Vec(0.65));
                meta.setBrightness(15, 15);
                meta.setShadow(true);
                meta.setUseDefaultBackground(false);
                meta.setBackgroundColor(0xB018202D);
                meta.setLineWidth(240);
            });
            player.setTag(LABEL, label);
            Entity created = label;
            // In front of the torso, toward the lobby camera; leaves faces visible.
            label.setInstance(player.getInstance(), InstanceManager.lobbyAvatarPosition(player).add(0, 1.05, -0.65))
                .thenRun(() -> {
                    if (player.getTag(LABEL) != created) created.remove();
                });
        }
        label.editEntityMeta(TextDisplayMeta.class, meta -> meta.setText(
            Component.text(player.getUsername(), NamedTextColor.WHITE).appendNewline()
                .append(Component.text(PlayerRole.get(player).displayName(), NamedTextColor.AQUA)).appendNewline()
                .append(Component.text(ready ? "Ready" : "Not ready", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY))));
    }

    static void detach(Player player) {
        Entity label = player.getTag(LABEL);
        player.removeTag(LABEL);
        if (label != null) label.remove();
    }
}
