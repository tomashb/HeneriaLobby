package com.lobby.scoreboard;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.servers.ServerPlaceholderCache;
import com.lobby.velocity.VelocityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the configurable lobby scoreboard. The layout, refresh intervals and
 * animations are fully driven by the {@code scoreboard.yml} configuration
 * stored in the plugin data folder.
 */
public final class ScoreboardManager implements Listener {

    private static final String CONFIG_FILE = "scoreboard.yml";
    private static final String CONFIG_ROOT = "scoreboard";
    private static final String DEFAULT_PREFIX = ChatColor.GRAY + "Joueur";
    private static final String SCOREBOARD_OBJECTIVE_PREFIX = "lobby";
    private static final int MAX_SCOREBOARD_LINES = 15;
    private static final String[] LINE_ENTRIES = createLineEntries();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();

    private static final List<String> DEFAULT_BODY = List.of(
            "&r",
            "&f→ &d&lProfil",
            "  &7Compte: &f%player_name%",
            "  &7Grade: %luckperms_prefix%",
            "  &7Coins: &e%player_coins% ⛁",
            "  &7Tokens: &b%player_tokens% ✪",
            "&r",
            "&f→ &6&lServeur",
            "  &7Lobby: &f#%server_id%",
            "  &7En ligne: &a%bungee_total%",
            "&r"
    );

    private static final List<String> DEFAULT_FOOTER_FRAMES = List.of(
            "    §7→ §ewww.§6h§ce§6n§ce§6r§ci§6a§e.com",
            "    §7→ §ewww.§ch§6e§cn§6e§cr§6i§ca§e.com",
            "    §7→ §ewww.§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.h§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.he§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.hen§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.hene§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.hener§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.heneri§eh§ce§6n§ce§6r§ci§6a.com",
            "    §7→ §ewww.heneria§e.§ch§6e§cn§6e§cr§6i.com",
            "    §7→ §ewww.heneria.§ec§ch§6e§cn§6e§cr.com",
            "    §7→ §ewww.heneria.co§em§ec§ch§6e§cn§6e.com",
            "    §7→ §ewww.heneria.com§em§ec§ch§6e§cn.com"
    );

    private final LobbyPlugin plugin;
    private final EconomyManager economyManager;
    private final ServerPlaceholderCache serverPlaceholderCache;
    private final LuckPermsPrefixResolver prefixResolver;
    private final ScoreboardAnimation footerAnimation = new ScoreboardAnimation(List.of());
    private final AtomicReference<ScoreboardSettings> settings = new AtomicReference<>();
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerScoreboardData> dataCache = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerScoreboardView> views = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();

    private BukkitTask dataTask;
    private BukkitTask refreshTask;
    private BukkitTask animationTask;
    private String serverId = "1";
    private volatile int footerLineIndex = -1;

