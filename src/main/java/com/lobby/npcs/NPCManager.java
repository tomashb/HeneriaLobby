package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.data.NPCData;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class NPCManager {
    private final LobbyPlugin plugin;
    private final Map<String, NPC> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ActionProcessor actionProcessor;
    private final NamespacedKey npcKey;
    private final NPCInteractionHandler interactionHandler;
    private BukkitTask cleanupTask;
    private long interactionCooldownMillis = 1000L;
    private double maxInteractionDistance = 3.0D;
    private boolean lookAtPlayer = true;

    public NPCManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.actionProcessor = new ActionProcessor(plugin);
        this.npcKey = new NamespacedKey(plugin, "npc_name");
        this.interactionHandler = new NPCInteractionHandler(this);
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

    private void loadNPCsFromDatabase() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM npcs WHERE visible = TRUE");
             ResultSet rs = stmt.executeQuery()) {

            int loaded = 0;
            while (rs.next()) {
                try {
                    final NPCData data = new NPCData(
                            rs.getString("name"),
                            rs.getString("display_name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("head_texture"),
                            parseActions(rs.getString("actions")),
                            rs.getBoolean("visible")
                    );

                    final NPC npc = new NPC(data, this);
                    npcs.put(data.name(), npc);
                    npc.spawn();
                    loaded++;
                } catch (final Exception exception) {
                    plugin.getLogger().warning("Failed to load NPC: " + exception.getMessage());
                }
            }

            LogUtils.info(plugin, "Loaded " + loaded + " NPCs from database");
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to load NPCs: " + exception.getMessage(), exception);
        }
    }

    public void createNPC(final String name, final String displayName, final Location location,
                          final String headTexture, final List<String> actions) {
        if (npcs.containsKey(name)) {
            throw new IllegalArgumentException("NPC with name '" + name + "' already exists");
        }

        LogUtils.info(plugin, "Creating NPC: " + name + " at " + formatLocation(location));

        final NPCData data = new NPCData(
                name, displayName,
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                headTexture, actions, true
        );

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO npcs (name, display_name, world, x, y, z, yaw, pitch, head_texture, actions, visible)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {

            stmt.setString(1, name);
            stmt.setString(2, displayName);
            stmt.setString(3, location.getWorld().getName());
            stmt.setDouble(4, location.getX());
            stmt.setDouble(5, location.getY());
            stmt.setDouble(6, location.getZ());
            stmt.setFloat(7, location.getYaw());
            stmt.setFloat(8, location.getPitch());
            stmt.setString(9, headTexture);
            stmt.setString(10, actionsToJson(actions));
            stmt.setBoolean(11, true);

            final int result = stmt.executeUpdate();
            plugin.getLogger().fine("Insert result: " + result + " rows affected");

            if (result > 0) {
                final NPC npc = new NPC(data, this);
                npcs.put(name, npc);
                npc.spawn();
                LogUtils.info(plugin, "Successfully created and spawned NPC: " + name);
            } else {
                throw new SQLException("No rows were inserted");
            }

        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "SQL Error creating NPC: " + exception.getMessage(), exception);
            throw new RuntimeException("Failed to create NPC: " + exception.getMessage(), exception);
        }
    }

    public void deleteNPC(final String name) {
        final NPC npc = npcs.remove(name);
        if (npc == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }

        npc.despawn();

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM npcs WHERE name = ?")) {
            stmt.setString(1, name);
            stmt.executeUpdate();
            LogUtils.info(plugin, "Deleted NPC: " + name);
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to delete NPC from database: " + exception.getMessage(), exception);
        }
    }

    public void updateNPCActions(final String name, final List<String> newActions) {
        final NPC npc = npcs.get(name);
        if (npc == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE npcs SET actions = ? WHERE name = ?")) {
            stmt.setString(1, actionsToJson(newActions));
            stmt.setString(2, name);
            stmt.executeUpdate();

            npc.despawn();
            final NPCData newData = npc.getData().withActions(newActions);
            final NPC newNPC = new NPC(newData, this);
            npcs.put(name, newNPC);
            newNPC.spawn();

            LogUtils.info(plugin, "Updated NPC actions: " + name);
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update NPC: " + exception.getMessage(), exception);
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
            plugin.getLogger().warning("Failed to parse NPC actions: " + actionsJson);
        }

        return Arrays.asList("[MESSAGE] &cError loading NPC actions");
    }

    private String actionsToJson(final List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", actions) + "\"]";
    }

    public boolean isOnCooldown(final UUID player, final String npcName) {
        final Map<String, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) {
            return false;
        }

        final Long cooldownEnd = playerCooldowns.get(npcName);
        return cooldownEnd != null && System.currentTimeMillis() < cooldownEnd;
    }

    public long getRemainingCooldown(final UUID player, final String npcName) {
        final Map<String, Long> playerCooldowns = cooldowns.get(player);
        if (playerCooldowns == null) {
            return 0L;
        }

        final Long cooldownEnd = playerCooldowns.get(npcName);
        if (cooldownEnd == null) {
            return 0L;
        }

        final long remaining = cooldownEnd - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public void setCooldown(final UUID player, final String npcName, final long durationMs) {
        cooldowns.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(npcName, System.currentTimeMillis() + durationMs);
    }

    private void startCooldownCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
        }

        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            final long now = System.currentTimeMillis();
            cooldowns.entrySet().removeIf(entry -> {
                entry.getValue().entrySet().removeIf(cooldown -> cooldown.getValue() < now);
                return entry.getValue().isEmpty();
            });
        }, 0L, 1200L);
    }

    public NPC getNPC(final String name) {
        return npcs.get(name);
    }

    public Collection<NPC> getAllNPCs() {
        return npcs.values();
    }

    public Set<String> getNPCNames() {
        return npcs.keySet();
    }

    public ActionProcessor getActionProcessor() {
        return actionProcessor;
    }

    public NPCInteractionHandler getInteractionHandler() {
        return interactionHandler;
    }

    public LobbyPlugin getPlugin() {
        return plugin;
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

    private void reloadSettings() {
        final var config = plugin.getConfig();
        interactionCooldownMillis = Math.max(0L, config.getLong("npcs.interaction_cooldown_ms", 1000L));
        maxInteractionDistance = Math.max(0D, config.getDouble("npcs.max_interaction_distance", 3.0D));
        lookAtPlayer = config.getBoolean("npcs.look_at_player", true);
    }

    private String formatLocation(final Location location) {
        return String.format("%s %.1f,%.1f,%.1f",
                location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ());
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
}
