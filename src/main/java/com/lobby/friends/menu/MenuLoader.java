package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.BlockedPlayersManager;
import com.lobby.friends.manager.FriendCodeManager;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.utils.HeadManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads friend-related inventories from YAML configuration files while applying
 * placeholder replacements and HeadDatabase decorations when available.
 */
public class MenuLoader {

    private final LobbyPlugin plugin;
    private final HeadManager headManager;

    public MenuLoader(final LobbyPlugin plugin) {
        this(plugin, new HeadManager(plugin));
    }

    public MenuLoader(final LobbyPlugin plugin, final HeadManager headManager) {
        this.plugin = plugin;
        this.headManager = headManager != null ? headManager : new HeadManager(plugin);
    }

    public Inventory loadMenuFromConfig(final String menuFileName, final Player player) {
        if (plugin == null) {
            return null;
        }
        final File menuFile = new File(plugin.getDataFolder(), "friends/" + menuFileName);
        if (!menuFile.exists()) {
            plugin.getLogger().warning("Fichier de menu introuvable: " + menuFileName);
            return null;
        }

        final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(menuFile);
        final Map<String, String> placeholders = buildPlaceholders(player);

        String title = configuration.getString("menu.title", "§8» §cMenu");
        final int size = configuration.getInt("menu.size", 54);
        title = applyPlaceholders(title, placeholders);

        final Inventory inventory = Bukkit.createInventory(null, size, title);
        addGlassItems(inventory, configuration, placeholders);
        addMenuItems(inventory, configuration, placeholders);
        addNavigationItems(inventory, configuration, placeholders);
        return inventory;
    }

