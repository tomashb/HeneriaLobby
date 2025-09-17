package com.lobby.utils;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public final class LogUtils {

    private LogUtils() {
    }

    public static void info(final JavaPlugin plugin, final String message) {
        log(plugin, Level.INFO, message, null);
    }

    public static void warning(final JavaPlugin plugin, final String message) {
        log(plugin, Level.WARNING, message, null);
    }

    public static void severe(final JavaPlugin plugin, final String message) {
        log(plugin, Level.SEVERE, message, null);
    }

    public static void severe(final JavaPlugin plugin, final String message, final Throwable throwable) {
        log(plugin, Level.SEVERE, message, throwable);
    }

    private static void log(final JavaPlugin plugin, final Level level, final String message, final Throwable throwable) {
        if (plugin == null || plugin.getLogger() == null) {
            return;
        }

        if (throwable == null) {
            plugin.getLogger().log(level, message);
        } else {
            plugin.getLogger().log(level, message, throwable);
        }
    }
}
