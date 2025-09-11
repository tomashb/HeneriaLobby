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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.heneria.lobby.util.Utils;

/**
 * Loads menus from configuration and builds inventories dynamically.
 */
public class GUIManager {

    private final HeneriaLobbyPlugin plugin;
    private final ServerInfoManager serverInfoManager;
    private FileConfiguration config;
    private final Map<String, Menu> menus = new HashMap<>();
    private final Map<Integer, MenuItem> navigationItems = new HashMap<>();
    private final Map<UUID, BukkitTask> borderTasks = new HashMap<>();

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
                String rawTitle = sec.getString("title", key);
                String title = Utils.format("&5&l" + ChatColor.stripColor(Utils.format(rawTitle)));
                int size = sec.getInt("size", 27);
                Map<Integer, MenuItem> items = new HashMap<>();
                ConfigurationSection itemsSec = sec.getConfigurationSection("items");
                if (itemsSec != null) {
                    for (String itemKey : itemsSec.getKeys(false)) {
                        ConfigurationSection itemSec = itemsSec.getConfigurationSection(itemKey);
                        ItemStack stack = buildItem(itemSec);
                        String action = itemSec.getString("action", "");
                        if (itemSec.isList("slots")) {
                            for (int slot : itemSec.getIntegerList("slots")) {
                                items.put(slot, new MenuItem(stack, action));
                            }
                        } else {
                            int slot = itemSec.getInt("slot");
                            items.put(slot, new MenuItem(stack, action));
                        }
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
                ItemStack stack = buildItem(itemSec);
                String action = itemSec.getString("action", "");
                if (itemSec.isList("slots")) {
                    for (int slot : itemSec.getIntegerList("slots")) {
                        navigationItems.put(slot, new MenuItem(stack, action));
                    }
                } else {
                    int slot = itemSec.getInt("slot");
                    navigationItems.put(slot, new MenuItem(stack, action));
                }
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
            String rawName = sec.getString("name", "");
            if (!rawName.isBlank()) {
                meta.setDisplayName(Utils.format("&d&l" + ChatColor.stripColor(Utils.format(rawName))));
            } else {
                meta.setDisplayName(Utils.format(rawName));
            }
            List<String> lore = sec.getStringList("lore").stream()
                    .map(Utils::format)
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
        addStandardItems(player, inv);
        player.openInventory(inv);
        startBorderAnimation(player, inv);
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

    private void addStandardItems(Player player, Inventory inv) {
        int base = inv.getSize() - 9;

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(Utils.format("&c&lRetour"));
            backMeta.setLore(List.of(Utils.format("&7Ferme le menu actuel.")));
            back.setItemMeta(backMeta);
        }
        inv.setItem(base, back);

        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta prevMeta = prev.getItemMeta();
        if (prevMeta != null) {
            prevMeta.setDisplayName(Utils.format("&d&lPage précédente"));
            prevMeta.setLore(List.of(Utils.format("&7Aller à la page précédente")));
            prev.setItemMeta(prevMeta);
        }
        inv.setItem(base + 3, prev);

        ItemStack info = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(Utils.format("&d&lProfil"));
            long coins = plugin.getEconomyManager().getCoins(player.getUniqueId());
            List<String> lore = List.of(
                    Utils.format("&7Grade: &6" + player.getDisplayName()),
                    Utils.format("&7Coins: &b" + coins)
            );
            infoMeta.setLore(lore);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(base + 4, info);

        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nextMeta = next.getItemMeta();
        if (nextMeta != null) {
            nextMeta.setDisplayName(Utils.format("&d&lPage suivante"));
            nextMeta.setLore(List.of(Utils.format("&7Aller à la page suivante")));
            next.setItemMeta(nextMeta);
        }
        inv.setItem(base + 5, next);
    }

    private void startBorderAnimation(Player player, Inventory inv) {
        stopBorderAnimation(player);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                for (int slot = 0; slot < inv.getSize(); slot++) {
                    if (!isBorderSlot(slot, inv.getSize())) {
                        continue;
                    }
                    ItemStack item = inv.getItem(slot);
                    if (item == null) {
                        continue;
                    }
                    if (item.getType() == Material.MAGENTA_STAINED_GLASS_PANE) {
                        inv.setItem(slot, createPane(Material.PURPLE_STAINED_GLASS_PANE));
                    } else if (item.getType() == Material.PURPLE_STAINED_GLASS_PANE) {
                        inv.setItem(slot, createPane(Material.MAGENTA_STAINED_GLASS_PANE));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
        borderTasks.put(player.getUniqueId(), task);
    }

    public void stopBorderAnimation(Player player) {
        BukkitTask task = borderTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private boolean isBorderSlot(int slot, int size) {
        int rows = size / 9;
        int row = slot / 9;
        int col = slot % 9;
        return row == 0 || row == rows - 1 || col == 0 || col == 8;
    }

    private ItemStack createPane(Material mat) {
        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

}

