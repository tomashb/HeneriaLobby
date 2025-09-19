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

import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class NPCManager {
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#[0-9a-fA-F]{6}$");
    private final LobbyPlugin plugin;
    private final Map<String, NPC> npcs = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final ActionProcessor actionProcessor;
    private final NamespacedKey npcKey;
    private final NPCInteractionHandler interactionHandler;
    private final NpcDAO npcDAO;
    private BukkitTask cleanupTask;
    private long interactionCooldownMillis = 1000L;
    private double maxInteractionDistance = 3.0D;
    private boolean lookAtPlayer = false;

    public NPCManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.actionProcessor = new ActionProcessor(plugin);
        this.npcKey = new NamespacedKey(plugin, "npc_name");
        this.interactionHandler = new NPCInteractionHandler(this);
        this.npcDAO = new NpcDAO(plugin.getDatabaseManager(), plugin);
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
        try {
            final List<NPCData> npcDataList = npcDAO.loadAllNpcs();
            int loaded = 0;
            for (final NPCData npcData : npcDataList) {
                try {
                    NPCData data = npcData;
                    final String rawColor = npcData.armorColor();
                    if (rawColor != null) {
                        final String normalized = normalizeArmorColor(rawColor);
                        if (normalized == null) {
                            LogUtils.warning(plugin, "Ignoring invalid armor color '" + rawColor + "' for NPC '" + npcData.name() + "'");
                            data = data.withArmorColor(null);
                        } else {
                            data = data.withArmorColor(normalized);
                        }
                    }

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

        try {
            final boolean created = npcDAO.createNpc(data);
            plugin.getLogger().fine("Insert result: " + (created ? 1 : 0) + " rows affected");

            if (!created) {
                throw new SQLException("No rows were inserted");
            }

            final NPC npc = new NPC(data, this);
            npcs.put(name, npc);
            npc.spawn();
            LogUtils.info(plugin, "Successfully created and spawned NPC: " + name);
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "SQL Error creating NPC: " + exception.getMessage(), exception);
            throw new RuntimeException("Failed to create NPC: " + exception.getMessage(), exception);
        }
    }

    public void deleteNPC(final String name) {
        final NPC npc = npcs.get(name);
        if (npc == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }

        final boolean deleted;
        try {
            deleted = npcDAO.deleteNpc(name);
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to delete NPC from database: " + exception.getMessage(), exception);
            throw new RuntimeException("Failed to delete NPC: " + exception.getMessage(), exception);
        }

        npc.despawn();
        npcs.remove(name);

        if (deleted) {
            LogUtils.info(plugin, "Deleted NPC: " + name);
        } else {
            LogUtils.warning(plugin, "NPC '" + name + "' was removed locally but no database row was deleted.");
        }
    }

    public void updateNPCActions(final String name, final List<String> newActions) {
        final NPC npc = npcs.get(name);
        if (npc == null) {
            throw new IllegalArgumentException("NPC '" + name + "' not found");
        }

        final boolean updated;
        try {
            updated = npcDAO.updateNpcActions(name, newActions);
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update NPC: " + exception.getMessage(), exception);
        }

        npc.despawn();
        final NPCData newData = npc.getData().withActions(newActions);
        final NPC newNPC = new NPC(newData, this);
        npcs.put(name, newNPC);
        newNPC.spawn();

        if (!updated) {
            LogUtils.warning(plugin, "Updated actions for NPC '" + name + "' locally but no database row was modified.");
        } else {
            LogUtils.info(plugin, "Updated NPC actions: " + name);
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

        final boolean updated;
        try {
            updated = npcDAO.updateNpcArmorColor(name, normalized);
        } catch (final SQLException exception) {
            throw new RuntimeException("Failed to update NPC color: " + exception.getMessage(), exception);
        }

        final ArmorStand armorStand = npc.getArmorStand();
        if (armorStand != null && !armorStand.isDead()) {
            applyArmorColor(armorStand, normalized);
        }

        npc.setData(npc.getData().withArmorColor(normalized));

        if (!updated) {
            LogUtils.warning(plugin, "Updated armor color for NPC '" + name + "' locally but no database row was modified.");
        }
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
