package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager extends Manager {

    private FileConfiguration menusConfig;
    private File menusFile;

    public ConfigManager(HeneriaLobby plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        // Load config.yml
        plugin.saveDefaultConfig();
        plugin.reloadConfig();

        // Load menus.yml
        menusFile = new File(plugin.getDataFolder(), "menus.yml");
        if (!menusFile.exists()) {
            plugin.saveResource("menus.yml", false);
        }
        menusConfig = YamlConfiguration.loadConfiguration(menusFile);
    }

    @Override
    public void onDisable() {
        // No specific disable logic needed
    }

    public FileConfiguration getMenusConfig() {
        if (menusConfig == null) {
             menusConfig = YamlConfiguration.loadConfiguration(menusFile);
        }
        return menusConfig;
    }

    /**
     * Retrieves an ItemStack from the config (works for config.yml sections).
     * @param path The path to the item section in config (e.g. "hotbar_items.selector")
     * @param player The player for placeholders (e.g. %player%)
     * @param itemId The logical ID of the item (e.g. "selector"), if applicable, to be stored in PDC. Can be null.
     * @return The constructed ItemStack, or null if invalid.
     */
    public ItemStack getItem(String path, Player player, String itemId) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return null;
        return buildItemFromSection(section, player, itemId);
    }

    public ItemStack getMenuItem(String menuId, String itemId, Player player) {
        ConfigurationSection section = getMenusConfig().getConfigurationSection("menus." + menuId + ".items." + itemId);
        if (section == null) return null;
        // For menu items, we don't store "item_id" (selector etc.) but we might store the action directly.
        // The logic handles action from config.
        return buildItemFromSection(section, player, null);
    }

    private ItemStack buildItemFromSection(ConfigurationSection section, Player player, String itemId) {
        ItemStack item;
        String hdbId = section.getString("hdb_id");
        boolean usePlayerHead = section.getBoolean("use_player_head", false);
        String materialName = section.getString("material", "STONE");
        Material material = Material.matchMaterial(materialName);

        // 1. Resolve the Base Item
        if (usePlayerHead) {
            item = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) item.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(player);
                item.setItemMeta(meta);
            }
        } else if (hdbId != null && !hdbId.isEmpty()) {
            item = plugin.getItemManager().getItemFromHDB(hdbId);
            if (item == null && material != null) {
                item = new ItemStack(material);
            } else if (item == null) {
                item = new ItemStack(Material.STONE);
            }
        } else {
            if (material == null) material = Material.STONE;
            item = new ItemStack(material);
        }

        // 2. Apply Meta (Name, Lore)
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name");
            if (name != null) {
                meta.displayName(parseComponent(name, player));
            }

            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                meta.lore(lore.stream()
                        .map(line -> parseComponent(line, player))
                        .collect(Collectors.toList()));
            }

            item.setItemMeta(meta);

            // 3. Apply Action / Item ID (PersistentDataContainer)

            // If itemId is provided (mostly for hotbar items), store it.
            if (itemId != null) {
                plugin.getItemManager().addPersistentItemId(item, itemId);
            }

            // If action string is provided (for menu items mostly, but hotbar can have it too implicitly via lookup), store it as action.
            // The requirements say: Hotbar items use ID to lookup action. Menu items use direct Action.
            // But if we can store action directly on hotbar items too, it's not forbidden?
            // However, the prompt says "InteractListener... Vérifie la balise... Exécute l'action associée".
            // And "Mapping des Actions... selector -> Ouvre le menu...".
            // Let's store action if present in config regardless. It's safer.
            // BUT, ticket 2 prompt specifically asked for "heneria_item_id" logic.
            // I will store BOTH if available.

            String action = section.getString("action");
            if (action != null && !action.isEmpty()) {
                plugin.getItemManager().addPersistentAction(item, action);
            }
        }

        return item;
    }

    public int getSlot(String path) {
        return plugin.getConfig().getInt(path + ".slot", -1);
    }

    public Component parseComponent(String text, Player player) {
        if (text == null) return Component.empty();

        if (player != null) {
            text = text.replace("%player%", player.getName());
        }

        if (text.contains("&")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        } else {
            return MiniMessage.miniMessage().deserialize(text);
        }
    }
}
