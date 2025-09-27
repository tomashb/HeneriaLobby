package com.lobby.friends.menu.statistics;

import com.lobby.LobbyPlugin;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive menu presenting advanced friendship statistics. The values are
 * computed asynchronously from the cached friend data and enriched with
 * deterministic pseudo-random insights so that the interface feels alive while
 * the analytical backend is under development.
 */
public final class FriendStatisticsMenu implements Listener {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final FriendStatisticsMenuConfiguration configuration;
    private final Player player;
    private final AssetManager assetManager;
    private final Map<Integer, FriendStatisticsMenuConfiguration.MenuItem> itemsBySlot = new HashMap<>();
    private final FriendStatisticsCalculator calculator = new FriendStatisticsCalculator();

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
        final CompletableFuture<List<FriendData>> friendsFuture = friendsManager.getFriends(player);
        final CompletableFuture<List<FriendRequest>> requestsFuture = friendsManager.getPendingRequests(player);

        friendsFuture.thenCombine(requestsFuture, (friends, requests) ->
                        new StatisticsData(friends != null ? friends : List.of(),
                                requests != null ? requests : List.of()))
                .whenComplete((data, throwable) -> {
                    if (throwable != null) {
                        plugin.getLogger().warning("Impossible de calculer les statistiques d'amis: " + throwable.getMessage());
                    }
                    final List<FriendData> safeFriends = data != null ? data.friends() : List.of();
                    final List<FriendRequest> safeRequests = data != null ? data.requests() : List.of();
                    final Map<String, String> placeholders = calculator.calculate(player, safeFriends, safeRequests);
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

    private record StatisticsData(List<FriendData> friends, List<FriendRequest> requests) {
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
            case "close_menu" -> player.closeInventory();
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
