package com.lobby.friends.menu;

import org.bukkit.entity.Player;

/**
 * Handles interactions triggered from the friends main menu.
 */
@FunctionalInterface
public interface FriendsMenuActionHandler {

    /**
     * Processes the requested action for the given player.
     *
     * @param player the player who clicked the item
     * @param action the configured action identifier
     * @return {@code true} if the action was handled successfully, {@code false}
     * otherwise
     */
    boolean handle(Player player, String action);
}

