package com.lobby.tablist;

import com.lobby.LobbyPlugin;
import com.lobby.scoreboard.ScoreboardAnimation;
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
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
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
 * Centralizes the management of the lobby tablist. The layout and behaviour are
 * fully driven by {@code tablist.yml} placed in the plugin data folder.
 */
public final class TablistManager implements Listener {

    private static final String CONFIG_FILE = "tablist.yml";
    private static final String CONFIG_ROOT = "tablist";
    private static final String TEAM_PREFIX = "tablist_";
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getInstance(Locale.FRANCE);

    private final LobbyPlugin plugin;
    private final ServerPlaceholderCache serverPlaceholderCache;
    private final VelocityManager velocityManager;
    private final LuckPermsTablistResolver luckPermsResolver;
    private final ScoreboardAnimation footerAnimation = new ScoreboardAnimation(List.of(""));
    private final AtomicReference<TablistSettings> settings = new AtomicReference<>();
    private final Set<UUID> trackedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> lastKnownNames = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerTablistData> dataCache = new ConcurrentHashMap<>();

    private BukkitTask dataTask;
    private BukkitTask refreshTask;
    private String serverId = "1";
    private boolean firstFrame = true;

    public TablistManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.serverPlaceholderCache = plugin.getServerPlaceholderCache();
        this.velocityManager = plugin.getVelocityManager();
        this.luckPermsResolver = new LuckPermsTablistResolver(plugin, ChatColor.GRAY + "Joueur");
        reload();
    }

    public void reload() {
        final TablistSettings loaded = loadSettings();
        settings.set(loaded);
        footerAnimation.setFrames(loaded.footerFrames());
        firstFrame = true;
        updateServerId();
        restartTasks();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!loaded.enabled()) {
                clearTrackedPlayers();
                return;
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                initializePlayer(player);
            }
            updateTablist();
        });
    }

    public void shutdown() {
        cancelTasks();
        clearTrackedPlayers();
        luckPermsResolver.clear();
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

    private void initializePlayer(final Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        final TablistSettings current = settings.get();
        if (current == null || !current.enabled()) {
            resetPlayer(player);
            return;
        }
        final UUID uuid = player.getUniqueId();
        trackedPlayers.add(uuid);
        lastKnownNames.put(uuid, player.getName());
        luckPermsResolver.forceRefresh(uuid, player.getName());
        dataCache.put(uuid, luckPermsResolver.getMeta(uuid, player.getName()));
        Bukkit.getScheduler().runTask(plugin, this::updateTablist);
    }

    private void handleQuit(final Player player) {
        if (player == null) {
            return;
        }
        final UUID uuid = player.getUniqueId();
        trackedPlayers.remove(uuid);
        lastKnownNames.remove(uuid);
        dataCache.remove(uuid);
        luckPermsResolver.invalidate(uuid);
        resetPlayer(player);
    }

    private void refreshPlayerData() {
        final TablistSettings current = settings.get();
        if (current == null || !current.enabled() || trackedPlayers.isEmpty()) {
            return;
        }
        for (UUID uuid : trackedPlayers) {
            final String username = lastKnownNames.getOrDefault(uuid, uuid.toString());
            try {
                final PlayerTablistData data = luckPermsResolver.getMeta(uuid, username);
                dataCache.put(uuid, data);
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to refresh tablist data for " + username + ": "
                        + exception.getMessage());
            }
        }
    }

    private void updateTablist() {
        final TablistSettings current = settings.get();
        if (current == null || !current.enabled() || trackedPlayers.isEmpty()) {
            return;
        }
        final Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        if (onlinePlayers.isEmpty()) {
            return;
        }
        final int networkPlayers = resolveNetworkPlayerCount();
        final String footerFrame = selectFooterFrame();
        final boolean sortByWeight = current.sortByWeight();
        final List<PlayerEntry> sortedEntries = buildSortedEntries(onlinePlayers, sortByWeight);
        applySortingForViewers(sortedEntries, sortByWeight);

        for (PlayerEntry entry : sortedEntries) {
            final Player player = entry.player();
            if (player == null || !player.isOnline()) {
                continue;
            }
            lastKnownNames.put(player.getUniqueId(), player.getName());
            final Map<String, String> placeholders = buildPlaceholders(player, entry.data(), networkPlayers);
            final Component header = renderHeader(current.headerLines(), placeholders);
            final Component footer = renderFooter(footerFrame, placeholders);
            player.sendPlayerListHeaderAndFooter(header, footer);
            applyPlayerListName(player, current.playerNameFormat(), placeholders);
        }
    }

    private List<PlayerEntry> buildSortedEntries(final Collection<? extends Player> players, final boolean sortByWeight) {
        final List<PlayerEntry> entries = new ArrayList<>(players.size());
        for (Player player : players) {
            final PlayerTablistData data = dataCache.getOrDefault(player.getUniqueId(), PlayerTablistData.empty());
            entries.add(new PlayerEntry(player, data));
        }
        if (sortByWeight) {
            entries.sort(Comparator
                    .comparingInt((PlayerEntry entry) -> entry.data().weight()).reversed()
                    .thenComparing(entry -> entry.player().getName(), String.CASE_INSENSITIVE_ORDER));
        } else {
            entries.sort(Comparator.comparing(entry -> entry.player().getName(), String.CASE_INSENSITIVE_ORDER));
        }
        return entries;
    }

    private void applySortingForViewers(final List<PlayerEntry> sortedEntries, final boolean sortByWeight) {
        final Collection<? extends Player> viewers = Bukkit.getOnlinePlayers();
        if (viewers.isEmpty()) {
            return;
        }
        for (Player viewer : viewers) {
            final Scoreboard scoreboard = viewer.getScoreboard();
            if (scoreboard == null) {
                continue;
            }
            cleanupTablistTeams(scoreboard);
            if (!sortByWeight) {
                continue;
            }
            final Map<String, Team> teams = new HashMap<>();
            for (PlayerEntry entry : sortedEntries) {
                final Player target = entry.player();
                if (target == null) {
                    continue;
                }
                final int priority = Math.max(0, 9999 - Math.max(0, entry.data().weight()));
                final String teamName = TEAM_PREFIX + String.format("%04d", Math.min(priority, 9999));
                final Team team = teams.computeIfAbsent(teamName, name -> {
                    Team created = scoreboard.getTeam(name);
                    if (created == null) {
                        created = scoreboard.registerNewTeam(name);
                    }
                    created.setPrefix("");
                    created.setSuffix("");
                    if (!created.getEntries().isEmpty()) {
                        for (String entryName : new HashSet<>(created.getEntries())) {
                            created.removeEntry(entryName);
                        }
                    }
                    return created;
                });
                final String entryName = target.getName();
                team.addEntry(entryName);
            }
        }
    }

    private void cleanupTablistTeams(final Scoreboard scoreboard) {
        final Set<Team> toRemove = new HashSet<>();
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().startsWith(TEAM_PREFIX)) {
                toRemove.add(team);
            }
        }
        toRemove.forEach(Team::unregister);
    }

    private Map<String, String> buildPlaceholders(final Player player,
                                                  final PlayerTablistData data,
                                                  final int networkPlayers) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", player.getName());
        placeholders.put("%player_ping%", Integer.toString(player.getPing()));
        placeholders.put("%luckperms_prefix%", Optional.ofNullable(data.prefix()).orElse(""));
        placeholders.put("%luckperms_suffix%", Optional.ofNullable(data.suffix()).orElse(""));
        placeholders.put("%server_id%", serverId);
        placeholders.put("%bungee_total%", NUMBER_FORMAT.format(networkPlayers));
        return placeholders;
    }

    private void applyPlayerListName(final Player player,
                                     final String format,
                                     final Map<String, String> placeholders) {
        String rendered = applyPlaceholders(format, placeholders);
        rendered = colorize(rendered);
        if (rendered == null || rendered.isBlank()) {
            player.playerListName(Component.text(player.getName()));
            return;
        }
        player.playerListName(LEGACY_SERIALIZER.deserialize(rendered));
    }

    private Component renderHeader(final List<String> lines, final Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            return Component.empty();
        }
        final List<String> rendered = new ArrayList<>(lines.size());
        for (String line : lines) {
            String resolved = colorize(applyPlaceholders(line, placeholders));
            if (ChatColor.RESET.toString().equals(resolved)) {
                resolved = "";
            }
            rendered.add(resolved);
        }
        return LEGACY_SERIALIZER.deserialize(String.join("\n", rendered));
    }

    private Component renderFooter(final String frame, final Map<String, String> placeholders) {
        if (frame == null || frame.isEmpty()) {
            return Component.empty();
        }
        final String resolved = colorize(applyPlaceholders(frame, placeholders));
        final String[] lines = resolved.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (ChatColor.RESET.toString().equals(lines[index])) {
                lines[index] = "";
            }
        }
        return LEGACY_SERIALIZER.deserialize(String.join("\n", lines));
    }

    private String selectFooterFrame() {
        if (footerAnimation.frameCount() <= 1) {
            return footerAnimation.getCurrentFrame();
        }
        if (firstFrame) {
            firstFrame = false;
            return footerAnimation.getCurrentFrame();
        }
        return footerAnimation.nextFrame();
    }

    private void restartTasks() {
        cancelTasks();
        final TablistSettings current = settings.get();
        if (current == null || !current.enabled()) {
            return;
        }
        dataTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshPlayerData, 20L, 100L);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateTablist, 40L,
                Math.max(1L, current.updateIntervalTicks()));
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
    }

    private void clearTrackedPlayers() {
        for (UUID uuid : new HashSet<>(trackedPlayers)) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                resetPlayer(player);
            }
        }
        trackedPlayers.clear();
        lastKnownNames.clear();
        dataCache.clear();
    }

    private void resetPlayer(final Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        player.playerListName(Component.text(player.getName()));
        final Scoreboard scoreboard = player.getScoreboard();
        if (scoreboard != null) {
            cleanupTablistTeams(scoreboard);
        }
    }

    private TablistSettings loadSettings() {
        final File file = new File(plugin.getDataFolder(), CONFIG_FILE);
        if (!file.exists()) {
            try {
                plugin.saveResource(CONFIG_FILE, false);
            } catch (final IllegalArgumentException ignored) {
                // Resource not bundled, continue with defaults.
            }
        }
        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            if (file.exists()) {
                configuration.load(file);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Failed to load tablist configuration: " + exception.getMessage());
        }
        final ConfigurationSection section = configuration.getConfigurationSection(CONFIG_ROOT);
        if (section == null) {
            return TablistSettings.defaultSettings();
        }
        final boolean enabled = section.getBoolean("enabled", true);
        final long updateInterval = Math.max(1L, section.getLong("update-interval-ticks", 40L));
        final List<String> header = section.getStringList("header");
        final List<String> footerFrames = section.getStringList("footer-animation-frames");
        final String nameFormat = section.getString("player-name-format", "%luckperms_prefix%%player_name%");
        final boolean sort = section.getBoolean("sort-players-by-luckperms-weight", false);
        return new TablistSettings(enabled, updateInterval,
                header == null ? List.of() : List.copyOf(header),
                footerFrames == null ? List.of("") : List.copyOf(footerFrames),
                nameFormat,
                sort);
    }

    private void updateServerId() {
        final String configured = plugin.getConfig().getString("lobby.server_id", "1");
        serverId = (configured == null || configured.isBlank()) ? "1" : configured.trim();
    }

    private int resolveNetworkPlayerCount() {
        int total = Bukkit.getOnlinePlayers().size();
        if (serverPlaceholderCache != null) {
            total = Math.max(total, serverPlaceholderCache.getTotalPlayerCount());
        }
        if (velocityManager != null) {
            total = Math.max(total, velocityManager.getTotalPlayerCount());
        }
        return total;
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

    private record PlayerEntry(Player player, PlayerTablistData data) {
    }

    private record TablistSettings(boolean enabled,
                                   long updateIntervalTicks,
                                   List<String> headerLines,
                                   List<String> footerFrames,
                                   String playerNameFormat,
                                   boolean sortByWeight) {

        private static TablistSettings defaultSettings() {
            return new TablistSettings(true, 40L,
                    List.of("&r", "&3&lHENERIA NETWORK", "&7Bienvenue sur nos serveurs, &b%player_name%&7!", "&r"),
                    List.of(""),
                    "%luckperms_prefix%%player_name%",
                    true);
        }

        public boolean enabled() {
            return enabled;
        }

        public long updateIntervalTicks() {
            return updateIntervalTicks;
        }

        public List<String> headerLines() {
            return headerLines;
        }

        public List<String> footerFrames() {
            return footerFrames;
        }

        public String playerNameFormat() {
            return playerNameFormat;
        }

        public boolean sortByWeight() {
            return sortByWeight;
        }
    }
}
