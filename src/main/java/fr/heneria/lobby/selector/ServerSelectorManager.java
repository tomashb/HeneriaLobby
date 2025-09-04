package fr.heneria.lobby.selector;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import fr.heneria.lobby.LobbyPlugin;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerProfile;
import com.mojang.authlib.properties.Property;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages the server selector GUI and item.
 */
public class ServerSelectorManager implements Listener {

    private final LobbyPlugin plugin;
    private ItemStack selectorItem;
    private int selectorSlot;
    private String menuTitle;
    private int menuSize;
    private final Map<Integer, ItemStack> templates = new HashMap<>();
    private final Map<Integer, String> actions = new HashMap<>();

    public ServerSelectorManager(LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        // Load item from config.yml
        FileConfiguration cfg = plugin.getConfig();
        ConfigurationSection itemSec = cfg.getConfigurationSection("server-selector-item");
        if (itemSec == null) {
            plugin.getLogger().warning("server-selector-item section missing in config.yml");
            return;
        }
        selectorSlot = itemSec.getInt("slot", 0);
        Material mat = Material.matchMaterial(itemSec.getString("material", "PLAYER_HEAD"));
        selectorItem = new ItemStack(mat);
        ItemMeta meta = selectorItem.getItemMeta();
        meta.setDisplayName(color(itemSec.getString("name", "")));
        meta.setLore(itemSec.getStringList("lore").stream().map(this::color).collect(Collectors.toList()));
        meta.setUnbreakable(true);
        if (meta instanceof SkullMeta) {
            String texture = itemSec.getString("texture-value");
            if (texture != null && !texture.isEmpty()) {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.setProperty(new Property("textures", texture));
                ((SkullMeta) meta).setPlayerProfile(profile);
            }
        }
        selectorItem.setItemMeta(meta);

        // Load menu from server-selector.yml
        File file = new File(plugin.getDataFolder(), "server-selector.yml");
        if (!file.exists()) {
            plugin.saveResource("server-selector.yml", false);
        }
        FileConfiguration menuCfg = YamlConfiguration.loadConfiguration(file);
        menuTitle = color(menuCfg.getString("menu-title", "Menu"));
        menuSize = menuCfg.getInt("menu-size", 3) * 9;
        templates.clear();
        actions.clear();
        ConfigurationSection itemsSec = menuCfg.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                if (sec == null) continue;
                int slot = sec.getInt("slot");
                Material material = Material.matchMaterial(sec.getString("material", "STONE"));
                ItemStack item = new ItemStack(material);
                ItemMeta im = item.getItemMeta();
                im.setDisplayName(color(sec.getString("name", "")));
                im.setLore(sec.getStringList("lore").stream().map(this::color).collect(Collectors.toList()));
                item.setItemMeta(im);
                templates.put(slot, item);
                String action = sec.getString("action");
                if (action != null) {
                    actions.put(slot, action);
                }
            }
        }
    }

    public void giveItem(Player player) {
        if (selectorItem != null) {
            player.getInventory().setItem(selectorSlot, selectorItem.clone());
        }
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, menuSize, menuTitle);
        for (Map.Entry<Integer, ItemStack> entry : templates.entrySet()) {
            ItemStack item = entry.getValue().clone();
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) {
                    meta.setDisplayName(applyPlaceholders(player, meta.getDisplayName()));
                }
                if (meta.getLore() != null) {
                    List<String> lore = meta.getLore().stream().map(line -> applyPlaceholders(player, line)).collect(Collectors.toList());
                    meta.setLore(lore);
                }
                item.setItemMeta(meta);
            }
            inv.setItem(entry.getKey(), item);
        }
        player.openInventory(inv);
    }

    private String applyPlaceholders(Player player, String text) {
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private boolean isSelector(ItemStack stack) {
        return stack != null && selectorItem != null && stack.isSimilar(selectorItem);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = event.getItem();
        if (isSelector(item)) {
            event.setCancelled(true);
            open(event.getPlayer());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isSelector(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        ItemStack current = event.getCurrentItem();
        if (isSelector(current)) {
            event.setCancelled(true);
            return;
        }
        if (event.getView().getTitle().equals(menuTitle)) {
            event.setCancelled(true);
            String action = actions.get(event.getSlot());
            if (action != null && action.startsWith("server:")) {
                String server = action.substring("server:".length());
                Player player = (Player) event.getWhoClicked();
                connect(player, server);
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (isSelector(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(this::isSelector)) {
            event.setCancelled(true);
        }
    }

    private void connect(Player player, String server) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("Connect");
        out.writeUTF(server);
        player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
    }
}
