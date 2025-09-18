package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.data.NPCData;
import com.lobby.utils.LogUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
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
import java.util.Locale;
import java.util.regex.Pattern;

public class NPCManager {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private final LobbyPlugin plugin;
    private final Map<String, NPC> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ActionProcessor actionProcessor;
    private final NamespacedKey npcKey;
    private final NPCInteractionHandler interactionHandler;
    private BukkitTask cleanupTask;
    private long interactionCooldownMillis = 1000L;
    private double maxInteractionDistance = 3.0D;
    private boolean lookAtPlayer = false;

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
                    final String name = rs.getString("name");
                    final String rawColor = rs.getString("armor_color");
                    final String armorColor = normalizeArmorColor(rawColor);
                    if (rawColor != null && armorColor == null) {
                        LogUtils.warning(plugin, "Ignoring invalid armor color '" + rawColor + "' for NPC '" + name + "'");
                    }
                    final NPCData data = new NPCData(
                            name,
                            rs.getString("display_name"),
                            rs.getString("world"),
                            rs.getDouble("x"),
                            rs.getDouble("y"),
                            rs.getDouble("z"),
                            rs.getFloat("yaw"),
                            rs.getFloat("pitch"),
                            rs.getString("head_texture"),
                            armorColor,
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
                headTexture, null, actions, true
        );

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("""
                     INSERT INTO npcs (name, display_name, world, x, y, z, yaw, pitch, head_texture, armor_color, actions, visible)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            stmt.setString(10, data.armorColor());
            stmt.setString(11, actionsToJson(actions));
            stmt.setBoolean(12, true);

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

    public void updateNPCArmorColor(final String name, final String hexColor) {
        final NPC npc = npcs.get(name);
        if (npc == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }

        final String normalized = normalizeArmorColor(hexColor);
        if (normalized == null) {
            throw new IllegalArgumentException("Invalid armor color: " + hexColor);
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE npcs SET armor_color = ? WHERE name = ?")) {
            stmt.setString(1, normalized);
            stmt.setString(2, name);
            stmt.executeUpdate();
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update NPC color: " + exception.getMessage(), exception);
        }

        final ArmorStand armorStand = npc.getArmorStand();
        if (armorStand != null && !armorStand.isDead()) {
            applyArmorColor(armorStand, normalized);
        }

        npc.setData(npc.getData().withArmorColor(normalized));
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

    public boolean isValidArmorColor(final String color) {
        if (color == null) {
            return false;
        }

        final String trimmed = color.trim();
        if (!trimmed.startsWith("#")) {
            return false;
        }

        return HEX_COLOR_PATTERN.matcher(trimmed).matches();
    }

    public boolean applyArmorColor(final ArmorStand armorStand, final String hexColor) {
        if (armorStand == null) {
            return false;
        }

        final String normalized = normalizeArmorColor(hexColor);
        if (normalized == null) {
            return false;
        }

        final var equipment = armorStand.getEquipment();
        if (equipment == null) {
            return false;
        }

        final ItemStack[] armorPieces = equipment.getArmorContents();
        boolean applied = false;

        try {
            final java.awt.Color awtColor = java.awt.Color.decode(normalized);
            final Color bukkitColor = Color.fromRGB(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());

            for (int index = 0; index < armorPieces.length; index++) {
                final ItemStack piece = armorPieces[index];
                if (piece == null || piece.getType() == Material.AIR) {
                    continue;
                }

                if (!piece.getType().name().startsWith("LEATHER_")) {
                    continue;
                }

                final var meta = piece.getItemMeta();
                if (!(meta instanceof LeatherArmorMeta leatherMeta)) {
                    continue;
                }

                leatherMeta.setColor(bukkitColor);
                piece.setItemMeta(leatherMeta);
                armorPieces[index] = piece;
                applied = true;
            }

            if (applied) {
                equipment.setArmorContents(armorPieces);
            }
        } catch (final NumberFormatException exception) {
            LogUtils.warning(plugin, "Invalid armor color provided: " + hexColor);
            return false;
        }

        return applied;
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
        lookAtPlayer = config.getBoolean("npcs.look_at_player", false);
    }

    private String normalizeArmorColor(final String hexColor) {
        if (hexColor == null) {
            return null;
        }

        String trimmed = hexColor.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }

        if (!HEX_COLOR_PATTERN.matcher(trimmed).matches()) {
            return null;
        }

        return trimmed.toUpperCase(Locale.ROOT);
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
