package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.data.NPCData;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

public class NPCManager {

    private final LobbyPlugin plugin;
    private final ConcurrentMap<String, NPC> npcs = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, ConcurrentMap<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ActionProcessor actionProcessor;
    private final NamespacedKey npcKey;

    private BukkitTask cleanupTask;
    private long interactionCooldownMillis = 1000L;
    private double maxInteractionDistance = 3.0D;
    private boolean lookAtPlayer = true;
    private int maxPerWorld = 50;

    public NPCManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.actionProcessor = new ActionProcessor(plugin);
        this.npcKey = new NamespacedKey(plugin, "npc_name");
    }

    public void initialize() {
        reloadSettings();
        loadNPCsFromDatabase();
        startCooldownCleanupTask();
        LogUtils.info(plugin, "NPCManager initialized with " + npcs.size() + " NPCs");
    }

    public void reload() {
        shutdown();
        initialize();
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        npcs.values().forEach(NPC::despawn);
        npcs.clear();
        cooldowns.clear();
        LogUtils.info(plugin, "NPCManager shut down");
    }

    public LobbyPlugin getPlugin() {
        return plugin;
    }

    public ActionProcessor getActionProcessor() {
        return actionProcessor;
    }

    public NamespacedKey getNpcKey() {
        return npcKey;
    }

    public long getInteractionCooldownMillis() {
        return interactionCooldownMillis;
    }

    public double getMaxInteractionDistance() {
        return maxInteractionDistance;
    }

    public boolean shouldLookAtPlayer() {
        return lookAtPlayer;
    }

    public NPC getNPC(final String name) {
        return name == null ? null : npcs.get(name);
    }

    public Set<String> getNPCNames() {
        return Collections.unmodifiableSet(npcs.keySet());
    }

    public boolean isOnCooldown(final UUID player, final String npcName) {
        if (player == null || npcName == null) {
            return false;
        }
        final ConcurrentMap<String, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) {
            return false;
        }
        final Long expiresAt = playerCooldowns.get(npcName);
        return expiresAt != null && expiresAt > System.currentTimeMillis();
    }

    public void setCooldown(final UUID player, final String npcName, final long durationMs) {
        if (player == null || npcName == null || durationMs <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player, key -> new ConcurrentHashMap<>())
                .put(npcName, System.currentTimeMillis() + durationMs);
    }

    public void createNPC(final String name, final String displayName, final Location location,
                          final String headTexture, final List<String> actions) {
        if (name == null || name.isEmpty() || location == null || location.getWorld() == null) {
            throw new IllegalArgumentException("Invalid NPC parameters");
        }
        if (npcs.containsKey(name)) {
            throw new IllegalArgumentException("NPC '" + name + "' already exists");
        }
        validateWorldLimit(location.getWorld().getName());
        final List<String> sanitizedActions = sanitizeActions(actions);
        final NPCData data = new NPCData(name, displayName, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw(), location.getPitch(),
                headTexture, sanitizedActions, true);
        persistCreate(data);
        final NPC npc = new NPC(data, this);
        npcs.put(name, npc);
        npc.spawn();
        LogUtils.info(plugin, "Created NPC: " + name);
    }

    public boolean deleteNPC(final String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        final NPC npc = npcs.remove(name);
        if (npc != null) {
            npc.despawn();
        }
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM npcs WHERE name = ?")) {
            statement.setString(1, name);
            final int affected = statement.executeUpdate();
            if (affected == 0 && npc == null) {
                return false;
            }
            LogUtils.info(plugin, "Deleted NPC: " + name);
            return true;
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to delete NPC '" + name + "'", exception);
            return false;
        }
    }

    public void updateNPCActions(final String name, final List<String> actions) {
        final NPC existing = npcs.get(name);
        if (existing == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }
        final List<String> sanitized = sanitizeActions(actions);
        final NPCData updatedData = existing.getData().withActions(sanitized);
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("UPDATE npcs SET `actions` = ? WHERE name = ?")) {
            statement.setString(1, actionsToJson(sanitized));
            statement.setString(2, name);
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update NPC actions: " + exception.getMessage(), exception);
        }
        final NPC replacement = new NPC(updatedData, this);
        if (existing.isSpawned()) {
            existing.despawn();
            replacement.spawn();
        }
        npcs.put(name, replacement);
        LogUtils.info(plugin, "Updated actions for NPC: " + name);
    }

    private void reloadSettings() {
        final var config = plugin.getConfig();
        interactionCooldownMillis = Math.max(0L, config.getLong("npcs.interaction_cooldown_ms", 1000L));
        maxInteractionDistance = Math.max(0D, config.getDouble("npcs.max_interaction_distance", 3.0D));
        lookAtPlayer = config.getBoolean("npcs.look_at_player", true);
        maxPerWorld = config.getInt("npcs.max_per_world", 50);
        if (maxPerWorld < 0) {
            maxPerWorld = 0;
        }
    }

    private void loadNPCsFromDatabase() {
        npcs.values().forEach(NPC::despawn);
        npcs.clear();
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM npcs")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    final String worldName = resultSet.getString("world");
                    final World world = Bukkit.getWorld(worldName);
                    if (world == null) {
                        LogUtils.warning(plugin, "Skipping NPC in unknown world '" + worldName + "'");
                        continue;
                    }
                    boolean visible = true;
                    try {
                        visible = resultSet.getBoolean("visible");
                    } catch (final SQLException exception) {
                        plugin.getLogger().fine("Column 'visible' not found in npcs table, using default value");
                    }
                    if (!visible) {
                        continue;
                    }
                    final List<String> actions = parseActions(resultSet.getString("actions"));
                    final NPCData data = new NPCData(
                            resultSet.getString("name"),
                            resultSet.getString("display_name"),
                            worldName,
                            resultSet.getDouble("x"),
                            resultSet.getDouble("y"),
                            resultSet.getDouble("z"),
                            resultSet.getFloat("yaw"),
                            resultSet.getFloat("pitch"),
                            resultSet.getString("head_texture"),
                            actions,
                            visible
                    );
                    try {
                        validateWorldLimit(worldName);
                    } catch (final IllegalStateException exception) {
                        LogUtils.warning(plugin, "Skipping NPC '" + resultSet.getString("name")
                                + "' due to world limit: " + exception.getMessage());
                        continue;
                    }
                    final NPC npc = new NPC(data, this);
                    npcs.put(data.name(), npc);
                    npc.spawn();
                }
            }
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to load NPCs", exception);
        }
    }

    private void startCooldownCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupCooldowns, 200L, 200L);
    }

    private void cleanupCooldowns() {
        final long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ConcurrentMap<String, Long>> entry : cooldowns.entrySet()) {
            final ConcurrentMap<String, Long> values = entry.getValue();
            values.entrySet().removeIf(e -> e.getValue() <= now);
            if (values.isEmpty()) {
                cooldowns.remove(entry.getKey(), values);
            }
        }
    }

    private void validateWorldLimit(final String worldName) {
        if (worldName == null || worldName.isEmpty()) {
            throw new IllegalArgumentException("World name cannot be empty");
        }
        if (maxPerWorld <= 0) {
            return;
        }
        final long count = npcs.values().stream()
                .filter(npc -> worldName.equalsIgnoreCase(npc.getData().world()))
                .count();
        if (count >= maxPerWorld) {
            throw new IllegalStateException("NPC limit reached for world '" + worldName + "'");
        }
    }

    private List<String> sanitizeActions(final List<String> actions) {
        final List<String> sanitized = new ArrayList<>();
        if (actions == null) {
            return sanitized;
        }
        for (final String action : actions) {
            sanitized.add(action == null ? "" : action);
        }
        return sanitized;
    }

    private void persistCreate(final NPCData data) {
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     INSERT INTO npcs (name, display_name, world, x, y, z, yaw, pitch, head_texture, `actions`, visible)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, data.name());
            statement.setString(2, data.displayName());
            statement.setString(3, data.world());
            statement.setDouble(4, data.x());
            statement.setDouble(5, data.y());
            statement.setDouble(6, data.z());
            statement.setFloat(7, data.yaw());
            statement.setFloat(8, data.pitch());
            statement.setString(9, data.headTexture());
            statement.setString(10, actionsToJson(data.actions()));
            statement.setBoolean(11, data.visible());
            statement.executeUpdate();
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to create NPC: " + exception.getMessage(), exception);
        }
    }

    private List<String> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            if (actionsJson.startsWith("[") && actionsJson.endsWith("]")) {
                actionsJson = actionsJson.substring(1, actionsJson.length() - 1);
                if (actionsJson.trim().isEmpty()) {
                    return new ArrayList<>();
                }

                return Arrays.stream(actionsJson.split("\",\""))
                        .map(s -> s.replace("\"", ""))
                        .collect(Collectors.toList());
            }
        } catch (final Exception exception) {
            LogUtils.warning(plugin, "Failed to parse NPC actions: " + actionsJson);
        }

        return Arrays.asList("[MESSAGE] &cError loading NPC actions");
    }

    private String actionsToJson(final List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", actions) + "\"]";
    }
}
