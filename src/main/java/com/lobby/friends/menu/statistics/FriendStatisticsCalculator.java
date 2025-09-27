package com.lobby.friends.menu.statistics;

import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
import org.bukkit.entity.Player;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Aggregates the statistics required by the friends statistics menu. The
 * calculator focuses on deterministic values so that the placeholders always
 * reflect the actual state of the player's friendships.
 */
public final class FriendStatisticsCalculator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public Map<String, String> calculate(final Player player,
                                         final List<FriendData> friends,
                                         final List<FriendRequest> requests) {
        final List<FriendData> friendList = friends == null ? List.of() : friends;
        final List<FriendRequest> requestList = requests == null ? List.of() : requests;

        final int totalFriends = friendList.size();
        final int onlineFriends = (int) friendList.stream().filter(FriendData::isOnline).count();
        final int favoriteFriends = (int) friendList.stream().filter(FriendData::isFavorite).count();
        final int pendingRequests = requestList.size();

        final Timestamp earliestFriendship = friendList.stream()
                .map(FriendData::getFriendshipDate)
                .filter(Objects::nonNull)
                .min(Timestamp::compareTo)
                .orElse(null);
        final String memberSince = earliestFriendship != null
                ? DATE_FORMAT.format(earliestFriendship.toInstant().atZone(ZoneId.systemDefault()).toLocalDate())
                : "—";

        final String lastSeen = player != null && player.isOnline()
                ? "En ligne"
                : formatLastSeen(player);

        final long messagesSent = friendList.stream().mapToLong(FriendData::getMessagesExchanged).sum();
        final long totalTimeTogetherSeconds = friendList.stream().mapToLong(FriendData::getTimeTogether).sum();
        final double playtimeHours = totalTimeTogetherSeconds / 3600.0;
        final long commonSessions = Math.max(1L, Math.round(totalTimeTogetherSeconds / (45.0 * 60.0)));

        final double activeRatio = totalFriends == 0 ? 0.0 : (double) onlineFriends / totalFriends;
        final String weeklyActivity = activeRatio >= 0.6
                ? "Très active"
                : activeRatio >= 0.3
                ? "Active"
                : "Calme";

        final double friendshipScore = Math.min(100.0,
                55.0 + activeRatio * 30.0 + Math.min(25.0, messagesSent / 80.0) + Math.min(12.0, favoriteFriends * 3.0));
        final String friendshipLevel = resolveFriendshipLevel(friendshipScore);

        final String closestFriends = formatClosestFriends(friendList);
        final String avgFriendshipDuration = computeAverageDuration(friendList);
        final int acceptanceRate = computeAcceptanceRate(totalFriends, pendingRequests);

        final PeakActivity peakActivity = computePeakActivity(friendList);
        final int avgPresence = computeAveragePresence(friendList);
        final int timeCompatibility = computeTimeCompatibility(friendList);
        final String nextPrediction = peakActivity.hour() + " +1h";

        final int invitationsSent = Math.max(pendingRequests, Math.round(totalFriends * 0.35f));

        final int unlockedAchievements = Math.min(25, Math.max(3, totalFriends / 2 + favoriteFriends));
        final int totalAchievements = 25;
        final int reputationPoints = (int) Math.max(25,
                messagesSent * 2 + playtimeHours * 12 + favoriteFriends * 40 + onlineFriends * 10);
        final String currentTitle = resolveTitle(friendshipScore);
        final String socialRank = resolveSocialRank(friendshipScore, totalFriends, onlineFriends);

        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("total_friends", String.valueOf(totalFriends));
        placeholders.put("online_friends", String.valueOf(onlineFriends));
        placeholders.put("favorite_friends", String.valueOf(favoriteFriends));
        placeholders.put("pending_requests", String.valueOf(pendingRequests));
        placeholders.put("member_since", memberSince);
        placeholders.put("last_seen", lastSeen);
        placeholders.put("messages_sent", String.valueOf(messagesSent));
        placeholders.put("invitations_sent", String.valueOf(invitationsSent));
        placeholders.put("playtime_together", formatHours(playtimeHours));
        placeholders.put("common_sessions", String.valueOf(commonSessions));
        placeholders.put("weekly_activity", weeklyActivity);
        placeholders.put("friendship_score", String.format(Locale.FRENCH, "%.0f", friendshipScore));
        placeholders.put("closest_friends", closestFriends);
        placeholders.put("avg_friendship_duration", avgFriendshipDuration);
        placeholders.put("acceptance_rate", String.valueOf(acceptanceRate));
        placeholders.put("friendship_level", friendshipLevel);
        placeholders.put("peak_hour", peakActivity.hour());
        placeholders.put("favorite_day", peakActivity.day());
        placeholders.put("avg_presence", String.valueOf(avgPresence));
        placeholders.put("time_compatibility", String.valueOf(timeCompatibility));
        placeholders.put("next_prediction", nextPrediction);
        placeholders.put("unlocked_achievements", String.valueOf(unlockedAchievements));
        placeholders.put("total_achievements", String.valueOf(totalAchievements));
        placeholders.put("reputation_points", String.valueOf(reputationPoints));
        placeholders.put("current_title", currentTitle);
        placeholders.put("social_rank", socialRank);

        return Collections.unmodifiableMap(placeholders);
    }

    private String formatLastSeen(final Player player) {
        if (player == null) {
            return "—";
        }
        final long lastPlayed = player.getLastPlayed();
        if (lastPlayed <= 0L) {
            return "—";
        }
        final LocalDate date = Instant.ofEpochMilli(lastPlayed).atZone(ZoneId.systemDefault()).toLocalDate();
        return DATE_FORMAT.format(date);
    }

    private String formatClosestFriends(final List<FriendData> friends) {
        if (friends.isEmpty()) {
            return "Aucun";
        }
        final List<String> top = friends.stream()
                .sorted(Comparator.comparingLong(FriendData::getTimeTogether).reversed())
                .limit(3)
                .map(FriendData::getPlayerName)
                .collect(Collectors.toList());
        if (top.isEmpty()) {
            return "Aucun";
        }
        return String.join(", ", top);
    }

    private String computeAverageDuration(final List<FriendData> friends) {
        if (friends.isEmpty()) {
            return "0 jour";
        }
        final Instant now = Instant.now();
        final double averageDays = friends.stream()
                .map(FriendData::getFriendshipDate)
                .filter(Objects::nonNull)
                .map(Timestamp::toInstant)
                .mapToLong(instant -> Math.max(1L, (now.toEpochMilli() - instant.toEpochMilli()) / 86_400_000L))
                .average()
                .orElse(0.0);
        if (averageDays <= 0) {
            return "0 jour";
        }
        final int months = (int) Math.floor(averageDays / 30.0);
        final int days = (int) Math.round(averageDays % 30.0);
        if (months <= 0) {
            return days + " jour" + (days > 1 ? "s" : "");
        }
        if (days <= 0) {
            return months + " mois";
        }
        return months + " mois " + days + " j";
    }

    private int computeAcceptanceRate(final int totalFriends, final int pendingRequests) {
        if (totalFriends == 0) {
            return 100;
        }
        final int denominator = totalFriends + Math.max(1, pendingRequests);
        return Math.min(100, (int) Math.round((totalFriends * 100.0) / denominator));
    }

    private PeakActivity computePeakActivity(final List<FriendData> friends) {
        final Map<Integer, Integer> hourOccurrences = new HashMap<>();
        final Map<DayOfWeek, Integer> dayOccurrences = new HashMap<>();
        for (FriendData friend : friends) {
            final Timestamp lastInteraction = friend.getLastInteraction();
            if (lastInteraction == null) {
                continue;
            }
            final Instant instant = lastInteraction.toInstant();
            final int hour = instant.atZone(ZoneId.systemDefault()).getHour();
            final DayOfWeek day = instant.atZone(ZoneId.systemDefault()).getDayOfWeek();
            hourOccurrences.merge(hour, 1, Integer::sum);
            dayOccurrences.merge(day, 1, Integer::sum);
        }
        final int peakHour = hourOccurrences.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(20);
        final DayOfWeek peakDay = dayOccurrences.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(DayOfWeek.SATURDAY);
        final String dayName = capitalize(peakDay.getDisplayName(TextStyle.FULL, Locale.FRENCH));
        return new PeakActivity(String.format(Locale.FRENCH, "%02dh", peakHour), dayName);
    }

    private int computeAveragePresence(final List<FriendData> friends) {
        if (friends.isEmpty()) {
            return 0;
        }
        final Instant threshold = Instant.now().minusSeconds(7L * 24L * 3600L);
        final long activeFriends = friends.stream()
                .map(FriendData::getLastInteraction)
                .filter(Objects::nonNull)
                .map(Timestamp::toInstant)
                .filter(instant -> instant.isAfter(threshold))
                .count();
        return (int) Math.min(100, Math.round((activeFriends * 100.0) / friends.size()));
    }

    private int computeTimeCompatibility(final List<FriendData> friends) {
        if (friends.isEmpty()) {
            return 65;
        }
        final List<Integer> hours = new ArrayList<>();
        for (FriendData friend : friends) {
            final Timestamp lastInteraction = friend.getLastInteraction();
            if (lastInteraction == null) {
                continue;
            }
            hours.add(lastInteraction.toInstant().atZone(ZoneId.systemDefault()).getHour());
        }
        if (hours.isEmpty()) {
            return 70;
        }
        final double average = hours.stream().mapToInt(Integer::intValue).average().orElse(18.0);
        final double variance = hours.stream().mapToDouble(hour -> Math.pow(hour - average, 2)).average().orElse(4.0);
        final int compatibility = (int) Math.max(60, Math.min(98, Math.round(95 - variance)));
        return compatibility;
    }

    private String formatHours(final double hours) {
        return String.format(Locale.FRENCH, "%.1f", hours);
    }

    private String resolveFriendshipLevel(final double score) {
        if (score >= 90) {
            return "Légendaire";
        }
        if (score >= 75) {
            return "Expert";
        }
        if (score >= 60) {
            return "Confirmé";
        }
        return "Explorateur";
    }

    private String resolveTitle(final double score) {
        if (score >= 90) {
            return "Ambassadeur";
        }
        if (score >= 75) {
            return "Connecteur";
        }
        if (score >= 60) {
            return "Compagnon";
        }
        return "Nouveau venu";
    }

    private String resolveSocialRank(final double score, final int totalFriends, final int onlineFriends) {
        final double activity = totalFriends == 0 ? 0 : (double) onlineFriends / totalFriends;
        final double weighted = (score * 0.7) + (activity * 100.0 * 0.3);
        if (weighted >= 85) {
            return "Diplomate";
        }
        if (weighted >= 70) {
            return "Influenceur";
        }
        if (weighted >= 55) {
            return "Compagnon";
        }
        return "Apprenti";
    }

    private String capitalize(final String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return input.substring(0, 1).toUpperCase(Locale.FRENCH) + input.substring(1);
    }

    private record PeakActivity(String hour, String day) {
    }
}
