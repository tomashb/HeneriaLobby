package com.heneria.lobby.cosmetics;

import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.database.DatabaseManager;
import com.heneria.lobby.economy.EconomyManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display;
import org.bukkit.attribute.Attribute;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;


import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages cosmetic items, their menus and purchase/equip logic.
 */
public class CosmeticsManager implements Listener {

    private final HeneriaLobbyPlugin plugin;
    private final EconomyManager economyManager;
    private final DatabaseManager databaseManager;

    private final Map<String, List<Cosmetic>> cosmetics = new HashMap<>();
    private final Map<String, Rarity> rarities = new HashMap<>();
    private final Map<UUID, Set<String>> owned = new HashMap<>();
    private final Map<UUID, Map<String, String>> equipped = new HashMap<>();
    private final Map<UUID, String> openCategory = new HashMap<>();
    private final Map<UUID, Integer> openPage = new HashMap<>();
    private final Map<UUID, ItemStack> savedHelmets = new HashMap<>();
    private final Map<UUID, BukkitTask> particleTasks = new HashMap<>();
    private final Map<UUID, TextDisplay> titleDisplays = new HashMap<>();
    private final Map<UUID, Entity> pets = new HashMap<>();
    private final Map<UUID, BukkitTask> petTasks = new HashMap<>();
    private final Map<UUID, Entity> balloons = new HashMap<>();
    private final Map<UUID, Entity> balloonHolders = new HashMap<>();

    private final double petSpeedMultiplier;

    private static final String OWNED_KEY = "unlocked";
    private static final String OWNED_TITLE = ChatColor.GREEN + "" + ChatColor.BOLD + "Mes Cosmétiques";

    public CosmeticsManager(HeneriaLobbyPlugin plugin, EconomyManager economyManager,
                             DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.databaseManager = databaseManager;
        loadConfig();
        this.petSpeedMultiplier = plugin.getConfig().getDouble("pets.speed-multiplier", 1.5);
    }

    /**
     * Load cosmetics from configuration file.
     */
    public void loadConfig() {
        plugin.saveResource("cosmetics.yml", false);
        plugin.saveResource("rarities.yml", false);

        File file = new File(plugin.getDataFolder(), "cosmetics.yml");
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);

        File rarFile = new File(plugin.getDataFolder(), "rarities.yml");
        FileConfiguration rarCfg = YamlConfiguration.loadConfiguration(rarFile);

