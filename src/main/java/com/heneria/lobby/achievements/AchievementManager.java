package com.heneria.lobby.achievements;

import com.heneria.lobby.economy.EconomyManager;
import com.heneria.lobby.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages achievements and player progress.
 */
public class AchievementManager {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final EconomyManager economyManager;
    private final Map<String, Achievement> achievements = new HashMap<>();
    private final Map<UUID, Map<String, Instant>> unlocked = new ConcurrentHashMap<>();

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());

    public AchievementManager(JavaPlugin plugin, DatabaseManager databaseManager, EconomyManager economyManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.economyManager = economyManager;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveResource("achievements.yml", false);
        File file = new File(plugin.getDataFolder(), "achievements.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        achievements.clear();
        ConfigurationSection sec = config.getConfigurationSection("achievements");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection a = sec.getConfigurationSection(id);
                if (a == null) continue;
                String name = ChatColor.translateAlternateColorCodes('&', a.getString("name", id));
                String description = ChatColor.translateAlternateColorCodes('&', a.getString("description", ""));
                Material locked = Material.matchMaterial(a.getString("icon.locked", "STONE"));
                Material unlockedMat = Material.matchMaterial(a.getString("icon.unlocked", "DIAMOND"));
                Achievement.ConditionType type = Achievement.ConditionType.valueOf(a.getString("condition.type", "PARKOUR_FINISH_TIME"));
                double value = a.getDouble("condition.value", 0);
                long rewardCoins = a.getLong("rewards.coins", 0);
                String rewardTitle = a.getString("rewards.title", "");
                Achievement ach = new Achievement(id, name, description, locked, unlockedMat, type, value, rewardCoins, rewardTitle);
                achievements.put(id, ach);
            }
        }
    }

    public void loadPlayer(UUID uuid) {
        Map<String, Instant> map = new HashMap<>();
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT achievement_id, unlock_date FROM player_achievements WHERE player_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("achievement_id"), rs.getTimestamp("unlock_date").toInstant());
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load achievements for " + uuid + ": " + e.getMessage());
        }
        unlocked.put(uuid, map);
    }

    public boolean hasAchievement(UUID uuid, String id) {
        return unlocked.getOrDefault(uuid, Collections.emptyMap()).containsKey(id);
    }

    private void unlock(Player player, Achievement achievement) {
        UUID uuid = player.getUniqueId();
        if (hasAchievement(uuid, achievement.getId())) {
            return;
        }
        Instant now = Instant.now();
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("INSERT INTO player_achievements (player_uuid, achievement_id, unlock_date) VALUES (?,?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, achievement.getId());
            ps.setTimestamp(3, Timestamp.from(now));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save achievement: " + e.getMessage());
            return;
        }
        unlocked.computeIfAbsent(uuid, k -> new HashMap<>()).put(achievement.getId(), now);
        if (achievement.getRewardCoins() > 0) {
            economyManager.addCoins(uuid, achievement.getRewardCoins());
        }
        player.sendMessage(ChatColor.GOLD + "Succès débloqué: " + achievement.getName());
    }

    public void handleParkourFinish(Player player, long timeMillis) {
        double seconds = timeMillis / 1000.0;
        for (Achievement a : achievements.values()) {
            if (a.getConditionType() == Achievement.ConditionType.PARKOUR_FINISH_TIME &&
                    seconds <= a.getValue()) {
                unlock(player, a);
            }
        }
    }

    public void handleFriendAdded(Player player, int totalFriends) {
        for (Achievement a : achievements.values()) {
            if (a.getConditionType() == Achievement.ConditionType.ADD_FRIEND &&
                    totalFriends >= a.getValue()) {
                unlock(player, a);
            }
        }
    }

    public void openMenu(Player player) {
        int size = ((achievements.size() / 9) + 1) * 9;
        Inventory inv = Bukkit.createInventory(null, size, ChatColor.DARK_GREEN + "Succès");
        int slot = 0;
        Map<String, Instant> unlockedMap = unlocked.getOrDefault(player.getUniqueId(), Collections.emptyMap());
        for (Achievement a : achievements.values()) {
            boolean has = unlockedMap.containsKey(a.getId());
            Material mat = has ? a.getUnlockedIcon() : a.getLockedIcon();
            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName((has ? ChatColor.GREEN : ChatColor.GRAY) + a.getName());
                List<String> lore = new ArrayList<>();
                lore.add(ChatColor.WHITE + a.getDescription());
                if (has) {
                    lore.add(ChatColor.YELLOW + "Débloqué le " + formatter.format(unlockedMap.get(a.getId())));
                } else {
                    lore.add(ChatColor.DARK_GRAY + "???");
                }
                meta.setLore(lore);
                if (has) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.POWER, 1, true);
                    meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                item.setItemMeta(meta);
            }
            inv.setItem(slot++, item);
        }
        player.openInventory(inv);
    }

    public Map<String, Achievement> getAchievements() {
        return achievements;
    }
}
