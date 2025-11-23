package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ConfigManager extends Manager {

    public ConfigManager(HeneriaLobby plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
    }

    @Override
    public void onDisable() {
        // No specific disable logic needed
    }

    /**
     * Retrieves an ItemStack from the config.
     * @param path The path to the item section in config (e.g. "hotbar_items.selector")
     * @param player The player for placeholders (e.g. %player%)
     * @return The constructed ItemStack, or null if invalid.
     */
    public ItemStack getItem(String path, Player player) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path);
        if (section == null) return null;

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
            // Delegate to ItemManager (or HDB API directly if we had access here, but let's use the HDB API wrapper)
            // Ideally, ConfigManager shouldn't depend on ItemManager's logic, but here we need the HDB item.
            // We can ask ItemManager to get it, or we can do it here if we move the HDB logic.
            // Given the architecture, let's assume we try to get it from HDB, and if null, fallback to material.

            // Since ItemManager holds the HDB API instance, we might want to call it.
            // However, to avoid circular dependency if ItemManager uses ConfigManager,
            // let's just use the HDB API if available via the plugin's ItemManager instance.

            item = plugin.getItemManager().getItemFromHDB(hdbId);
            if (item == null && material != null) {
                item = new ItemStack(material);
            } else if (item == null) {
                item = new ItemStack(Material.STONE); // Fallback
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
        }

        return item;
    }

    public int getSlot(String path) {
        return plugin.getConfig().getInt(path + ".slot", -1);
    }

    private Component parseComponent(String text, Player player) {
        if (text == null) return Component.empty();

        // Replace placeholders
        if (player != null) {
            text = text.replace("%player%", player.getName());
        }

        // Check for Legacy colors (simple check)
        // If it has '&', we treat it as legacy.
        // Note: MiniMessage can also use tags.
        // A robust way is: try MiniMessage, if it looks like it failed or we want to support both.
        // The prompt implies we want both.
        // We can use LegacyComponentSerializer.legacyAmpersand().deserialize(text) for legacy.

        // Decision: If it starts with '<' it's likely MiniMessage. If it contains '&' it might be legacy.
        // Or we can just deserialize with Legacy first, then convert to string? No.

        if (text.contains("&")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
        } else {
            return MiniMessage.miniMessage().deserialize(text);
        }
    }
}
