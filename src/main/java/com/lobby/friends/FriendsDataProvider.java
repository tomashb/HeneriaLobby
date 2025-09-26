package com.lobby.friends;

import org.bukkit.entity.Player;

/**
 * Provides dynamic data for the friends system UI. Implementations are
 * responsible for aggregating statistics and counts that populate the main
 * friends menu placeholders.
 */
public interface FriendsDataProvider {

    /**
     * Resolves the placeholder data for the given player.
     *
     * @param player the player viewing the menu
     * @return the data snapshot that should be displayed
     */
    FriendsPlaceholderData resolve(Player player);
}

