package com.heneria.lobby.cosmetics;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

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
                ChatColor.GOLD + "" + ChatColor.BOLD + capitalize(category));
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            Cosmetic c = list.get(start + i);
            ItemStack item = new ItemStack(c.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                UUID uuid = player.getUniqueId();
                boolean has = owned.getOrDefault(uuid, Collections.emptySet()).contains(c.getId());
                String equippedId = equipped.getOrDefault(uuid, Collections.emptyMap()).get(category);
                boolean isEquipped = c.getId().equals(equippedId);

                String baseName = ChatColor.stripColor(c.getName());
                List<String> lore = new ArrayList<>(c.getLore());
                lore.add(ChatColor.DARK_GRAY + "-------------------------");
                lore.add(ChatColor.WHITE + "Rareté : " + ChatColor.RED + c.getRarity());

                if (!has) {
                    long coins = economyManager.getCoins(uuid);
                    lore.add(ChatColor.WHITE + "Prix : " + ChatColor.GOLD + c.getPrice() + " Coins");
                    lore.add(ChatColor.WHITE + "Votre solde : " + ChatColor.GOLD + coins + " Coins");
                    lore.add("" );
                    lore.add(ChatColor.RED + "" + ChatColor.BOLD + "BLOQUÉ");
                    lore.add(ChatColor.YELLOW + "► Cliquez pour acheter");
                    meta.setDisplayName(ChatColor.RED + baseName);
                } else if (isEquipped) {
                    lore.add("" );
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "ÉQUIPÉ");
                    lore.add(ChatColor.RED + "► Cliquez pour déséquiper");
                    meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + baseName + ChatColor.YELLOW + " (Équipé)");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add("" );
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "DÉBLOQUÉ");
                    lore.add(ChatColor.YELLOW + "► Cliquez pour équiper");
                    meta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + baseName);
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                lore.add(ChatColor.DARK_GRAY + "-------------------------");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_id"),
                        PersistentDataType.STRING, c.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        // back to shop
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Retour");
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
        String category = openCategory.get(uuid);
        if (category == null) {
            return;
        }
        event.setCancelled(true);
        int slot = event.getRawSlot();
        int page = openPage.getOrDefault(uuid, 0);
        if (slot < 45) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) {
                return;
            }
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) {
                return;
            }
            String id = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "cosmetic_id"),
                    PersistentDataType.STRING);
            if (id == null) {
                return;
            }
            Cosmetic cosmetic = cosmetics.getOrDefault(category, Collections.emptyList()).stream()
                    .filter(c -> c.getId().equals(id))
                    .findFirst().orElse(null);
            if (cosmetic == null) {
                return;
            }
            Set<String> ownedSet = owned.computeIfAbsent(uuid, k -> new HashSet<>());
            Map<String, String> eq = equipped.computeIfAbsent(uuid, k -> new HashMap<>());
            boolean has = ownedSet.contains(cosmetic.getId());
            String current = eq.get(category);
            boolean isEquipped = cosmetic.getId().equals(current);
            if (!has) {
                long coins = economyManager.getCoins(uuid);
                if (coins < cosmetic.getPrice()) {
                    player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Vous n'avez pas assez de Coins !");
                    return;
                }
                economyManager.addCoins(uuid, -cosmetic.getPrice());
                ownedSet.add(cosmetic.getId());
                player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY +
                        "Vous avez débloqué le cosmétique : " + ChatColor.YELLOW + cosmetic.getName());
            } else if (!isEquipped) {
                eq.put(category, cosmetic.getId());
                if (category.equalsIgnoreCase("hats")) {
                    player.getInventory().setHelmet(new ItemStack(cosmetic.getMaterial()));
                }
                player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY +
                        "Vous avez équipé le cosmétique : " + ChatColor.YELLOW + cosmetic.getName());
            } else {
                eq.remove(category);
                if (category.equalsIgnoreCase("hats")) {
                    player.getInventory().setHelmet(null);
                }
                player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY +
                        "Vous avez déséquipé le cosmétique : " + ChatColor.YELLOW + cosmetic.getName());
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
