package com.lobby.utils;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessageUtilsTest {

    @Test
    @DisplayName("colorize() should translate legacy color codes")
    void colorizeShouldTranslateColorCodes() {
        final String input = "&6Lobby";
        final String expected = ChatColor.GOLD + "Lobby";
        assertEquals(expected, MessageUtils.colorize(input));
    }

    @Test
    @DisplayName("applyPrefix() should handle null messages gracefully")
    void applyPrefixShouldHandleNullMessages() {
        final String result = MessageUtils.applyPrefix(null);
        assertEquals("", ChatColor.stripColor(result));
    }
}
