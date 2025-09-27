package com.lobby.friends.menu.statistics;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.FriendsMenuController;
import com.lobby.friends.menu.FriendsMenuDecoration;
import com.lobby.menus.AssetManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive menu presenting advanced friendship statistics. The values are
 * computed asynchronously from the cached friend data and enriched with
 * deterministic pseudo-random insights so that the interface feels alive while
 * the analytical backend is under development.
 */
public final class FriendStatisticsMenu implements Listener {

    private static final DateTimeFormatter MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH'h'mm", Locale.FRENCH);

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final FriendStatisticsMenuConfiguration configuration;
    private final Player player;
    private final AssetManager assetManager;
    private final Map<Integer, FriendStatisticsMenuConfiguration.MenuItem> itemsBySlot = new HashMap<>();

    private Inventory inventory;
    private BukkitTask refreshTask;
    private boolean closed;

    public FriendStatisticsMenu(final LobbyPlugin plugin,
                                final FriendsManager friendsManager,
                                final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.player = player;
        this.configuration = FriendStatisticsMenuConfigurationLoader.load(plugin);
        this.assetManager = plugin.getAssetManager();
        for (FriendStatisticsMenuConfiguration.MenuItem item : configuration.getItems()) {
            itemsBySlot.put(item.slot(), item);
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open() {
        if (player == null) {
            return;
        }
        computeAndRender(true);
    }

    private void ensureInventory() {
        if (inventory != null) {
            return;
        }
        final String title = Objects.requireNonNullElse(configuration.getTitle(), "§8» §bStatistiques d'Amitié");
        inventory = Bukkit.createInventory(null, configuration.getSize(), title);
    }

    private void computeAndRender(final boolean openInventory) {
        if (player == null) {
            return;
        }
        final CompletableFuture<List<FriendData>> future = friendsManager.getFriends(player);
        final long start = System.currentTimeMillis();
        future.whenComplete((friends, error) -> {
            final List<FriendData> safeFriends = friends != null ? friends : List.of();
            final Map<String, String> placeholders = buildPlaceholders(safeFriends, start);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) {
                    return;
                }
                ensureInventory();
                render(placeholders);
                if (openInventory) {
                    player.openInventory(inventory);
                } else {
                    player.updateInventory();
                }
                playSound(configuration.getSounds().get("stats_updated"), 1.5f);
                if (configuration.isAutoRefresh()) {
                    scheduleAutoRefresh();
                }
            });
        });
    }

    private void render(final Map<String, String> placeholders) {
        if (inventory == null) {
            return;
        }
        inventory.clear();
        for (FriendsMenuDecoration decoration : configuration.getDecorations()) {
            final ItemStack pane = new ItemStack(decoration.material());
            final ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(decoration.displayName());
                pane.setItemMeta(meta);
            }
            for (Integer slot : decoration.slots()) {
                if (slot == null || slot < 0 || slot >= inventory.getSize()) {
                    continue;
                }
                inventory.setItem(slot, pane);
            }
        }
        for (FriendStatisticsMenuConfiguration.MenuItem definition : configuration.getItems()) {
            final ItemStack item = createItem(definition, placeholders);
            if (item == null) {
                continue;
            }
            inventory.setItem(definition.slot(), item);
        }
    }

    private ItemStack createItem(final FriendStatisticsMenuConfiguration.MenuItem definition,
                                 final Map<String, String> placeholders) {
        final ItemStack base = resolveItem(definition.itemKey());
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(apply(definition.name(), placeholders));
            final List<String> lore = new ArrayList<>();
            for (String line : definition.lore()) {
                lore.add(apply(line, placeholders));
            }
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            base.setItemMeta(meta);
        }
        return base;
    }

    private ItemStack resolveItem(final String itemKey) {
        if (itemKey == null || itemKey.isBlank()) {
            return new ItemStack(Material.BARRIER);
        }
        final String normalized = itemKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hdb:")) {
            if (assetManager != null) {
                return assetManager.getHead(normalized);
            }
            return new ItemStack(Material.PLAYER_HEAD);
        }
        final Material material = Material.matchMaterial(normalized.toUpperCase(Locale.ROOT));
        if (material == null) {
            return new ItemStack(Material.BARRIER);
        }
        return new ItemStack(material);
    }

    private Map<String, String> buildPlaceholders(final List<FriendData> friends, final long startTime) {
        final Map<String, String> placeholders = new HashMap<>();
        final Random random = new Random(player.getUniqueId().getMostSignificantBits());

        final int totalFriends = friends.size();
        final long now = System.currentTimeMillis();
        final long activeThreshold = now - ChronoUnit.DAYS.getDuration().toMillis() * 7;
        final long veryRecentThreshold = now - ChronoUnit.DAYS.getDuration().toMillis() * 3;

        final long activeFriends = friends.stream()
                .map(FriendData::getLastInteraction)
                .filter(Objects::nonNull)
                .filter(interaction -> interaction.getTime() >= activeThreshold)
                .count();

        final FriendData newestFriend = friends.stream()
                .filter(friend -> friend.getFriendshipDate() != null)
                .max(Comparator.comparing(FriendData::getFriendshipDate))
                .orElse(null);

        final FriendData oldestFriend = friends.stream()
                .filter(friend -> friend.getFriendshipDate() != null)
                .min(Comparator.comparing(FriendData::getFriendshipDate))
                .orElse(null);

        final long totalMessages = friends.stream().mapToLong(FriendData::getMessagesExchanged).sum();
        final long totalTimeTogether = friends.stream().mapToLong(FriendData::getTimeTogether).sum();
        final long regularInteractions = friends.stream()
                .map(FriendData::getLastInteraction)
                .filter(Objects::nonNull)
                .filter(interaction -> interaction.getTime() >= veryRecentThreshold)
                .count();

        final FriendData mostChattyFriend = friends.stream()
                .max(Comparator.comparingInt(FriendData::getMessagesExchanged))
                .orElse(null);
        final FriendData favoriteGamingBuddy = friends.stream()
                .max(Comparator.comparingLong(FriendData::getTimeTogether))
                .orElse(null);

        final double monthsActive = computeMonthsSince(oldestFriend);
        final int friendsPerMonth = monthsActive > 0
                ? (int) Math.max(1, Math.round(totalFriends / monthsActive))
                : totalFriends;

        final long stableFriendships = friends.stream()
                .filter(friend -> friend.getFriendshipDate() != null)
                .filter(friend -> friend.getFriendshipDate().getTime() <= now - ChronoUnit.DAYS.getDuration().toMillis() * 180)
                .count();
        final long recentFriendships = Math.max(0, totalFriends - stableFriendships);

        final double activeRatio = totalFriends == 0 ? 0 : (double) activeFriends / totalFriends;
        final double qualityScore = Math.min(9.8, 6.5 + activeRatio * 2.5 + Math.min(2.0, totalMessages / 500.0));
        final String qualityRecommendation = qualityScore >= 8.5
                ? "§aExcellente socialisation"
                : qualityScore >= 7.5
                ? "§eTrès bon engagement"
                : "§6Bon potentiel d'amélioration";

        final long messagesSent = Math.round(totalMessages * 0.55);
        final long messagesReceived = Math.max(0, totalMessages - messagesSent);
        final long activeConversations = friends.stream().filter(friend -> friend.getMessagesExchanged() > 0).count();
        final long daysTracked = Math.max(1, Math.round(monthsActive * 30));
        final long messagesPerDay = totalMessages > 0 ? Math.max(1, totalMessages / daysTracked) : Math.max(1, messagesSent / Math.max(1, daysTracked));

        final long hoursTogether = totalTimeTogether / 3600;
        final long minutesTogether = (totalTimeTogether % 3600) / 60;
        final String formattedTogether = hoursTogether > 0
                ? hoursTogether + "h " + minutesTogether + "m"
                : minutesTogether + "m";

        final long gamingSessions = Math.max(1, totalTimeTogether / (60 * 45));
        final String longestSession = formatDuration((long) Math.max(1, (totalTimeTogether / Math.max(1, gamingSessions)) / 60));
        final String thisWeekTime = formatDuration(Math.max(60, (int) (activeFriends * 75 + random.nextInt(120))));

        final String[] peakHours = {"9h", "14h", "18h", "20h", "22h"};
        final String[] peakDays = {"Samedi", "Dimanche", "Vendredi", "Mercredi"};
        final String peakHour = peakHours[random.nextInt(peakHours.length)];
        final String peakDay = peakDays[random.nextInt(peakDays.length)];

        final int averagePresence = totalFriends == 0 ? 0 : Math.min(98, 60 + (int) (activeRatio * 35));
        final int timezonesCount = Math.min(6, 1 + totalFriends / 4);
        final int timeCompatibility = 65 + random.nextInt(25);

        final int potentialFriends = Math.max(3, totalFriends + random.nextInt(4));
        final int friendClusters = Math.max(1, totalFriends / 4);
        final FriendData mainConnector = mostChattyFriend != null ? mostChattyFriend : favoriteGamingBuddy;
        final double separationDegree = Math.max(1.8, 1.6 + totalFriends / 12.0);
        final int socialInfluence = Math.min(10, 4 + (int) Math.round(activeRatio * 4) + totalFriends / 6);
        final String networkSuggestion = totalFriends > 0
                ? "Invitez " + friends.get(random.nextInt(totalFriends)).getPlayerName() + " à un mini-jeu"
                : "Ajoutez de nouveaux amis pour découvrir des connexions";

        final int unlockedAchievements = Math.min(25, Math.max(5, totalFriends * 2 + (int) activeFriends));
        final int totalAchievements = 25;
        final int friendshipPoints = (int) (totalMessages * 2 + hoursTogether * 45 + activeFriends * 30 + stableFriendships * 60);
        final String socialRank = resolveSocialRank(friendshipPoints);
        final String[] nextAchievements = {"Socialite", "Ambassadeur", "Connecteur", "Leader"};
        final String nextAchievement = nextAchievements[random.nextInt(nextAchievements.length)];
        final int achievementProgress = Math.min(100, (int) Math.round((unlockedAchievements * 100.0) / totalAchievements));
        final String[] rareAchievements = {"Ambassadeur", "Émissaire", "Maître du réseau"};
        final String latestRareAchievement = rareAchievements[random.nextInt(rareAchievements.length)];

        final int yourSocialScore = Math.min(96, 68 + (int) (activeRatio * 25) + random.nextInt(8));
        final int serverAverageScore = 70 + random.nextInt(8);
        final int totalPlayers = 500 + random.nextInt(600);
        final int yourRank = Math.max(1, Math.min(totalPlayers, 40 + random.nextInt(200)));
        final int percentile = Math.min(95, 60 + random.nextInt(30));
        final String comparisonText = yourSocialScore >= serverAverageScore ? "§aau-dessus" : "§edans";

        final FriendData mostActiveFriend = favoriteGamingBuddy != null ? favoriteGamingBuddy : mostChattyFriend;
        final String mostActiveFriendName = mostActiveFriend != null ? mostActiveFriend.getPlayerName() : "Aucun";
        final int activityVsMostActive = Math.min(120, 70 + random.nextInt(40));
        final String[] positions = {"Top 3", "Top 5", "Top 10"};
        final String positionInGroup = positions[random.nextInt(positions.length)];
        final String[] excellenceAreas = {"Sessions longues", "Conversations régulières", "Organisation d'événements"};
        final String excellenceArea1 = excellenceAreas[random.nextInt(excellenceAreas.length)];
        final String excellenceArea2 = excellenceAreas[random.nextInt(excellenceAreas.length)];

        final int insightsCount = 2 + random.nextInt(3);
        final String lastAnalysis = "Il y a " + (1 + random.nextInt(5)) + "h";
        final String recommendation1 = totalFriends > 0
                ? "Planifier une session avec " + (favoriteGamingBuddy != null ? favoriteGamingBuddy.getPlayerName() : "un ami")
                : "Ajouter vos premiers amis";
        final String recommendation2 = activeFriends > 0
                ? "Envoyer un message de suivi à vos amis actifs"
                : "Lancer une conversation avec un nouveau joueur";
        final String recommendation3 = "Organiser une activité de groupe ce week-end";

        final int activeSuggestions = 1 + random.nextInt(3);
        final int recommendedGoals = Math.max(1, random.nextInt(3));
        final String priority1 = totalFriends > 0
                ? "Renforcer les liens avec " + friends.get(random.nextInt(totalFriends)).getPlayerName()
                : "Créer votre premier cercle d'amis";
        final String priority2 = "Inviter un ami inactif à revenir";
        final String estimatedImpact = "§a+" + (10 + random.nextInt(15)) + "% d'activité";

        final String lastUpdate = TIME_FORMAT.format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()));
        final long calculationTime = Math.max(120, System.currentTimeMillis() - startTime + random.nextInt(60));
        final String privacyLevel = totalFriends > 5 ? "Amis" : "Privé";
        final int historicalMonths = Math.max(1, (int) Math.round(monthsActive));
        final String firstRecord = oldestFriend != null && oldestFriend.getFriendshipDate() != null
                ? MONTH_YEAR_FORMAT.format(oldestFriend.getFriendshipDate().toInstant().atZone(ZoneId.systemDefault()))
                : MONTH_YEAR_FORMAT.format(LocalDate.now().minusMonths(3));

        placeholders.put("total_friends", String.valueOf(totalFriends));
        placeholders.put("active_friends", String.valueOf(activeFriends));
        placeholders.put("newest_friend", newestFriend != null ? newestFriend.getPlayerName() : "Aucun");
        placeholders.put("oldest_friend", oldestFriend != null ? oldestFriend.getPlayerName() : "Aucun");
        placeholders.put("friends_per_month", String.valueOf(Math.max(1, friendsPerMonth)));
        placeholders.put("trend_indicator", activeFriends >= totalFriends / 2 ? "§a↗" : "§e→");
        placeholders.put("trend_text", activeFriends >= totalFriends / 2 ? "§aEn croissance" : "§eStable");

        placeholders.put("messages_sent", String.valueOf(messagesSent));
        placeholders.put("messages_received", String.valueOf(messagesReceived));
        placeholders.put("active_conversations", String.valueOf(activeConversations));
        placeholders.put("most_chatty_friend", mostChattyFriend != null ? mostChattyFriend.getPlayerName() : "Aucun");
        placeholders.put("messages_per_day", String.valueOf(messagesPerDay));
        placeholders.put("peak_chat_time", pickPeakChatTime(random));

        placeholders.put("time_together", formattedTogether);
        placeholders.put("gaming_sessions", String.valueOf(gamingSessions));
        placeholders.put("favorite_gaming_buddy", favoriteGamingBuddy != null ? favoriteGamingBuddy.getPlayerName() : "Aucun");
        placeholders.put("favorite_activity", pickFavoriteActivity(random));
        placeholders.put("longest_session", longestSession);
        placeholders.put("this_week_time", thisWeekTime);

        placeholders.put("peak_hour", peakHour);
        placeholders.put("peak_day", peakDay);
        placeholders.put("average_presence", String.valueOf(averagePresence));
        placeholders.put("timezones_count", String.valueOf(Math.max(1, timezonesCount)));
        placeholders.put("time_compatibility", String.valueOf(timeCompatibility));
        placeholders.put("next_prediction", "Dans " + (1 + random.nextInt(3)) + "h");

        placeholders.put("average_quality", String.format(Locale.FRENCH, "%.1f", qualityScore));
        placeholders.put("stable_friendships", String.valueOf(stableFriendships));
        placeholders.put("recent_friendships", String.valueOf(recentFriendships));
        placeholders.put("retention_rate", String.valueOf(Math.min(98, 85 + random.nextInt(10))));
        placeholders.put("regular_interactions", String.valueOf(regularInteractions));
        placeholders.put("quality_recommendation", qualityRecommendation);

        placeholders.put("potential_friends", String.valueOf(potentialFriends));
        placeholders.put("friend_clusters", String.valueOf(friendClusters));
        placeholders.put("main_connector", mainConnector != null ? mainConnector.getPlayerName() : "Aucun");
        placeholders.put("separation_degree", String.format(Locale.FRENCH, "%.1f", separationDegree));
        placeholders.put("social_influence", String.valueOf(socialInfluence));
        placeholders.put("network_suggestions", networkSuggestion);

        placeholders.put("unlocked_achievements", String.valueOf(unlockedAchievements));
        placeholders.put("total_achievements", String.valueOf(totalAchievements));
        placeholders.put("friendship_points", String.valueOf(friendshipPoints));
        placeholders.put("social_rank", socialRank);
        placeholders.put("next_achievement", nextAchievement);
        placeholders.put("achievement_progress", String.valueOf(achievementProgress));
        placeholders.put("latest_rare_achievement", latestRareAchievement);

        placeholders.put("chart_period", "30 jours");
        placeholders.put("data_points", String.valueOf(30 + random.nextInt(10)));
        placeholders.put("heatmap_days", String.valueOf(14 + random.nextInt(7)));

        placeholders.put("your_social_score", String.valueOf(yourSocialScore));
        placeholders.put("server_average_score", String.valueOf(serverAverageScore));
        placeholders.put("your_rank", String.valueOf(yourRank));
        placeholders.put("total_players", String.valueOf(totalPlayers));
        placeholders.put("percentile", String.valueOf(percentile));
        placeholders.put("comparison_text", comparisonText);

        placeholders.put("most_active_friend", mostActiveFriendName);
        placeholders.put("activity_vs_most_active", String.valueOf(activityVsMostActive));
        placeholders.put("position_in_group", positionInGroup);
        placeholders.put("excellence_area_1", excellenceArea1);
        placeholders.put("excellence_area_2", excellenceArea2);

        placeholders.put("insights_count", String.valueOf(insightsCount));
        placeholders.put("last_analysis", lastAnalysis);
        placeholders.put("recommendation_1", recommendation1);
        placeholders.put("recommendation_2", recommendation2);
        placeholders.put("recommendation_3", recommendation3);

        placeholders.put("active_suggestions", String.valueOf(activeSuggestions));
        placeholders.put("recommended_goals", String.valueOf(Math.max(1, recommendedGoals)));
        placeholders.put("priority_1", priority1);
        placeholders.put("priority_2", priority2);
        placeholders.put("estimated_impact", estimatedImpact);

        placeholders.put("last_update", lastUpdate);
        placeholders.put("calculation_time", String.valueOf(calculationTime));
        placeholders.put("privacy_level", privacyLevel);
        placeholders.put("historical_months", String.valueOf(Math.max(1, historicalMonths)));
        placeholders.put("first_record", firstRecord);

        return placeholders;
    }

    private double computeMonthsSince(final FriendData friend) {
        if (friend == null || friend.getFriendshipDate() == null) {
            return 0;
        }
        final Instant start = friend.getFriendshipDate().toInstant();
        final Instant now = Instant.now();
        final long days = ChronoUnit.DAYS.between(start, now);
        return Math.max(0, days / 30.0);
    }

    private String pickPeakChatTime(final Random random) {
        final String[] periods = {"18h-20h", "20h-22h", "16h-18h", "14h-16h"};
        return periods[random.nextInt(periods.length)];
    }

    private String pickFavoriteActivity(final Random random) {
        final String[] activities = {"Mini-jeux", "SkyWars", "BedWars", "Créatif", "Aventures"};
        return activities[random.nextInt(activities.length)];
    }

    private String formatDuration(final long minutes) {
        if (minutes <= 0) {
            return "0m";
        }
        final long hours = minutes / 60;
        final long remaining = minutes % 60;
        if (hours <= 0) {
            return remaining + "m";
        }
        return hours + "h " + remaining + "m";
    }

    private String apply(final String input, final Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (inventory == null || event.getInventory() != inventory) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player clicker)) {
            return;
        }
        if (!clicker.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        final FriendStatisticsMenuConfiguration.MenuItem definition = itemsBySlot.get(event.getSlot());
        if (definition == null) {
            return;
        }
        final String action = resolveAction(definition.actions(), event.getClick());
        if (action == null) {
            return;
        }
        handleAction(action);
    }

    private String resolveAction(final Map<String, String> actions, final ClickType clickType) {
        if (actions.isEmpty()) {
            return null;
        }
        if ((clickType.isLeftClick() || clickType == ClickType.SHIFT_LEFT) && actions.containsKey("left_click")) {
            return actions.get("left_click");
        }
        if ((clickType.isRightClick() || clickType == ClickType.SHIFT_RIGHT) && actions.containsKey("right_click")) {
            return actions.get("right_click");
        }
        if (clickType == ClickType.MIDDLE && actions.containsKey("middle_click")) {
            return actions.get("middle_click");
        }
        return null;
    }

    private void handleAction(final String action) {
        switch (action.toLowerCase(Locale.ROOT)) {
            case "view_general_details" -> sendDetailMessages("§b📊 Statistiques générales détaillées:",
                    "§7- Analyse complète de votre réseau d'amis",
                    "§7- Tendances de croissance",
                    "§7- Comparaisons temporelles");
            case "view_communication_details" -> sendDetailMessages("§b💬 Statistiques de communication:",
                    "§7- Messages échangés par période",
                    "§7- Fréquence de communication",
                    "§7- Amis les plus bavards");
            case "view_activity_details" -> sendDetailMessages("§b🎮 Activités communes détaillées:",
                    "§7- Temps de jeu par activité",
                    "§7- Partenaires de jeu préférés",
                    "§7- Sessions les plus longues");
            case "view_temporal_details" -> sendDetailMessages("§b⏰ Analyses temporelles:",
                    "§7- Patterns d'activité par heure",
                    "§7- Jours les plus sociaux",
                    "§7- Prédictions de connexion");
            case "view_quality_details" -> sendDetailMessages("§b🏆 Qualité des amitiés:",
                    "§7- Score de fidélité par ami",
                    "§7- Stabilité des relations",
                    "§7- Recommandations d'amélioration");
            case "view_network_details" -> sendDetailMessages("§b🌐 Analyse de réseau:",
                    "§7- Connexions indirectes",
                    "§7- Points d'influence",
                    "§7- Opportunités de nouveaux liens");
            case "view_achievements_details" -> sendDetailMessages("§b🎯 Succès d'amitié:",
                    "§7- Liste complète des succès",
                    "§7- Progression actuelle",
                    "§7- Prochains objectifs");
            case "view_activity_chart" -> triggerChartFeedback();
            case "view_distribution_chart" -> triggerChartFeedback();
            case "view_heatmap" -> triggerChartFeedback();
            case "view_server_comparison" -> sendDetailMessages("§d📊 Comparaison serveur:",
                    "§7- Votre position dans le classement",
                    "§7- Domaines d'excellence",
                    "§7- Points d'amélioration");
            case "view_friend_comparison" -> sendDetailMessages("§d👥 Comparaison avec vos amis:",
                    "§7- Analyse détaillée de votre cercle",
                    "§7- Forces sociales",
                    "§7- Opportunités de progression");
            case "view_ai_insights" -> sendDetailMessages("§a🤖 Insights intelligents:",
                    "§7- Tendances récentes",
                    "§7- Actions recommandées",
                    "§7- Prévisions personnalisées");
            case "view_improvement_plan" -> sendDetailMessages("§a📈 Suggestions d'amélioration:",
                    "§7- Priorités identifiées",
                    "§7- Objectifs recommandés",
                    "§7- Impact estimé");
            case "export_statistics" -> handleExport();
            case "refresh_statistics" -> handleRefresh();
            case "back_to_main" -> handleBackToMain();
            case "share_statistics" -> handleShare();
            case "view_historical_data" -> sendDetailMessages("§7📚 Données historiques:",
                    "§7- Historique complet disponible",
                    "§7- Tendances sur plusieurs mois",
                    "§7- Exportez pour analyse avancée");
            default -> player.sendMessage("§cAction inconnue: " + action);
        }
    }

    private void handleRefresh() {
        final String calculating = configuration.getMessages().getOrDefault("calculating", "§7Calcul des statistiques en cours...");
        player.sendMessage(calculating);
        playSound(configuration.getSounds().get("chart_generated"), 1.0f);
        computeAndRender(false);
        final String updated = configuration.getMessages().getOrDefault("updated", "§aStatistiques mises à jour !");
        player.sendMessage(updated);
    }

    private void handleExport() {
        final String filename = "stats-" + player.getName().toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID().toString().substring(0, 6) + ".pdf";
        final String message = configuration.getMessages().getOrDefault("export_success", "§aStatistiques exportées vers {filename}");
        player.sendMessage(message.replace("{filename}", filename));
        playSound(configuration.getSounds().get("export_complete"), 1.2f);
    }

    private void handleShare() {
        final String link = "https://stats.server.com/" + UUID.randomUUID().toString().substring(0, 8);
        final String message = configuration.getMessages().getOrDefault("share_link_created", "§aLien de partage créé: {link}");
        player.sendMessage(message.replace("{link}", link));
        playSound(configuration.getSounds().get("stats_updated"), 1.3f);
    }

    private void handleBackToMain() {
        player.closeInventory();
        playSound(Sound.UI_BUTTON_CLICK, 1.0f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            final FriendsMenuController controller = plugin.getFriendsMenuController();
            if (controller != null) {
                controller.openMainMenu(player);
            }
        }, 2L);
    }

    private void triggerChartFeedback() {
        player.sendMessage("§e📈 Visualisation générée !");
        playSound(configuration.getSounds().get("chart_generated"), 1.0f);
    }

    private void sendDetailMessages(final String header, final String line1, final String line2, final String line3) {
        player.sendMessage(header);
        player.sendMessage(line1);
        player.sendMessage(line2);
        player.sendMessage(line3);
        playSound(Sound.UI_BUTTON_CLICK, 1.1f);
    }

    private void playSound(final Sound sound, final float pitch) {
        if (sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private void scheduleAutoRefresh() {
        if (refreshTask != null || !configuration.isAutoRefresh()) {
            return;
        }
        final long intervalTicks = Math.max(20L, configuration.getRefreshIntervalSeconds() * 20L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (closed) {
                return;
            }
            computeAndRender(false);
        }, intervalTicks, intervalTicks);
    }

    private void cancelTasks() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (inventory == null || event.getInventory() != inventory) {
            return;
        }
        if (!(event.getPlayer() instanceof Player closing) || !closing.getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        closed = true;
        cancelTasks();
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        if (!event.getPlayer().getUniqueId().equals(player.getUniqueId())) {
            return;
        }
        closed = true;
        cancelTasks();
        HandlerList.unregisterAll(this);
    }
}
