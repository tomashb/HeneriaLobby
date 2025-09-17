package com.lobby.utils;

import com.lobby.LobbyPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    public static void sendConfigMessage(final CommandSender sender, final String path, final Map<String, ?> placeholders) {
        final String message = getConfigMessage(path, placeholders);
        if (sender == null || message == null || message.isEmpty()) {
            return;
        }
        sendPrefixedMessage(sender, message);
    }

    public static void sendConfigMessage(final CommandSender sender, final String path) {
        sendConfigMessage(sender, path, Collections.emptyMap());
    }

    public static String getConfigMessage(final String path, final Map<String, ?> placeholders) {
        final FileConfiguration messages = getMessagesConfig();
        if (messages == null) {
            return null;
        }
        final String raw = messages.getString(path);
        if (raw == null) {
            return null;
        }
        return applyPlaceholders(raw, placeholders);
    }

    public static String getConfigMessage(final String path) {
        return getConfigMessage(path, Collections.emptyMap());
    }

    public static List<String> getConfigMessageList(final String path, final Map<String, ?> placeholders) {
        final FileConfiguration messages = getMessagesConfig();
        if (messages == null) {
            return List.of();
        }
        final List<String> lines = messages.getStringList(path);
        if (lines.isEmpty()) {
            return lines;
        }
        return lines.stream().map(line -> applyPlaceholders(line, placeholders)).toList();
    }

    public static List<String> getConfigMessageList(final String path) {
        return getConfigMessageList(path, Collections.emptyMap());
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

    private static FileConfiguration getMessagesConfig() {
        return LobbyPlugin.getInstance() != null ? LobbyPlugin.getInstance().getConfigManager().getMessagesConfig() : null;
    }

    private static String applyPlaceholders(final String message, final Map<String, ?> placeholders) {
        if (message == null) {
            return null;
        }
        if (placeholders == null || placeholders.isEmpty()) {
            return colorize(message);
        }
        String formatted = message;
        for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
            final String key = entry.getKey();
            if (key == null) {
                continue;
            }
            final String value = Objects.toString(entry.getValue(), "");
            formatted = formatted.replace("{" + key + "}", value);
        }
        return colorize(formatted);
    }
}