    private void addGlassItems(final Inventory inventory,
                               final YamlConfiguration configuration,
                               final Map<String, String> placeholders) {
        final List<Integer> glassSlots = configuration.getIntegerList("glass_slots");
        if (glassSlots.isEmpty()) {
            return;
        }

        final ConfigurationSection glassSection = configuration.getConfigurationSection("glass_item");
        final String materialName = glassSection != null ? glassSection.getString("material", "BLACK_STAINED_GLASS_PANE")
                : "BLACK_STAINED_GLASS_PANE";
        final Material material = resolveMaterial(materialName, Material.BLACK_STAINED_GLASS_PANE);
        final String name = glassSection != null ? applyPlaceholders(glassSection.getString("name", "§7"), placeholders)
                : "§7";
        final List<String> lore = glassSection != null
                ? applyLorePlaceholders(glassSection.getStringList("lore"), placeholders)
                : List.of();

        final ItemStack item = headManager.createFallbackItem(name, lore, material);
        for (Integer slot : glassSlots) {
            if (slot == null) {
                continue;
            }
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, item);
            }
        }
    }

    private void addMenuItems(final Inventory inventory,
                              final YamlConfiguration configuration,
                              final Map<String, String> placeholders) {
        if (!configuration.contains("items")) {
            return;
        }
        final ConfigurationSection itemsSection = configuration.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }

        for (String key : itemsSection.getKeys(false)) {
            final ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final int slot = itemSection.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            final String name = applyPlaceholders(itemSection.getString("name", "§7Item"), placeholders);
            final List<String> lore = applyLorePlaceholders(itemSection.getStringList("lore"), placeholders);
            final String headId = itemSection.getString("head_id");

            final ItemStack item;
            if (headId != null && !headId.isBlank()) {
                item = headManager.createCustomHead(headId.trim(), name, lore);
            } else {
                final String materialName = itemSection.getString("material", "STONE");
                final Material material = resolveMaterial(materialName, Material.STONE);
                item = headManager.createFallbackItem(name, lore, material);
            }
            inventory.setItem(slot, item);
        }
    }

    private void addNavigationItems(final Inventory inventory,
                                    final YamlConfiguration configuration,
                                    final Map<String, String> placeholders) {
        if (!configuration.contains("navigation")) {
            return;
        }
        final ConfigurationSection navigationSection = configuration.getConfigurationSection("navigation");
        if (navigationSection == null) {
            return;
        }

        for (String key : navigationSection.getKeys(false)) {
            final ConfigurationSection itemSection = navigationSection.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final int slot = itemSection.getInt("slot", -1);
            if (slot < 0 || slot >= inventory.getSize()) {
                continue;
            }

            final String name = applyPlaceholders(itemSection.getString("name", "§7Navigation"), placeholders);
            final List<String> lore = applyLorePlaceholders(itemSection.getStringList("lore"), placeholders);
            final String headId = itemSection.getString("head_id");
            ItemStack item;
            if (headId != null && !headId.isBlank()) {
                item = headManager.createCustomHead(headId.trim(), name, lore);
            } else {
                final String materialName = itemSection.getString("material", "STONE");
                final Material material = resolveMaterial(materialName, Material.STONE);
                item = headManager.createFallbackItem(name, lore, material);
            }
            inventory.setItem(slot, item);
        }
    }

    private Material resolveMaterial(final String materialName, final Material fallback) {
        if (materialName == null || materialName.isBlank()) {
            return fallback;
        }
        final Material material = Material.matchMaterial(materialName.trim().toUpperCase(Locale.ROOT));
        if (material == null) {
            plugin.getLogger().warning("Material invalide dans le menu: " + materialName);
            return fallback;
        }
        return material;
    }

    private List<String> applyLorePlaceholders(final List<String> lines, final Map<String, String> placeholders) {
        if (lines == null || lines.isEmpty()) {
            return Collections.emptyList();
        }
        final List<String> result = new ArrayList<>(lines.size());
        for (String line : lines) {
            result.add(applyPlaceholders(line, placeholders));
        }
        return result;
    }

    private String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        if (input == null || placeholders.isEmpty()) {
            return input;
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private Map<String, String> buildPlaceholders(final Player player) {
        if (player == null) {
            return Map.of();
        }
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        placeholders.put("%player_uuid%", player.getUniqueId().toString());

        final FriendCodeManager codeManager = plugin.getFriendCodeManager();
        String friendCode = null;
        if (codeManager != null) {
            friendCode = codeManager.getCachedCode(player.getUniqueId());
            if (friendCode == null) {
                friendCode = codeManager.getPlayerCode(player.getUniqueId());
            }
        }
        placeholders.put("%player_friend_code%", friendCode != null ? friendCode : "Non généré");

        final BlockedPlayersManager blockedPlayersManager = plugin.getBlockedPlayersManager();
        if (blockedPlayersManager != null) {
            placeholders.put("%blocked_count%", String.valueOf(blockedPlayersManager.getBlockedCount(player.getUniqueId())));
        }

        final FriendsManager friendsManager = plugin.getFriendsManager();
        if (friendsManager != null) {
            // cached values may not be immediately available; provide quick estimates
            placeholders.putIfAbsent("%total_friends%", String.valueOf(friendsManager.getCachedFriendsCount(player.getUniqueId())));
            placeholders.putIfAbsent("%online_friends%", String.valueOf(friendsManager.getCachedOnlineFriendsCount(player.getUniqueId())));
            placeholders.putIfAbsent("%pending_requests%", String.valueOf(friendsManager.getCachedPendingRequests(player.getUniqueId())));
        }

        placeholders.putIfAbsent("%total_friends%", "0");
        placeholders.putIfAbsent("%online_friends%", "0");
        placeholders.putIfAbsent("%pending_requests%", "0");
        placeholders.putIfAbsent("%favorite_friends%", "0");
        placeholders.putIfAbsent("%recent_24h%", "0");
        placeholders.putIfAbsent("%recent_week%", "0");
        placeholders.putIfAbsent("%mutual_friends%", "0");
        placeholders.putIfAbsent("%same_server%", "0");
        placeholders.putIfAbsent("%messages_sent%", "0");
        placeholders.putIfAbsent("%invitations_sent%", "0");
        placeholders.putIfAbsent("%playtime_together%", "0");
        placeholders.putIfAbsent("%common_sessions%", "0");
        placeholders.putIfAbsent("%weekly_activity%", "0");
        placeholders.putIfAbsent("%friendship_score%", "0");
        placeholders.putIfAbsent("%best_friend%", "Aucun");
        placeholders.putIfAbsent("%longest_friendship%", "0j");
        placeholders.putIfAbsent("%acceptance_rate%", "0");
        placeholders.putIfAbsent("%social_level%", "1");
        placeholders.putIfAbsent("%peak_hour%", "--:--");
        placeholders.putIfAbsent("%favorite_day%", "?" );
        placeholders.putIfAbsent("%avg_presence%", "0");
        placeholders.putIfAbsent("%time_compatibility%", "0");
        placeholders.putIfAbsent("%next_prediction%", "Bientôt disponible");
        placeholders.putIfAbsent("%unlocked_achievements%", "0");
        placeholders.putIfAbsent("%total_achievements%", "0");
        placeholders.putIfAbsent("%reputation_points%", "0");
        placeholders.putIfAbsent("%current_title%", "Novice social");
        placeholders.putIfAbsent("%social_rank%", "0");
        placeholders.putIfAbsent("%last_update%", "Jamais");
        placeholders.putIfAbsent("%member_since%", "--/--/----");

        return placeholders;
    }
}

