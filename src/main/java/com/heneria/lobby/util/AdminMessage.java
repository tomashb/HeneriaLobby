package com.heneria.lobby.util;

import org.bukkit.command.CommandSender;

/**
 * Utility for sending formatted admin command messages.
 */
public final class AdminMessage {

    private static final String PREFIX = "§f[LobbyAdmin] §7";

    private AdminMessage() {
    }

    public static void success(CommandSender sender, String msg) {
        sender.sendMessage("§a✔ " + PREFIX + msg);
    }

    public static void error(CommandSender sender, String msg) {
        sender.sendMessage("§c✖ " + PREFIX + msg);
    }

    public static void info(CommandSender sender, String msg) {
        sender.sendMessage("§3ℹ " + PREFIX + msg);
    }
}

