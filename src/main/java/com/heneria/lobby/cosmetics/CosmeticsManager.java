package com.heneria.lobby.cosmetics;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages cosmetic items, their menus and purchase/equip logic.
 */
public class CosmeticsManager implements Listener {

    private final HeneriaLobbyPlugin plugin;
    private final EconomyManager economyManager;

    private final Map<String, List<Cosmetic>> cosmetics = new HashMap<>();
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, Map<String, String>> equipped = new HashMap<>();
    private final Map<UUID, Map<Integer, Cosmetic>> openMenus = new HashMap<>();
    private final Map<UUID, String> openCategory = new HashMap<>();
    private final Map<UUID, Integer> openPage = new HashMap<>();

    public CosmeticsManager(HeneriaLobbyPlugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        loadConfig();
    }

    /**
     * Load cosmetics from configuration file.
     */
    public void loadConfig() {
        plugin.saveResource("cosmetics.yml", false);
        File file = new File(plugin.getDataFolder(), "cosmetics.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        cosmetics.clear();
        ConfigurationSection root = config.getConfigurationSection("cosmetics");
        if (root != null) {
            for (String category : root.getKeys(false)) {
                ConfigurationSection catSec = root.getConfigurationSection(category);
                if (catSec == null) continue;
                List<Cosmetic> list = new ArrayList<>();
                for (String id : catSec.getKeys(false)) {
                    ConfigurationSection cSec = catSec.getConfigurationSection(id);
                    if (cSec == null) continue;
                    String name = color(cSec.getString("name", id));
                    List<String> lore = cSec.getStringList("lore").stream()
                            .map(this::color)
                            .collect(Collectors.toList());
                    Material mat = Material.matchMaterial(cSec.getString("material", "STONE"));
                    if (mat == null) {
                        mat = Material.STONE;
                    }
                    String rarity = cSec.getString("rarity", "COMMUN");
                    int price = cSec.getInt("price", 0);
                    String text = color(cSec.getString("text", ""));
                    list.add(new Cosmetic(id, name, lore, mat, rarity, price, category, text));
                }
                cosmetics.put(category, list);
            }
        }
    }

    /**
     * Open a menu for the specified category.
     *
     * @return true if a menu was opened
     */
    public boolean openCategoryMenu(Player player, String category) {
        return openCategoryMenu(player, category, 0);
    }

    private boolean openCategoryMenu(Player player, String category, int page) {
        List<Cosmetic> list = cosmetics.get(category);
        if (list == null) {
            return false;
        }
        int perPage = 45;
        int maxPage = (list.size() + perPage - 1) / perPage;
        if (maxPage == 0) {
            maxPage = 1;
        }
        if (page < 0) {
            page = 0;
        }
        if (page >= maxPage) {
            page = maxPage - 1;
        }
        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.DARK_PURPLE + capitalize(category) + " " + (page + 1) + "/" + maxPage);
        Map<Integer, Cosmetic> map = new HashMap<>();
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            Cosmetic c = list.get(start + i);
            ItemStack item = new ItemStack(c.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(c.getName());
                List<String> lore = new ArrayList<>(c.getLore());
                lore.add("");
                boolean has = owned.getOrDefault(player.getUniqueId(), Collections.emptySet()).contains(c.getId());
                String equippedId = equipped.getOrDefault(player.getUniqueId(), Collections.emptyMap()).get(category);
                boolean isEquipped = c.getId().equals(equippedId);
                if (has) {
                    lore.add(ChatColor.GREEN + "Débloqué");
                    lore.add(isEquipped ? ChatColor.YELLOW + "Cliquez pour déséquiper" : ChatColor.YELLOW + "Cliquez pour équiper");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add(ChatColor.GRAY + c.getRarity());
                    lore.add(ChatColor.GOLD + String.valueOf(c.getPrice()) + " Coins");
                    lore.add(ChatColor.YELLOW + "Cliquez pour acheter");
                }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
            map.put(i, c);
        }
        // back to shop
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "Retour");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);
        // navigation
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta pm = prev.getItemMeta();
            if (pm != null) {
                pm.setDisplayName(ChatColor.YELLOW + "Page précédente");
                prev.setItemMeta(pm);
            }
            inv.setItem(45, prev);
        }
        if (page < maxPage - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nm = next.getItemMeta();
            if (nm != null) {
                nm.setDisplayName(ChatColor.YELLOW + "Page suivante");
                next.setItemMeta(nm);
            }
            inv.setItem(53, next);
        }
        openMenus.put(player.getUniqueId(), map);
        openCategory.put(player.getUniqueId(), category);
        openPage.put(player.getUniqueId(), page);
        player.openInventory(inv);
        return true;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<Integer, Cosmetic> map = openMenus.get(uuid);
        if (map == null) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        String category = openCategory.get(uuid);
        int page = openPage.getOrDefault(uuid, 0);
        if (slot < 45) {
            Cosmetic cosmetic = map.get(slot);
            if (cosmetic == null) {
                return;
            }
            Set<String> ownedSet = owned.computeIfAbsent(uuid, k -> new HashSet<>());
            if (!ownedSet.contains(cosmetic.getId())) {
                long coins = economyManager.getCoins(uuid);
                if (coins < cosmetic.getPrice()) {
                    player.sendMessage(ChatColor.RED + "Vous n'avez pas assez de Coins.");
                    return;
                }
                economyManager.addCoins(uuid, -cosmetic.getPrice());
                ownedSet.add(cosmetic.getId());
                player.sendMessage(ChatColor.GREEN + "Vous avez acheté " + cosmetic.getName());
            } else {
                Map<String, String> eq = equipped.computeIfAbsent(uuid, k -> new HashMap<>());
                String current = eq.get(category);
                if (cosmetic.getId().equals(current)) {
                    eq.remove(category);
                    if (category.equalsIgnoreCase("hats")) {
                        player.getInventory().setHelmet(null);
                    }
                    player.sendMessage(ChatColor.YELLOW + "Cosmétique retiré.");
                } else {
                    eq.put(category, cosmetic.getId());
                    if (category.equalsIgnoreCase("hats")) {
                        player.getInventory().setHelmet(new ItemStack(cosmetic.getMaterial()));
                    }
                    player.sendMessage(ChatColor.YELLOW + "Cosmétique équipé.");
                }
            }
            openCategoryMenu(player, category, page);
        } else if (slot == 45) {
            openCategoryMenu(player, category, page - 1);
        } else if (slot == 53) {
            openCategoryMenu(player, category, page + 1);
        } else if (slot == 49) {
            player.closeInventory();
            plugin.getGuiManager().openMenu(player, "shop");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openMenus.remove(uuid);
        openCategory.remove(uuid);
        openPage.remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        owned.putIfAbsent(event.getPlayer().getUniqueId(), new HashSet<>());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openMenus.remove(uuid);
        openCategory.remove(uuid);
        openPage.remove(uuid);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
