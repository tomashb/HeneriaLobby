package com.heneria.lobby.util;

import org.bukkit.ChatColor;

/**
 * Common text utilities for applying the Heneria color formatting.
 */
public final class Utils {

    private Utils() {
    }

    /**
     * Translate the legacy colour codes using '&' into Bukkit colour codes.
     *
     * @param text the text containing legacy colour codes
     * @return the formatted text, or an empty string if input is null
     */
    public static String format(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}