    public ScoreboardManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.economyManager = plugin.getEconomyManager();
        this.serverPlaceholderCache = plugin.getServerPlaceholderCache();
        this.prefixResolver = new LuckPermsPrefixResolver(plugin, DEFAULT_PREFIX);
        reload();
    }

    public void reload() {
        final ScoreboardSettings loaded = loadSettings();
        settings.set(loaded);
        footerAnimation.setFrames(colorizeList(loaded.footerFrames()));
        updateServerId();
        restartTasks();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!loaded.enabled()) {
                clearTrackedPlayers();
                return;
            }
            Bukkit.getOnlinePlayers().forEach(this::initializePlayer);
        });
    }

    public void shutdown() {
        cancelTasks();
        clearTrackedPlayers();
        prefixResolver.clear();
    }

    @EventHandler
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            initializePlayer(player);
            forceUpdateForPlayer(player);
        }, 2L);
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        handleQuit(event.getPlayer());
    }

    private void initializePlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled()) {
            resetPlayerScoreboard(player);
            return;
        }
        final UUID uuid = player.getUniqueId();
        trackedPlayers.add(uuid);
        lastKnownNames.put(uuid, player.getName());
        dataCache.putIfAbsent(uuid, PlayerScoreboardData.empty());
        prefixResolver.forceRefresh(uuid, player.getName());
        final PlayerScoreboardView view = views.computeIfAbsent(uuid, id -> createView(player, current.titleComponent()));
        view.updateTitle(current.titleComponent());
        final PlayerScoreboardData data = dataCache.getOrDefault(uuid, PlayerScoreboardData.empty());
        view.apply(buildLines(player, data, resolveNetworkPlayerCount(), footerAnimation.getCurrentFrame(), current));
    }

    public void forceUpdateForPlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (!trackedPlayers.contains(uuid)) {
            return;
        }
        final String username = player.getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                final long coins = economyManager != null ? economyManager.getCoins(uuid) : 0L;
                final long tokens = economyManager != null ? economyManager.getTokens(uuid) : 0L;
                final String prefix = prefixResolver.fetchPrefixAsync(uuid, username).join();
                final PlayerScoreboardData data = new PlayerScoreboardData(prefix, coins, tokens);
                dataCache.put(uuid, data);
                Bukkit.getScheduler().runTask(plugin, () -> applyForcedUpdate(player, data));
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to force refresh scoreboard data for " + username + ": "
                        + exception.getMessage());
            }
        });
    }

    private void applyForcedUpdate(final Player player, final PlayerScoreboardData data) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        if (!trackedPlayers.contains(uuid)) {
            return;
        }
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled()) {
            return;
        }
        lastKnownNames.put(uuid, player.getName());
        final PlayerScoreboardView view = views.computeIfAbsent(uuid,
                id -> createView(player, current.titleComponent()));
        view.updateTitle(current.titleComponent());
        final int networkPlayers = resolveNetworkPlayerCount();
        final String footerFrame = footerAnimation.getCurrentFrame();
        view.apply(buildLines(player, data, networkPlayers, footerFrame, current));
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
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled() || trackedPlayers.isEmpty()) {
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
                plugin.getLogger().warning("Failed to refresh scoreboard data for " + username + ": "
                        + exception.getMessage());
            }
        }
    }

    private void updateScoreboards() {
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled() || trackedPlayers.isEmpty()) {
            return;
        }
        final int networkPlayers = resolveNetworkPlayerCount();
        final String footerFrame = footerAnimation.getCurrentFrame();
        for (final UUID uuid : trackedPlayers) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }
            lastKnownNames.put(uuid, player.getName());
            final PlayerScoreboardView view = views.computeIfAbsent(uuid,
                    id -> createView(player, current.titleComponent()));
            view.updateTitle(current.titleComponent());
            final PlayerScoreboardData data = dataCache.getOrDefault(uuid, PlayerScoreboardData.empty());
            view.apply(buildLines(player, data, networkPlayers, footerFrame, current));
        }
    }

    private void advanceAnimation() {
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled() || current.footerFrames().isEmpty() || trackedPlayers.isEmpty()) {
            return;
        }
        final String nextFrame = footerAnimation.nextFrame();
        final int networkPlayers = resolveNetworkPlayerCount();
        final int index = footerLineIndex;
        if (index < 0) {
            return;
        }
        for (final UUID uuid : trackedPlayers) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                cleanup(uuid);
                continue;
            }
            final PlayerScoreboardView view = views.get(uuid);
            if (view == null) {
                continue;
            }
            final PlayerScoreboardData data = dataCache.getOrDefault(uuid, PlayerScoreboardData.empty());
            final Map<String, String> placeholders = buildPlaceholderMap(player, data, networkPlayers);
            final String renderedFrame = colorize(applyPlaceholders(nextFrame, placeholders));
            view.updateLine(index, renderedFrame.isBlank() ? " " : renderedFrame);
        }
    }

    private void restartTasks() {
        cancelTasks();
        final ScoreboardSettings current = settings.get();
        if (current == null || !current.enabled()) {
            return;
        }
        dataTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshPlayerData, 20L, 100L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateScoreboards, 20L,
                Math.max(1L, current.updateIntervalTicks()));
        if (current.footerFrames().size() > 1) {
            animationTask = Bukkit.getScheduler().runTaskTimer(plugin, this::advanceAnimation, current.animationInterval(),
                    current.animationInterval());
        }
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

    private void cleanup(final UUID uuid) {
        trackedPlayers.remove(uuid);
        dataCache.remove(uuid);
        lastKnownNames.remove(uuid);
        prefixResolver.invalidate(uuid);
        views.remove(uuid);
    }

    private void clearTrackedPlayers() {
        for (final UUID uuid : new HashSet<>(trackedPlayers)) {
            final PlayerScoreboardView view = views.remove(uuid);
            final Player player = Bukkit.getPlayer(uuid);
            if (view != null && player != null) {
                view.clear(player);
            }
        }
        trackedPlayers.clear();
        dataCache.clear();
        lastKnownNames.clear();
        footerLineIndex = -1;
    }

    private List<String> buildLines(final Player player,
                                   final PlayerScoreboardData data,
                                   final int networkPlayers,
                                   final String footerFrame,
                                   final ScoreboardSettings current) {
        final Map<String, String> placeholders = buildPlaceholderMap(player, data, networkPlayers);
        final List<String> lines = new ArrayList<>(current.bodyLines().size() + 1);
        for (String rawLine : current.bodyLines()) {
            final String resolved = colorize(applyPlaceholders(rawLine, placeholders));
            lines.add(resolved.isBlank() ? " " : resolved);
        }
        final String renderedFooter = colorize(applyPlaceholders(footerFrame, placeholders));
        lines.add(renderedFooter.isBlank() ? " " : renderedFooter);
        ensureScoreboardLimits(lines);
        footerLineIndex = lines.size() - 1;
        return lines;
    }

    private Map<String, String> buildPlaceholderMap(final Player player,
                                                    final PlayerScoreboardData data,
                                                    final int networkPlayers) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", player.getName());
        placeholders.put("%player_displayname%", player.getDisplayName());
        final String prefix = Optional.ofNullable(data.prefix()).filter(s -> !s.isBlank()).orElse(DEFAULT_PREFIX);
        placeholders.put("%luckperms_prefix%", prefix);
        placeholders.put("%player_coins%", formatNumber(data.coins()));
        placeholders.put("%player_tokens%", formatNumber(data.tokens()));
        placeholders.put("%server_id%", serverId);
        placeholders.put("%bungee_total%", formatNumber(networkPlayers));
        return placeholders;
    }

    private void ensureScoreboardLimits(final List<String> lines) {
        if (lines.size() > MAX_SCOREBOARD_LINES) {
            lines.subList(MAX_SCOREBOARD_LINES, lines.size()).clear();
        }
    }

    private PlayerScoreboardView createView(final Player player, final Component title) {
        final org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            throw new IllegalStateException("Scoreboard manager is not available");
        }
        final Scoreboard scoreboard = manager.getNewScoreboard();
        final String objectiveName = SCOREBOARD_OBJECTIVE_PREFIX + "_" + player.getEntityId();
        final Objective objective = scoreboard.registerNewObjective(objectiveName, Criteria.DUMMY, title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(scoreboard);
        return new PlayerScoreboardView(scoreboard, objective, title);
    }

    private void resetPlayerScoreboard(final Player player) {
        final org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager != null) {
            player.setScoreboard(manager.getNewScoreboard());
        }
    }

    private ScoreboardSettings loadSettings() {
        final File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            try {
                plugin.saveResource(CONFIG_FILE, false);
            } catch (final IllegalArgumentException ignored) {
                // Continue with defaults if resource is not packaged.
            }
        }

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            if (file.exists()) {
                configuration.load(file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Failed to load scoreboard configuration: " + exception.getMessage());
        }

        final ConfigurationSection section = configuration.getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            return ScoreboardSettings.defaultSettings();
        }

        final boolean enabled = section.getBoolean("enabled", true);
        final long updateInterval = Math.max(1L, section.getLong("update-interval-ticks", 20L));
        final long animationInterval = Math.max(1L, section.getLong("animation-interval-ticks", 4L));
        final String title = colorize(section.getString("title", "&6&l· &f&lHENERIA &6&l·"));
        List<String> body = section.getStringList("body");
        if (body == null || body.isEmpty()) {
            body = DEFAULT_BODY;
        }
        List<String> footer = section.getStringList("footer-animation-frames");
        if (footer == null || footer.isEmpty()) {
            footer = DEFAULT_FOOTER_FRAMES;
        }
        return new ScoreboardSettings(enabled, updateInterval, animationInterval, title, body, footer);
    }

    private void updateServerId() {
        final String configured = plugin.getConfig().getString("lobby.server_id", "1");
        serverId = (configured == null || configured.isBlank()) ? "1" : configured.trim();
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

    private static List<String> colorizeList(final List<String> raw) {
        final List<String> colored = new ArrayList<>(raw.size());
        for (String line : raw) {
            colored.add(colorize(line));
        }
        return colored;
    }

    private static String[] createLineEntries() {
        final String[] entries = new String[MAX_SCOREBOARD_LINES];
        for (int index = 0; index < MAX_SCOREBOARD_LINES; index++) {
            final String color = Integer.toHexString(index);
            entries[index] = "" + ChatColor.COLOR_CHAR + color + ChatColor.COLOR_CHAR + 'r';
        }
        return entries;
    }

    private static String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private static String colorize(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private String formatNumber(final long value) {
        return NumberFormat.getInstance(Locale.FRANCE).format(value);
    }

    private static final class ScoreboardSettings {

        private final boolean enabled;
        private final long updateIntervalTicks;
        private final long animationIntervalTicks;
        private final String title;
        private final Component titleComponent;
        private final List<String> bodyLines;
        private final List<String> footerFrames;

        private ScoreboardSettings(final boolean enabled,
                                   final long updateIntervalTicks,
                                   final long animationIntervalTicks,
                                   final String title,
                                   final List<String> bodyLines,
                                   final List<String> footerFrames) {
            this.enabled = enabled;
            this.updateIntervalTicks = updateIntervalTicks;
            this.animationIntervalTicks = animationIntervalTicks;
            this.title = title;
            this.titleComponent = LEGACY_SERIALIZER.deserialize(title);
            this.bodyLines = List.copyOf(bodyLines);
            this.footerFrames = List.copyOf(footerFrames);
        }

        private static ScoreboardSettings defaultSettings() {
            final String title = colorize("&6&l· &f&lHENERIA &6&l·");
            return new ScoreboardSettings(true, 20L, 4L, title, DEFAULT_BODY, DEFAULT_FOOTER_FRAMES);
        }

        private boolean enabled() {
            return enabled;
        }

        private long updateIntervalTicks() {
            return updateIntervalTicks;
        }

        private long animationInterval() {
            return animationIntervalTicks;
        }

        private Component titleComponent() {
            return titleComponent;
        }

        private List<String> bodyLines() {
            return bodyLines;
        }

        private List<String> footerFrames() {
            return footerFrames;
        }
    }

    private static final class PlayerScoreboardView {

        private final Scoreboard scoreboard;
        private final Objective objective;
        private final List<ScoreboardLine> lines = new ArrayList<>();
        private Component title;

        private PlayerScoreboardView(final Scoreboard scoreboard, final Objective objective, final Component title) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.title = title;
        }

        private void apply(final List<String> newLines) {
            final int targetSize = Math.min(newLines.size(), MAX_SCOREBOARD_LINES);
            for (int index = lines.size() - 1; index >= targetSize; index--) {
                final ScoreboardLine removed = lines.remove(index);
                removed.clear(scoreboard);
            }
            for (int index = 0; index < targetSize; index++) {
                final String value = sanitize(newLines.get(index));
                final ScoreboardLine line;
                if (index < lines.size()) {
                    line = lines.get(index);
                } else {
                    line = createLine(index);
                    lines.add(line);
                }
                line.update(value);
                objective.getScore(line.entry()).setScore(targetSize - index);
            }
        }

        private void updateLine(final int index, final String value) {
            if (index < 0 || index >= lines.size()) {
                return;
            }
            final ScoreboardLine line = lines.get(index);
            line.update(sanitize(value));
            objective.getScore(line.entry()).setScore(lines.size() - index);
        }

        private void updateTitle(final Component newTitle) {
            if (newTitle == null || newTitle.equals(title)) {
                return;
            }
            objective.displayName(newTitle);
            title = newTitle;
        }

        private void clear(final Player player) {
            for (final ScoreboardLine line : lines) {
                line.clear(scoreboard);
            }
            lines.clear();
            final org.bukkit.scoreboard.ScoreboardManager manager = Bukkit.getScoreboardManager();
            if (manager != null) {
                player.setScoreboard(manager.getNewScoreboard());
            }
        }

        private ScoreboardLine createLine(final int index) {
            final String teamName = SCOREBOARD_OBJECTIVE_PREFIX + "_line_" + index;
            Team team = scoreboard.getTeam(teamName);
            if (team == null) {
                team = scoreboard.registerNewTeam(teamName);
            }
            team.setSuffix("");
            final String entry = LINE_ENTRIES[index];
            if (!team.getEntries().contains(entry)) {
                team.addEntry(entry);
            }
            return new ScoreboardLine(team, entry);
        }

        private String sanitize(final String value) {
            return (value == null || value.isBlank()) ? " " : value;
        }
    }

    private static final class ScoreboardLine {

        private final Team team;
        private final String entry;
        private String text = "";

        private ScoreboardLine(final Team team, final String entry) {
            this.team = team;
            this.entry = entry;
        }

        private void update(final String value) {
            if (!team.getEntries().contains(entry)) {
                team.addEntry(entry);
            }
            if (!value.equals(text)) {
                team.setPrefix(value);
                team.setSuffix("");
                text = value;
            }
        }

        private void clear(final Scoreboard scoreboard) {
            for (String existing : new HashSet<>(team.getEntries())) {
                team.removeEntry(existing);
            }
            scoreboard.resetScores(entry);
            team.unregister();
            text = "";
        }

        private String entry() {
            return entry;
        }
    }
}
