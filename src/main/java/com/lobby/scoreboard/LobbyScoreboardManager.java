package com.lobby.scoreboard;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.servers.ServerPlaceholderCache;
import com.lobby.velocity.VelocityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LobbyScoreboardManager implements Listener {

    private static final String SCOREBOARD_TITLE = "      §6§l· §f§lHENERIA §6§l·";
    private static final String SCOREBOARD_OBJECTIVE = "lobby";
    private static final String PROFILE_HEADER = "§f→ §d§lProfil";
    private static final String SERVER_HEADER = "§f→ §6§lServeur";
    private static final String SEPARATOR_PRIMARY = "§r§0";
    private static final String SEPARATOR_SECONDARY = "§r§1";
    private static final String DEFAULT_PREFIX = "§7Joueur";
    private static final int FOOTER_LINE_INDEX = 10;
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private final LobbyPlugin plugin;
    private final EconomyManager economyManager;
    private final ServerPlaceholderCache serverPlaceholderCache;
    private final LuckPermsPrefixResolver prefixResolver;
    private final UrlAnimation urlAnimation = new UrlAnimation();
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerScoreboardData> dataCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerScoreboardView> views = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();

    private BukkitTask dataTask;
    private BukkitTask refreshTask;
    private BukkitTask animationTask;
    private String serverId = "1";

    public LobbyScoreboardManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.serverPlaceholderCache = plugin.getServerPlaceholderCache();
        this.prefixResolver = new LuckPermsPrefixResolver(plugin, DEFAULT_PREFIX);
        reload();
        start();
    }

    public void reload() {
        final String configuredServerId = plugin.getConfig().getString("lobby.server_id", "1");
        serverId = (configuredServerId == null || configuredServerId.isBlank()) ? "1" : configuredServerId.trim();
        Bukkit.getScheduler().runTask(plugin, this::updateScoreboards);
    }

    public void shutdown() {
        cancelTasks();
        prefixResolver.clear();
        trackedPlayers.clear();
        dataCache.clear();
        lastKnownNames.clear();
        views.forEach((uuid, view) -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                view.clear(player);
            }
        });
        views.clear();
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> initializePlayer(player), 2L);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    private void start() {
        cancelTasks();
        dataTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshPlayerData, 20L, 100L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboards, 20L, 20L);
        animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::advanceAnimation, 3L, 3L);
        Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::initializePlayer));
    }

    private void cancelTasks() {
        if (dataTask != null) {
            dataTask.cancel();
            dataTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (animationTask != null) {
            animationTask.cancel();
            animationTask = null;
        }
    }

    private void initializePlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        trackedPlayers.add(uuid);
        lastKnownNames.put(uuid, player.getName());
        dataCache.putIfAbsent(uuid, PlayerScoreboardData.empty());
        prefixResolver.forceRefresh(uuid, player.getName());
        final PlayerScoreboardView view = views.computeIfAbsent(uuid, id -> createView(player));
        final PlayerScoreboardData data = dataCache.getOrDefault(uuid, PlayerScoreboardData.empty());
        view.apply(buildLines(player, data, resolveNetworkPlayerCount(), urlAnimation.getCurrentFrame()));
    }

    private void handleQuit(final Player player) {
        if (player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        trackedPlayers.remove(uuid);
        dataCache.remove(uuid);
        lastKnownNames.remove(uuid);
        prefixResolver.invalidate(uuid);
        final PlayerScoreboardView view = views.remove(uuid);
        if (view != null) {
            view.clear(player);
        }
    }

    private void refreshPlayerData() {
        if (trackedPlayers.isEmpty()) {
            return;
        }
        for (final UUID uuid : trackedPlayers) {
            final String username = lastKnownNames.getOrDefault(uuid, uuid.toString());
            try {
                final long coins = economyManager != null ? economyManager.getCoins(uuid) : 0L;
                final long tokens = economyManager != null ? economyManager.getTokens(uuid) : 0L;
                final String prefix = prefixResolver.getPrefix(uuid, username);
                dataCache.put(uuid, new PlayerScoreboardData(prefix, coins, tokens));
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to refresh scoreboard data for " + username + ": " + exception.getMessage());
            }
        }
    }

    private void updateScoreboards() {
        if (trackedPlayers.isEmpty()) {
            return;
        }
        final String footerFrame = urlAnimation.getCurrentFrame();
        final int networkPlayers = resolveNetworkPlayerCount();
        for (final UUID uuid : trackedPlayers) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }
            lastKnownNames.put(uuid, player.getName());
            final PlayerScoreboardView view = views.computeIfAbsent(uuid, id -> createView(player));
            final PlayerScoreboardData data = dataCache.getOrDefault(uuid, PlayerScoreboardData.empty());
            view.apply(buildLines(player, data, networkPlayers, footerFrame));
        }
    }

    private void advanceAnimation() {
        if (trackedPlayers.isEmpty()) {
            return;
        }
        final String nextFrame = urlAnimation.nextFrame();
        for (final UUID uuid : trackedPlayers) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }
            final PlayerScoreboardView view = views.get(uuid);
            if (view != null) {
                view.updateLine(FOOTER_LINE_INDEX, nextFrame);
            }
        }
    }

    private void cleanup(final UUID uuid) {
        trackedPlayers.remove(uuid);
        dataCache.remove(uuid);
        lastKnownNames.remove(uuid);
        prefixResolver.invalidate(uuid);
        views.remove(uuid);
    }

    private List<String> buildLines(final Player player, final PlayerScoreboardData data, final int networkPlayers,
                                    final String footerFrame) {
        final List<String> lines = new ArrayList<>(11);
        lines.add(PROFILE_HEADER);
        lines.add("  §7Compte: §f" + player.getName());
        final String prefix = data.prefix().isBlank() ? DEFAULT_PREFIX : data.prefix();
        lines.add("  §7Grade: " + prefix);
        lines.add("  §7Coins: §e" + formatNumber(data.coins()) + " ⛁");
        lines.add("  §7Tokens: §b" + formatNumber(data.tokens()) + " ✪");
        lines.add(SEPARATOR_PRIMARY);
        lines.add(SERVER_HEADER);
        lines.add("  §7Lobby: §f#" + serverId);
        lines.add("  §7En ligne: §a" + formatNumber(networkPlayers));
        lines.add(SEPARATOR_SECONDARY);
        lines.add(footerFrame);
        return lines;
    }

    private String formatNumber(final long value) {
        return NumberFormat.getInstance(Locale.FRANCE).format(value);
    }

    private int resolveNetworkPlayerCount() {
        int total = 0;
        if (serverPlaceholderCache != null) {
            total = Math.max(total, serverPlaceholderCache.getTotalPlayerCount());
        }
        final VelocityManager velocityManager = plugin.getVelocityManager();
        if (velocityManager != null) {
            total = Math.max(total, velocityManager.getTotalPlayerCount());
        }
        return Math.max(total, Bukkit.getOnlinePlayers().size());
    }

    private PlayerScoreboardView createView(final Player player) {
        final org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        final Scoreboard scoreboard = manager.getNewScoreboard();
        final String objectiveName = SCOREBOARD_OBJECTIVE + "_" + player.getEntityId();
        final Component displayName = LEGACY_SERIALIZER.deserialize(SCOREBOARD_TITLE);
        final Objective objective = scoreboard.registerNewObjective(objectiveName, Criteria.DUMMY, displayName);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
        return new PlayerScoreboardView(scoreboard, objective);
    }

    private static final class PlayerScoreboardView {

        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<String> lines = new ArrayList<>();

        private PlayerScoreboardView(final Scoreboard scoreboard, final Objective objective) {
            this.scoreboard = scoreboard;
            this.objective = objective;
        }

        private void apply(final List<String> newLines) {
            final Set<String> currentEntries = Set.copyOf(lines);
            for (final String entry : currentEntries) {
                if (!newLines.contains(entry)) {
                    scoreboard.resetScores(entry);
                }
            }
            lines.clear();
            lines.addAll(newLines);
            final int size = newLines.size();
            for (int index = 0; index < size; index++) {
                final String line = newLines.get(index);
                objective.getScore(line).setScore(size - index);
            }
        }

        private void updateLine(final int index, final String value) {
            if (index < 0 || index >= lines.size()) {
                return;
            }
            final String current = lines.get(index);
            if (current.equals(value)) {
                return;
            }
            scoreboard.resetScores(current);
            final int score = lines.size() - index;
            objective.getScore(value).setScore(score);
            lines.set(index, value);
        }

        private void clear(final Player player) {
            for (final String entry : lines) {
                scoreboard.resetScores(entry);
            }
            lines.clear();
            final org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getNewScoreboard());
            }
        }
    }
}
