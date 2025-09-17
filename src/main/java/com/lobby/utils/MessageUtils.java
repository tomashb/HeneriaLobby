package com.lobby.utils;

import com.lobby.LobbyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class MessageUtils {

    private MessageUtils() {
    }

    public static void sendPrefixedMessage(final CommandSender sender, final String message) {
        if (sender == null || message == null) {
            return;
        }
        sender.sendMessage(applyPrefix(message));
    }

    public static void sendPrefixedMessage(final CommandSender sender, final List<String> messages) {
        if (sender == null || messages == null) {
            return;
        }
        messages.forEach(line -> sender.sendMessage(applyPrefix(line)));
    }

    public static String applyPrefix(final String message) {
        final String prefix = LobbyPlugin.getInstance() != null
                ? LobbyPlugin.getInstance().getConfigManager().getMessagesConfig().getString("prefix", "")
                : "";
        final String sanitized = message == null ? "" : message;
        return colorize(prefix + sanitized);
    }

    public static String colorize(final String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }
}
