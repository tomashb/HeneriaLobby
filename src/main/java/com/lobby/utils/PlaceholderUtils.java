package com.lobby.utils;

import com.lobby.LobbyPlugin;
import com.lobby.data.PlayerData;
import com.lobby.economy.EconomyManager;
import com.lobby.holograms.HologramManager;
import com.lobby.holograms.PlaceholderProcessor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class PlaceholderUtils {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withLocale(Locale.FRENCH);

    private PlaceholderUtils() {
    }

    public static String applyPlaceholders(final LobbyPlugin plugin, final String text, final Player player) {
        if (text == null || plugin == null) {
            return text == null ? "" : text;
        }

        String processed = text;

        final HologramManager hologramManager = plugin.getHologramManager();
        if (hologramManager != null) {
            final PlaceholderProcessor processor = hologramManager.getPlaceholderProcessor();
            if (processor != null) {
                processed = processor.process(processed, player);
            }
        }

        if (player == null) {
            return processed;
        }

        processed = processed.replace("%player_name%", player.getName());

        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return processed;
        }

        final PlayerData data = economyManager.getPlayerData(player.getUniqueId());
        if (data == null) {
            return processed;
        }

        processed = processed
                .replace("%player_coins%", String.valueOf(data.coins()))
                .replace("%player_tokens%", String.valueOf(data.tokens()))
                .replace("%player_first_join%", formatInstant(data.firstJoin()))
                .replace("%player_last_join%", formatInstant(data.lastJoin()))
                .replace("%player_playtime%", formatPlaytime(data.totalPlaytime()));

        return processed;
    }

    public static List<String> applyPlaceholders(final LobbyPlugin plugin, final List<String> lines, final Player player) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        return lines.stream()
                .filter(Objects::nonNull)
                .map(line -> applyPlaceholders(plugin, line, player))
                .toList();
    }

    private static String formatInstant(final Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        return DATE_FORMATTER.format(instant.atZone(ZoneId.systemDefault()));
    }

    private static String formatPlaytime(final long totalSeconds) {
        if (totalSeconds <= 0L) {
            return "0s";
        }
        final Duration duration = Duration.ofSeconds(totalSeconds);
        final long days = duration.toDays();
        final long hours = duration.minusDays(days).toHours();
        final long minutes = duration.minusDays(days).minusHours(hours).toMinutes();
        final long seconds = duration.minusDays(days).minusHours(hours).minusMinutes(minutes).toSeconds();

        final StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append('j').append(' ');
        }
        if (hours > 0) {
            builder.append(hours).append('h').append(' ');
        }
        if (minutes > 0) {
            builder.append(minutes).append('m').append(' ');
        }
        if (seconds > 0 && builder.length() == 0) {
            builder.append(seconds).append('s');
        }
        final String result = builder.toString().trim();
        return result.isEmpty() ? "0s" : result;
    }
}
