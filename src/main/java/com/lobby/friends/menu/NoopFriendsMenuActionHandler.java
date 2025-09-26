package com.lobby.friends.menu;

import org.bukkit.entity.Player;

/**
 * Default fallback action handler used until dedicated implementations are
 * provided for each friends feature.
 */
public class NoopFriendsMenuActionHandler implements FriendsMenuActionHandler {

    @Override
    public boolean handle(final Player player, final String action) {
        if (player != null) {
            player.sendMessage("§cCette fonctionnalité n'est pas encore disponible.");
        }
        return false;
    }
}

