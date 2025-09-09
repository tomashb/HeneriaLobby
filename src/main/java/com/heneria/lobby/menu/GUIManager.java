package com.heneria.lobby.menu;

import com.heneria.lobby.HeneriaLobbyPlugin;
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

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads menus from configuration and builds inventories dynamically.
 */
public class GUIManager {

    private final HeneriaLobbyPlugin plugin;
    private final ServerInfoManager serverInfoManager;
    private FileConfiguration config;
    private final Map<String, Menu> menus = new HashMap<>();
    private final Map<Integer, MenuItem> navigationItems = new HashMap<>();

    public GUIManager(HeneriaLobbyPlugin plugin, ServerInfoManager serverInfoManager) {
        this.plugin = plugin;
        this.serverInfoManager = serverInfoManager;
        loadConfig();
    }

    public void loadConfig() {
        plugin.saveResource("menus.yml", false);
        plugin.saveResource("items.yml", false);
        File file = new File(plugin.getDataFolder(), "menus.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        menus.clear();
        navigationItems.clear();
        ConfigurationSection menusSec = config.getConfigurationSection("menus");
        if (menusSec != null) {
            for (String key : menusSec.getKeys(false)) {
                ConfigurationSection sec = menusSec.getConfigurationSection(key);
                String title = color(sec.getString("title", key));
                int size = sec.getInt("size", 27);
                Map<Integer, MenuItem> items = new HashMap<>();
                ConfigurationSection itemsSec = sec.getConfigurationSection("items");
                if (itemsSec != null) {
                    for (String itemKey : itemsSec.getKeys(false)) {
                        ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
                        int slot = itemSec.getInt("slot");
                        ItemStack stack = buildItem(itemSec);
                        String action = itemSec.getString("action", "");
                        items.put(slot, new MenuItem(stack, action));
                    }
                }
                Menu menu = new Menu(key, title, size, items);
                menus.put(key, menu);
            }
        }
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        FileConfiguration itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection navSec = itemsConfig.getConfigurationSection("items");
        if (navSec != null) {
            for (String key : navSec.getKeys(false)) {
                ConfigurationSection itemSec = navSec.getConfigurationSection(key);
                int slot = itemSec.getInt("slot");
                ItemStack stack = buildItem(itemSec);
                String action = itemSec.getString("action", "");
                navigationItems.put(slot, new MenuItem(stack, action));
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection sec) {
        Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
        if (mat == null) {
            mat = Material.STONE;
        }
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(sec.getString("name", "")));
            List<String> lore = sec.getStringList("lore").stream()
                    .map(this::color)
                    .collect(Collectors.toList());
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public void openMenu(Player player, String menuName) {
        Menu menu = menus.get(menuName);
        if (menu == null) {
            return;
        }
        Inventory inv = Bukkit.createInventory(null, menu.getSize(), menu.getTitle());
        for (Map.Entry<Integer, MenuItem> entry : menu.getItems().entrySet()) {
            MenuItem item = entry.getValue();
            ItemStack stack = item.getItemStack().clone();
            ItemMeta meta = stack.getItemMeta();
            String action = item.getAction();
            if (action.startsWith("connect_server:")) {
                String server = action.split(":", 2)[1];
                serverInfoManager.requestPlayerCount(player, server);
                int count = serverInfoManager.getPlayerCount(server);
                if (meta != null && meta.hasLore()) {
                    List<String> lore = meta.getLore().stream()
                            .map(l -> l.replace("%players%", count >= 0 ? String.valueOf(count) : "N/A"))
                            .collect(Collectors.toList());
                    meta.setLore(lore);
                }
            }
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore().stream()
                        .map(l -> l.replace("%player%", player.getName()))
                        .collect(Collectors.toList());
                meta.setLore(lore);
            }
            if (meta != null) {
                stack.setItemMeta(meta);
            }
            inv.setItem(entry.getKey(), stack);
        }
        player.openInventory(inv);
    }

    public Map<Integer, MenuItem> getNavigationItems() {
        return navigationItems;
    }

    public MenuItem getNavigationItem(ItemStack stack) {
        for (MenuItem item : navigationItems.values()) {
            if (stack.isSimilar(item.getItemStack())) {
                return item;
            }
        }
        return null;
    }

    public boolean isNavigationItem(ItemStack stack) {
        return getNavigationItem(stack) != null;
    }

    public Menu getMenuByTitle(String title) {
        for (Menu menu : menus.values()) {
            if (menu.getTitle().equals(title)) {
                return menu;
            }
        }
        return null;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}

