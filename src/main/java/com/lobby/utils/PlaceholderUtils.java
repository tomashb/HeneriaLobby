package com.lobby.utils;

import com.lobby.LobbyPlugin;
import com.lobby.data.PlayerData;
import com.lobby.economy.EconomyManager;
import com.lobby.settings.PlayerSettings;
import com.lobby.settings.PlayerSettingsManager;
import com.lobby.stats.GameStats;
import com.lobby.stats.GlobalStats;
import com.lobby.stats.StatsManager;
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
import java.util.UUID;

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

        if (plugin.getConfirmationManager() != null) {
            processed = plugin.getConfirmationManager().applyPlaceholders(player, processed);
        }

        if (player == null) {
            return processed;
        }

        processed = processed.replace("%player_name%", player.getName());

        final UUID uuid = player.getUniqueId();

        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager != null) {
            final PlayerData data = economyManager.getPlayerData(uuid);
            if (data != null) {
                processed = processed
                        .replace("%player_coins%", String.valueOf(data.coins()))
                        .replace("%player_tokens%", String.valueOf(data.tokens()))
                        .replace("%player_first_join%", formatInstant(data.firstJoin()))
                        .replace("%player_last_join%", formatInstant(data.lastJoin()))
                        .replace("%player_playtime%", formatPlaytime(data.totalPlaytime()))
                        .replace("%player_playtime_total%", formatPlaytime(data.totalPlaytime()));
            }
        }

        processed = applyStatsPlaceholders(plugin, processed, uuid);
        processed = applySettingsPlaceholders(plugin, processed, uuid);

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

    private static String applyStatsPlaceholders(final LobbyPlugin plugin, final String text, final UUID uuid) {
        if (text == null || uuid == null || !text.contains("%stats_")) {
            return text;
        }
        final StatsManager statsManager = plugin.getStatsManager();
        if (statsManager == null) {
            return text;
        }

        String processed = text;
        final GlobalStats globalStats = statsManager.getGlobalStats(uuid);
        processed = processed
                .replace("%stats_global_games%", String.valueOf(globalStats.getTotalGames()))
                .replace("%stats_global_wins%", String.valueOf(globalStats.getTotalWins()))
                .replace("%stats_global_losses%", String.valueOf(globalStats.getTotalLosses()))
                .replace("%stats_global_ratio%", formatRatio(globalStats.getRatio()))
                .replace("%stats_global_kills%", String.valueOf(globalStats.getTotalKills()))
                .replace("%stats_global_deaths%", String.valueOf(globalStats.getTotalDeaths()))
                .replace("%stats_global_playtime%", globalStats.getFormattedPlaytime());

        processed = applyGameStats(processed, statsManager.getPlayerStats(uuid, "BEDWARS"),
                "bedwars", true, false);
        processed = applyGameStats(processed, statsManager.getPlayerStats(uuid, "NEXUS"),
                "nexus", true, false);
        processed = applyGameStats(processed, statsManager.getPlayerStats(uuid, "ZOMBIE"),
                "zombie", false, true);
        processed = applyGameStats(processed, statsManager.getPlayerStats(uuid, "CUSTOM"),
                "custom", false, false);

        return processed;
    }

    private static String applySettingsPlaceholders(final LobbyPlugin plugin, final String text, final UUID uuid) {
        if (text == null || uuid == null || (!text.contains("%setting_") && !text.contains("%lang_"))) {
            return text;
        }
        final PlayerSettingsManager settingsManager = plugin.getPlayerSettingsManager();
        if (settingsManager == null) {
            return text;
        }
        final PlayerSettings settings = settingsManager.getPlayerSettings(uuid);
        if (settings == null) {
            return text;
        }

        String processed = text
                .replace("%setting_private_messages_display%", settings.getPrivateMessagesDisplay())
                .replace("%setting_group_requests_display%", settings.getGroupRequestsDisplay())
                .replace("%setting_visibility_display%", settings.getVisibilityDisplay())
                .replace("%setting_ui_sounds_display%", settings.getUiSoundsDisplay())
                .replace("%setting_particles_display%", settings.getParticlesDisplay())
                .replace("%setting_music_display%", settings.getMusicDisplay())
                .replace("%setting_clan_notifications_display%", settings.getClanNotificationsDisplay())
                .replace("%setting_system_notifications_display%", settings.getSystemNotificationsDisplay())
                .replace("%setting_language_flag%", settings.getLanguageFlag())
                .replace("%setting_language_name%", settings.getLanguageName())
                .replace("%setting_language_display%", settings.getLanguageDisplay());

        processed = processed
                .replace("%lang_fr_status%", settings.getLanguageStatus("fr"))
                .replace("%lang_en_status%", settings.getLanguageStatus("en"))
                .replace("%lang_es_status%", settings.getLanguageStatus("es"));

        return processed;
    }

    private static String applyGameStats(final String text,
                                         final GameStats stats,
                                         final String prefix,
                                         final boolean useSpecialOne,
                                         final boolean useSpecialTwo) {
        if (stats == null) {
            return text;
        }
        String processed = text
                .replace("%stats_" + prefix + "_games%", String.valueOf(stats.getGamesPlayed()))
                .replace("%stats_" + prefix + "_wins%", String.valueOf(stats.getWins()))
                .replace("%stats_" + prefix + "_losses%", String.valueOf(stats.getLosses()))
                .replace("%stats_" + prefix + "_ratio%", formatRatio(stats.getRatio()))
                .replace("%stats_" + prefix + "_kills%", String.valueOf(stats.getKills()))
                .replace("%stats_" + prefix + "_deaths%", String.valueOf(stats.getDeaths()))
                .replace("%stats_" + prefix + "_playtime%", stats.getFormattedPlaytime());

        if (useSpecialOne) {
            processed = processed.replace("%stats_" + prefix + "_beds%", String.valueOf(stats.getSpecialStat1()))
                    .replace("%stats_" + prefix + "_destroyed%", String.valueOf(stats.getSpecialStat1()));
        }
        if (useSpecialTwo) {
            processed = processed.replace("%stats_" + prefix + "_record%", String.valueOf(stats.getSpecialStat2()));
        }
        return processed;
    }

    private static String formatRatio(final double ratio) {
        if (Double.isNaN(ratio) || Double.isInfinite(ratio)) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", ratio);
    }
}
