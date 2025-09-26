package com.lobby.menus;

import org.bukkit.entity.Player;

/**
 * Extension point for menus that need to execute custom logic when the
 * underlying inventory is closed. This is especially useful for menus that
 * schedule repeating tasks or keep per-player state while opened.
 */
public interface CloseableMenu extends Menu {

    /**
     * Invoked when the menu inventory is closed by the given player.
     *
     * @param player the player who closed the menu
     */
    void handleClose(Player player);
}

