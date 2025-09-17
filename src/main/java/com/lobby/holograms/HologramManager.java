package com.lobby.holograms;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.data.HologramData;
import com.lobby.economy.event.EconomyTransactionEvent;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class HologramManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<String, Hologram> holograms = new ConcurrentHashMap<>();
    private final PlaceholderProcessor placeholderProcessor;
    private final AnimationManager animationManager;
    private final HologramRenderer renderer;
    private int updateTaskId = -1;
    private double maxViewDistance = 50.0D;
    private int maxPerWorld = 100;

    public HologramManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.placeholderProcessor = new PlaceholderProcessor(plugin);
        this.animationManager = new AnimationManager(plugin);
        this.renderer = new HologramRenderer();
    }

    public void initialize() {
        reloadSettings();
        registerListener();
        loadHologramsFromDatabase();
        loadDefaults();
        startUpdateTask();
        LogUtils.info(plugin, "HologramManager initialized with " + holograms.size() + " holograms");
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        if (updateTaskId != -1) {
            Bukkit.getScheduler().cancelTask(updateTaskId);
            updateTaskId = -1;
        }
        holograms.values().forEach(Hologram::remove);
        holograms.clear();
        HandlerList.unregisterAll(this);
        LogUtils.info(plugin, "HologramManager shut down");
    }

    public PlaceholderProcessor getPlaceholderProcessor() {
        return placeholderProcessor;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public HologramRenderer getRenderer() {
        return renderer;
    }

    public Collection<Hologram> getAllHolograms() {
        return Collections.unmodifiableCollection(holograms.values());
    }

    public Hologram getHologram(final String name) {
        return holograms.get(name);
    }

    public Set<String> getHologramNames() {
        return Collections.unmodifiableSet(holograms.keySet());
    }

    public LobbyPlugin getPlugin() {
        return plugin;
    }

    public double getMaxViewDistance() {
        return maxViewDistance;
    }

    public void createHologram(final String name, final Location location, final List<String> lines) {
        createHologram(name, location, lines, HologramData.AnimationType.NONE);
    }

    public void createHologram(final String name, final Location location, final List<String> lines,
                               final HologramData.AnimationType animation) {
        if (name == null || name.isEmpty() || location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Invalid hologram parameters");
        }
        if (holograms.containsKey(name)) {
            throw new IllegalArgumentException("Hologram with name '" + name + "' already exists");
        }
        validateWorldLimit(location.getWorld().getName());
        final List<String> sanitized = new ArrayList<>(lines == null ? List.of() : lines);
        final HologramData data = new HologramData(name, location.getWorld().getName(), location.getX(), location.getY(),
                location.getZ(), sanitized, true, animation);
        persistCreate(data);
        final Hologram hologram = new Hologram(data, this);
        holograms.put(name, hologram);
        hologram.spawn();
        LogUtils.info(plugin, "Created hologram: " + name);
    }

    public void deleteHologram(final String name) {
        final Hologram hologram = holograms.remove(name);
        if (hologram == null) {
            throw new IllegalArgumentException("Hologram '" + name + "' not found");
        }
        hologram.remove();
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM holograms WHERE name = ?")) {
            statement.setString(1, name);
            statement.executeUpdate();
            LogUtils.info(plugin, "Deleted hologram: " + name);
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to delete hologram from database", exception);
        }
    }

    public void updateHologramLines(final String name, final List<String> newLines) {
        final Hologram hologram = holograms.get(name);
        if (hologram == null) {
            throw new IllegalArgumentException("Hologram '" + name + "' not found");
        }
        final List<String> sanitized = new ArrayList<>(newLines == null ? List.of() : newLines);
        final HologramData updatedData = hologram.getData().withLines(sanitized);
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE holograms SET `lines` = ? WHERE name = ?")) {
            statement.setString(1, linesToJson(sanitized));
            statement.setString(2, name);
            statement.executeUpdate();
            final Hologram refreshed = new Hologram(updatedData, this);
            hologram.remove();
            holograms.put(name, refreshed);
            refreshed.spawn();
            LogUtils.info(plugin, "Updated hologram lines: " + name);
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update hologram: " + exception.getMessage(), exception);
        }
    }

    public void updateHologramAnimation(final String name, final HologramData.AnimationType animation) {
        final Hologram hologram = holograms.get(name);
        if (hologram == null) {
            throw new IllegalArgumentException("Hologram '" + name + "' not found");
        }
        final HologramData updatedData = hologram.getData().withAnimation(animation);
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE holograms SET animation = ? WHERE name = ?")) {
            statement.setString(1, animation.name());
            statement.setString(2, name);
            statement.executeUpdate();
            final Hologram refreshed = new Hologram(updatedData, this);
            hologram.remove();
            holograms.put(name, refreshed);
            refreshed.spawn();
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update hologram animation: " + exception.getMessage(), exception);
        }
    }

    private void loadHologramsFromDatabase() {
        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager == null) {
            return;
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM holograms WHERE visible = ?")) {
            statement.setBoolean(1, true);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String name = resultSet.getString("name");
                    final HologramData data = new HologramData(
                            name,
                            resultSet.getString("world"),
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            parseLines(resultSet.getString("lines")),
                            resultSet.getBoolean("visible"),
                            parseAnimation(resultSet.getString("animation"))
                    );
                    final Hologram hologram = new Hologram(data, this);
                    holograms.put(name, hologram);
                    hologram.spawn();
                }
            }
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to load holograms", exception);
        }
    }

    private void loadDefaults() {
        final File configFile = new File(plugin.getDataFolder(), "holograms.yml");
        if (!configFile.exists()) {
            plugin.saveResource("holograms.yml", false);
        }
        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(configFile);
        final ConfigurationSection section = configuration.getConfigurationSection("default_holograms");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (holograms.containsKey(key)) {
                continue;
            }
            final ConfigurationSection hologramSection = section.getConfigurationSection(key);
            if (hologramSection == null) {
                continue;
            }
            final String world = hologramSection.getString("world");
            final double x = hologramSection.getDouble("x");
            final double y = hologramSection.getDouble("y");
            final double z = hologramSection.getDouble("z");
            final String animationName = hologramSection.getString("animation", "NONE");
            final HologramData.AnimationType animation = parseAnimation(animationName);
            final List<String> lines = hologramSection.getStringList("lines");
            if (world == null || lines.isEmpty()) {
                continue;
            }
            final Location location = new Location(Bukkit.getWorld(world), x, y, z);
            if (location.getWorld() == null) {
                continue;
            }
            try {
                createHologram(key, location, lines, animation);
            } catch (final IllegalArgumentException exception) {
                LogUtils.warning(plugin, "Failed to create default hologram '" + key + "': " + exception.getMessage());
            }
        }
    }

    private void startUpdateTask() {
        final FileConfiguration configuration = plugin.getConfigManager().getMainConfig();
        final int interval = configuration.getInt("holograms.update_interval_ticks", 20);
        updateTaskId = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                holograms.values().forEach(Hologram::updateLines), interval, interval).getTaskId();
    }

    private void reloadSettings() {
        final FileConfiguration configuration = plugin.getConfigManager().getMainConfig();
        maxViewDistance = configuration.getDouble("holograms.max_view_distance", 50.0D);
        maxPerWorld = configuration.getInt("holograms.max_per_world", 100);
        final boolean animationsEnabled = configuration.getBoolean("holograms.animations_enabled", true);
        animationManager.setAnimationsEnabled(animationsEnabled);
    }

    private void registerListener() {
        HandlerList.unregisterAll(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void validateWorldLimit(final String worldName) {
        if (worldName == null || maxPerWorld <= 0) {
            return;
        }
        final long count = holograms.values().stream()
                .map(Hologram::getData)
                .filter(data -> worldName.equalsIgnoreCase(data.world()))
                .count();
        if (count >= maxPerWorld) {
            throw new IllegalStateException("Maximum holograms reached for world " + worldName);
        }
    }

    private void persistCreate(final HologramData data) {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO holograms (name, world, x, y, z, `lines`, visible, animation) VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, data.name());
            statement.setString(2, data.world());
            statement.setDouble(3, data.x());
            statement.setDouble(4, data.y());
            statement.setDouble(5, data.z());
            statement.setString(6, linesToJson(data.lines()));
            statement.setBoolean(7, data.visible());
            statement.setString(8, data.animation().name());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to create hologram: " + exception.getMessage(), exception);
        }
    }

    private List<String> parseLines(final String linesJson) {
        if (linesJson == null || linesJson.isBlank()) {
            return new ArrayList<>();
        }
        final String trimmed = linesJson.trim();
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return new ArrayList<>(List.of(trimmed));
        }
        final String content = trimmed.substring(1, trimmed.length() - 1);
        if (content.isBlank()) {
            return new ArrayList<>();
        }
        final String[] parts = content.split("\",\"");
        final List<String> lines = new ArrayList<>(parts.length);
        for (int index = 0; index < parts.length; index++) {
            String part = parts[index];
            if (index == 0 && part.startsWith("\"")) {
                part = part.substring(1);
            }
            if (index == parts.length - 1 && part.endsWith("\"")) {
                part = part.substring(0, part.length() - 1);
            }
            part = part.replace("\\\"", "\"").replace("\\\\", "\\");
            lines.add(part);
        }
        return lines;
    }

    private String linesToJson(final List<String> lines) {
        final List<String> sanitized = Optional.ofNullable(lines).orElse(List.of());
        return sanitized.stream()
                .map(value -> value.replace("\\", "\\\\").replace("\"", "\\\""))
                .map(value -> "\"" + value + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private HologramData.AnimationType parseAnimation(final String animation) {
        if (animation == null || animation.isEmpty()) {
            return HologramData.AnimationType.NONE;
        }
        try {
            return HologramData.AnimationType.valueOf(animation.toUpperCase());
        } catch (final IllegalArgumentException exception) {
            return HologramData.AnimationType.NONE;
        }
    }

    @EventHandler
    public void onEconomyTransaction(final EconomyTransactionEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> holograms.values().stream()
                .filter(hologram -> hasEconomyPlaceholder(hologram.getData().lines()))
                .forEach(Hologram::updateLines));
    }

    private boolean hasEconomyPlaceholder(final List<String> lines) {
        if (lines == null) {
            return false;
        }
        return lines.stream().anyMatch(line ->
                line.contains("%top_coins_") ||
                        line.contains("%top_tokens_") ||
                        line.contains("%player_coins%") ||
                        line.contains("%player_tokens%"));
    }
}