        rarities.clear();
        ConfigurationSection rarSec = rarCfg.getConfigurationSection("rarities");
        if (rarSec != null) {
            for (String key : rarSec.getKeys(false)) {
                ConfigurationSection r = rarSec.getConfigurationSection(key);
                if (r == null) continue;
                String name = color(r.getString("name", key));
                String color = color(r.getString("color", ""));
                String stars = color(r.getString("stars", ""));
                rarities.put(key.toUpperCase(), new Rarity(name, color, stars));
            }
        }

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
                    String text = color(cSec.getString("text", cSec.getString("format", "")));
                    Particle particle = null;
                    int particleCount = cSec.getInt("count", 1);
                    double particleOffset = cSec.getDouble("offset", 0.0);
                    String particleName = cSec.getString("particle");
                    if (particleName != null) {
                        try {
                            particle = Particle.valueOf(particleName.toUpperCase());
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    list.add(new Cosmetic(id, name, lore, mat, rarity, price, category, text,
                            particle, particleCount, particleOffset));
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

    public boolean openCategoryMenu(Player player, String category, int page) {
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
                Rarity r = getRarity(c.getRarity());
                String rarityColor = r.getColor();
                List<String> lore = new ArrayList<>(c.getLore());
                lore.add(ChatColor.DARK_GRAY + "-------------------------");
                lore.add(ChatColor.WHITE + "Rareté : " + rarityColor + r.getName() + " " + r.getStars());

                String coloredName = rarityColor + ChatColor.BOLD.toString() + baseName;

                if (!has) {
                    long coins = economyManager.getCoins(uuid);
                    lore.add(ChatColor.WHITE + "Prix : " + ChatColor.GOLD + c.getPrice() + " Coins");
                    lore.add(ChatColor.WHITE + "Votre solde : " + ChatColor.GOLD + coins + " Coins");
                    lore.add("");
                    lore.add(ChatColor.RED + "" + ChatColor.BOLD + "BLOQUÉ");
                    lore.add(ChatColor.YELLOW + "► Cliquez pour acheter");
                    meta.setDisplayName(coloredName);
                } else if (isEquipped) {
                    lore.add("");
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "ÉQUIPÉ");
                    lore.add(ChatColor.RED + "► Cliquez pour déséquiper");
                    meta.setDisplayName(coloredName + ChatColor.YELLOW + " (Équipé)");
                    meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                } else {
                    lore.add("");
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "DÉBLOQUÉ");
                    lore.add(ChatColor.YELLOW + "► Cliquez pour équiper");
                    meta.setDisplayName(coloredName);
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

    public void openOwnedMenu(Player player) {
        openOwnedMenu(player, 0);
    }

    public void openOwnedMenu(Player player, int page) {
        UUID uuid = player.getUniqueId();
        Set<String> ownedIds = loadOwnedCosmetics(uuid);
        owned.put(uuid, ownedIds);
        List<Cosmetic> list = ownedIds.stream()
                .map(this::getCosmeticById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
        Inventory inv = Bukkit.createInventory(null, 54, OWNED_TITLE);
        Map<String, String> equippedMap = equipped.getOrDefault(uuid, Collections.emptyMap());
        int start = page * perPage;
        for (int i = 0; i < perPage && start + i < list.size(); i++) {
            Cosmetic c = list.get(start + i);
            ItemStack item = new ItemStack(c.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                String baseName = ChatColor.stripColor(c.getName());
                String equippedId = equippedMap.get(c.getCategory());
                boolean isEquipped = c.getId().equals(equippedId);
                Rarity r = getRarity(c.getRarity());
                String rarityColor = r.getColor();
                List<String> lore = new ArrayList<>(c.getLore());
                lore.add(ChatColor.DARK_GRAY + "-------------------------");
                lore.add(ChatColor.WHITE + "Rareté : " + rarityColor + r.getName() + " " + r.getStars());
                lore.add("");
                String coloredName = rarityColor + ChatColor.BOLD.toString() + baseName;
                if (isEquipped) {
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "ÉQUIPÉ");
                    lore.add(ChatColor.RED + "► Cliquez pour déséquiper");
                    meta.setDisplayName(coloredName + ChatColor.YELLOW + " (Équipé)");
                } else {
                    lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "DÉBLOQUÉ");
                    lore.add(ChatColor.YELLOW + "► Cliquez pour équiper");
                    meta.setDisplayName(coloredName);
                }
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                lore.add(ChatColor.DARK_GRAY + "-------------------------");
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_id"),
                        PersistentDataType.STRING, c.getId());
                item.setItemMeta(meta);
            }
            inv.setItem(i, item);
        }
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ChatColor.RED + "" + ChatColor.BOLD + "Retour");
            back.setItemMeta(backMeta);
        }
        inv.setItem(49, back);
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
        openCategory.put(uuid, OWNED_KEY);
        openPage.put(uuid, page);
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        openCategory.remove(uuid);
        openPage.remove(uuid);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        owned.put(uuid, loadOwnedCosmetics(uuid));
        Map<String, String> equippedMap = loadEquippedCosmetics(uuid);
        equipped.put(uuid, equippedMap);
        // Reapply equipped cosmetics on join
        for (String cosmeticId : equippedMap.values()) {
            Cosmetic c = getCosmeticById(cosmeticId);
            if (c != null) {
                applyCosmeticEffect(player, c);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Map<String, String> eq = equipped.remove(uuid);
        if (eq != null) {
            for (String category : eq.keySet()) {
                removeCosmeticEffect(event.getPlayer(), category);
            }
        }
        owned.remove(uuid);
        openCategory.remove(uuid);
        openPage.remove(uuid);
        savedHelmets.remove(uuid);
        BukkitTask task = particleTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        TextDisplay display = titleDisplays.remove(uuid);
        if (display != null) {
            display.remove();
        }
        BukkitTask petTask = petTasks.remove(uuid);
        if (petTask != null) {
            petTask.cancel();
        }
        Entity pet = pets.remove(uuid);
        if (pet != null) {
            pet.remove();
        }
        Entity balloon = balloons.remove(uuid);
        if (balloon != null) {
            balloon.remove();
        }
        Entity holder = balloonHolders.remove(uuid);
        if (holder != null) {
            holder.remove();
        }
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

    private Rarity getRarity(String key) {
        return rarities.getOrDefault(key.toUpperCase(), new Rarity(key, "", ""));
    }

    public boolean isCosmeticMenu(String title) {
        if (OWNED_TITLE.equals(title)) {
            return true;
        }
        for (String category : cosmetics.keySet()) {
            if ((ChatColor.GOLD + "" + ChatColor.BOLD + capitalize(category)).equals(title)) {
                return true;
            }
        }
        return false;
    }

    public void openNextPage(Player player) {
        UUID uuid = player.getUniqueId();
        String category = openCategory.get(uuid);
        if (category != null) {
            int page = openPage.getOrDefault(uuid, 0);
            if (OWNED_KEY.equals(category)) {
                openOwnedMenu(player, page + 1);
            } else {
                openCategoryMenu(player, category, page + 1);
            }
        }
    }

    public void openPreviousPage(Player player) {
        UUID uuid = player.getUniqueId();
        String category = openCategory.get(uuid);
        if (category != null) {
            int page = openPage.getOrDefault(uuid, 0);
            if (OWNED_KEY.equals(category)) {
                openOwnedMenu(player, page - 1);
            } else {
                openCategoryMenu(player, category, page - 1);
            }
        }
    }

    public boolean isOwned(Player player, String cosmeticId) {
        UUID uuid = player.getUniqueId();
        return owned.computeIfAbsent(uuid, k -> new HashSet<>()).contains(cosmeticId);
    }

    public boolean isEquipped(Player player, String cosmeticId) {
        Cosmetic cosmetic = getCosmeticById(cosmeticId);
        if (cosmetic == null) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        Map<String, String> equippedMap = equipped.get(uuid);
        if (equippedMap == null) {
            return false;
        }
        return cosmeticId.equals(equippedMap.get(cosmetic.getCategory()));
    }

    public String getEquippedCosmeticId(Player player, String category) {
        Map<String, String> equippedMap = equipped.get(player.getUniqueId());
        if (equippedMap == null) {
            return null;
        }
        return equippedMap.get(category);
    }

    public void equipCosmetic(Player player, String cosmeticId) {
        Cosmetic cosmetic = getCosmeticById(cosmeticId);
        if (cosmetic == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> ownedSet = owned.computeIfAbsent(uuid, k -> new HashSet<>());
        if (!ownedSet.contains(cosmeticId)) {
            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Cosmétique non débloqué.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        Map<String, String> equippedMap = equipped.computeIfAbsent(uuid, k -> new HashMap<>());
        String category = cosmetic.getCategory();
        String current = equippedMap.get(category);
        if (current != null && !current.equals(cosmeticId)) {
            removeCosmeticEffect(player, category);
        }
        applyCosmeticEffect(player, cosmetic);
        equippedMap.put(category, cosmeticId);
        saveEquipped(uuid, category, cosmeticId);
        player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY + "Vous avez équipé : " + ChatColor.YELLOW + cosmetic.getName());
    }

    public void purchaseCosmetic(Player player, String cosmeticId) {
        Cosmetic cosmetic = getCosmeticById(cosmeticId);
        if (cosmetic == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> ownedSet = owned.computeIfAbsent(uuid, k -> new HashSet<>());
        if (ownedSet.contains(cosmeticId)) {
            return;
        }
        int price = cosmetic.getPrice();
        if (!economyManager.hasEnoughCoins(player, price)) {
            long missing = price - economyManager.getCoins(uuid);
            player.sendMessage(ChatColor.RED + "✖ " + ChatColor.GRAY + "Fonds insuffisants. Il vous manque " +
                    ChatColor.GOLD + missing + ChatColor.GRAY + " Coins.");
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        economyManager.removeCoins(player, price);
        ownedSet.add(cosmeticId);
        saveCosmetic(uuid, cosmeticId);
        player.sendMessage(ChatColor.GREEN + "✔ " + ChatColor.GRAY +
                "Vous avez débloqué : " + ChatColor.YELLOW + cosmetic.getName());
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    public void unequipCosmetic(Player player, String cosmeticId) {
        Cosmetic cosmetic = getCosmeticById(cosmeticId);
        if (cosmetic == null) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Map<String, String> equippedMap = equipped.get(uuid);
        if (equippedMap == null) {
            return;
        }
        String category = cosmetic.getCategory();
        String current = equippedMap.get(category);
        if (!cosmeticId.equals(current)) {
            return;
        }
        removeCosmeticEffect(player, category);
        equippedMap.remove(category);
        deleteEquipped(uuid, category);
        player.sendMessage(ChatColor.RED + "✔ " + ChatColor.GRAY + "Vous avez déséquipé : " + ChatColor.YELLOW + cosmetic.getName());
    }

    public void refreshMenu(Player player) {
        UUID uuid = player.getUniqueId();
        int page = openPage.getOrDefault(uuid, 0);
        String open = openCategory.get(uuid);
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (OWNED_KEY.equals(open)) {
                openOwnedMenu(player, page);
            } else if (open != null) {
                openCategoryMenu(player, open, page);
            }
        });
    }

    public Cosmetic getCosmeticById(String id) {
        for (List<Cosmetic> list : cosmetics.values()) {
            for (Cosmetic c : list) {
                if (c.getId().equals(id)) {
                    return c;
                }
            }
        }
        return null;
    }

    private void saveCosmetic(UUID uuid, String id) {
        try (java.sql.Connection connection = databaseManager.getConnection();
             java.sql.PreparedStatement ps = connection.prepareStatement(
                     "INSERT INTO player_cosmetics (player_uuid, cosmetic_id) VALUES (?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, id);
            ps.executeUpdate();
        } catch (java.sql.SQLException e) {
            plugin.getLogger().severe("Failed to save cosmetic purchase: " + e.getMessage());
        }
    }

    private void saveEquipped(UUID uuid, String category, String cosmeticId) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "REPLACE INTO player_equipped_cosmetics (player_uuid, category, cosmetic_id) VALUES (?, ?, ?)")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category);
            ps.setString(3, cosmeticId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save equipped cosmetic: " + e.getMessage());
        }
    }

    private void deleteEquipped(UUID uuid, String category) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM player_equipped_cosmetics WHERE player_uuid=? AND category=?")) {
            ps.setString(1, uuid.toString());
            ps.setString(2, category);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete equipped cosmetic: " + e.getMessage());
        }
    }

    private Set<String> loadOwnedCosmetics(UUID uuid) {
        Set<String> set = new HashSet<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT cosmetic_id FROM player_cosmetics WHERE player_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    set.add(rs.getString("cosmetic_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load cosmetics: " + e.getMessage());
        }
        return set;
    }

    private Map<String, String> loadEquippedCosmetics(UUID uuid) {
        Map<String, String> map = new HashMap<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT category, cosmetic_id FROM player_equipped_cosmetics WHERE player_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("category"), rs.getString("cosmetic_id"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load equipped cosmetics: " + e.getMessage());
        }
        return map;
    }

    public void clearCosmetics(UUID uuid) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "DELETE FROM player_cosmetics WHERE player_uuid=?");
             PreparedStatement ps2 = connection.prepareStatement(
                     "DELETE FROM player_equipped_cosmetics WHERE player_uuid=?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            ps2.setString(1, uuid.toString());
            ps2.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to clear cosmetics: " + e.getMessage());
        }

        owned.remove(uuid);
        Map<String, String> eq = equipped.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && eq != null) {
            for (String category : eq.keySet()) {
                removeCosmeticEffect(player, category);
            }
        }
        savedHelmets.remove(uuid);
        BukkitTask task = particleTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        TextDisplay display = titleDisplays.remove(uuid);
        if (display != null) {
            display.remove();
        }
        BukkitTask petTask = petTasks.remove(uuid);
        if (petTask != null) {
            petTask.cancel();
        }
        Entity pet = pets.remove(uuid);
        if (pet != null) {
            pet.remove();
        }
    }

    public void clearCosmetics(Player player) {
        clearCosmetics(player.getUniqueId());
    }

    private void applyCosmeticEffect(Player player, Cosmetic cosmetic) {
        String category = cosmetic.getCategory().toLowerCase();
        switch (category) {
            case "hats" -> equipHat(player, cosmetic);
            case "particles" -> activateParticles(player, cosmetic);
            case "titles" -> showTitle(player, cosmetic);
            case "pets" -> equipPet(player, cosmetic);
            case "balloons" -> equipBalloon(player, cosmetic);
        }
    }

    private void removeCosmeticEffect(Player player, String category) {
        switch (category.toLowerCase()) {
            case "hats" -> unequipHat(player);
            case "particles" -> deactivateParticles(player);
            case "titles" -> hideTitle(player);
            case "pets" -> unequipPet(player);
            case "balloons" -> unequipBalloon(player);
        }
    }

    private void equipHat(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        ItemStack current = player.getInventory().getHelmet();
        if (current != null && current.getType() != Material.AIR) {
            savedHelmets.put(uuid, current.clone());
        }
        ItemStack hat = new ItemStack(cosmetic.getMaterial());
        ItemMeta meta = hat.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic_hat"),
                    PersistentDataType.STRING, cosmetic.getId());
            hat.setItemMeta(meta);
        }
        player.getInventory().setHelmet(hat);
    }

    private void unequipHat(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack previous = savedHelmets.remove(uuid);
        if (previous != null && previous.getType() != Material.AIR) {
            player.getInventory().setHelmet(previous);
        } else {
            player.getInventory().setHelmet(null);
        }
    }

    private void activateParticles(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        deactivateParticles(player);
        Particle particle = cosmetic.getParticle() != null ? cosmetic.getParticle() : Particle.FLAME;
        int count = Math.max(1, cosmetic.getParticleCount());
        double offset = cosmetic.getParticleOffset();
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), count,
                        offset, offset, offset, 0);
            }
        }.runTaskTimer(plugin, 0L, 5L);
        particleTasks.put(uuid, task);
    }

    private void deactivateParticles(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = particleTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    private void equipPet(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        unequipPet(player);
        String id = cosmetic.getId();

        EntityType type;
        boolean rainCloud = false;
        boolean soul = false;
        boolean fairy = false;
        switch (id) {
            case "pet_fairy" -> {
                type = EntityType.BAT;
                fairy = true;
            }
            case "pet_rain_cloud" -> {
                type = EntityType.SHEEP;
                rainCloud = true;
            }
            case "pet_soul" -> {
                type = EntityType.VEX;
                soul = true;
            }
            default -> {
                String typeName = id.substring(id.indexOf('_') + 1).toUpperCase(Locale.ROOT);
                try {
                    type = EntityType.valueOf(typeName);
                } catch (IllegalArgumentException e) {
                    return;
                }
            }
        }

        Entity entity = player.getWorld().spawnEntity(player.getLocation(), type);
        entity.setInvulnerable(true);
        entity.setSilent(true);
        entity.setPersistent(false);
        if (entity instanceof Sheep sheep && rainCloud) {
            sheep.setCustomName("jeb_");
            sheep.setCustomNameVisible(false);
        }
        if (entity instanceof Ageable ageable) {
            ageable.setBaby();
        }

        if (entity instanceof Mob mob) {
            var attack = mob.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
            if (attack != null) {
                attack.setBaseValue(0);
            }
            mob.setTarget(null);
            var speed = mob.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(speed.getBaseValue() * petSpeedMultiplier);
            }
            BukkitTask task = new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline() || !mob.isValid()) {
                        cancel();
                        return;
                    }
                    mob.getPathfinder().moveTo(player);
                    if (rainCloud) {
                        player.getWorld().spawnParticle(Particle.DRIPPING_WATER,
                                mob.getLocation().clone().add(0, -0.5, 0), 5, 0.3, 0, 0.3, 0);
                    } else if (soul) {
                        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                                mob.getLocation(), 3, 0.2, 0.2, 0.2, 0);
                    } else if (fairy) {
                        player.getWorld().spawnParticle(Particle.END_ROD,
                                mob.getLocation(), 1, 0, 0, 0, 0);
                    }
                }
            }.runTaskTimer(plugin, 0L, 10L);
            petTasks.put(uuid, task);
        }
        pets.put(uuid, entity);
    }

    private void unequipPet(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = petTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        Entity entity = pets.remove(uuid);
        if (entity != null) {
            entity.remove();
        }
    }

    private void equipBalloon(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        unequipBalloon(player);

        Rabbit holder = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Rabbit.class, r -> {
            r.setSilent(true);
            r.setInvulnerable(true);
            r.setPersistent(false);
            r.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
        });
        player.addPassenger(holder);

        Entity balloon = player.getWorld().spawn(player.getLocation().add(0, 2, 0), EntityType.CHICKEN.getEntityClass());
        if (balloon instanceof LivingEntity living) {
            living.setSilent(true);
            living.setInvulnerable(true);
            living.setPersistent(false);
            living.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 1, false, false));
            ItemStack itemStack = new ItemStack(cosmetic.getMaterial());
            living.getEquipment().setHelmet(itemStack);
            living.setLeashHolder(holder);
        }
        balloons.put(uuid, balloon);
        balloonHolders.put(uuid, holder);
    }

    private void unequipBalloon(Player player) {
        UUID uuid = player.getUniqueId();
        Entity balloon = balloons.remove(uuid);
        if (balloon != null) {
            balloon.remove();
        }
        Entity holder = balloonHolders.remove(uuid);
        if (holder != null) {
            holder.remove();
        }
    }

    private void showTitle(Player player, Cosmetic cosmetic) {
        UUID uuid = player.getUniqueId();
        hideTitle(player);
        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class, td -> {
            td.text(Component.text(color(cosmetic.getText())));
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setInvisible(player.isInvisible() || player.isSneaking());
        });
        Transformation transformation = display.getTransformation();
        display.setTransformation(new Transformation(
                transformation.getTranslation().add(new Vector3f(0f, 0.35f, 0f)),
                transformation.getLeftRotation(),
                transformation.getScale(),
                transformation.getRightRotation()));
        player.addPassenger(display);
        titleDisplays.put(uuid, display);
    }

    private void hideTitle(Player player) {
        UUID uuid = player.getUniqueId();
        TextDisplay display = titleDisplays.remove(uuid);
        if (display != null) {
            display.remove();
        }
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        TextDisplay display = titleDisplays.get(player.getUniqueId());
        if (display != null) {
            boolean invisible = event.isSneaking() || player.hasPotionEffect(PotionEffectType.INVISIBILITY);
            display.setInvisible(invisible);
        }
    }

    @EventHandler
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        TextDisplay display = titleDisplays.get(player.getUniqueId());
        if (display != null) {
            boolean invisible = player.hasPotionEffect(PotionEffectType.INVISIBILITY) || player.isSneaking();
            display.setInvisible(invisible);
        }
    }
}
