package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import me.arcaniax.hdb.api.HeadDatabaseAPI;

public class ItemManager extends Manager {

    private HeadDatabaseAPI hdbApi;
    public static final NamespacedKey ACTION_KEY = new NamespacedKey("heneria", "action");

    public ItemManager(HeneriaLobby plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        this.hdbApi = new HeadDatabaseAPI();
    }

    @Override
    public void onDisable() {
        // Cleanup if necessary
    }

    public ItemStack getItemFromHDB(String id) {
        try {
            return hdbApi.getItemHead(id);
        } catch (Exception e) {
            // If the head is not found or API is not ready, we return null
            return null;
        }
    }

    public ItemStack getConfigItem(String key, Player player) {
        return plugin.getConfigManager().getItem("hotbar_items." + key, player);
    }

    public int getConfigSlot(String key) {
        return plugin.getConfigManager().getSlot("hotbar_items." + key);
    }

    public void addPersistentMeta(ItemStack item, String key, String value) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        // For now we only use one key "action", but we could have dynamic keys.
        // However, NamespacedKey requires plugin instance usually or fixed string.
        // Let's support "action" specifically as requested.
        if (key.equals("action")) {
            meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, value);
            item.setItemMeta(meta);
        }
    }

    public String getPersistentMeta(ItemStack item) {
         if (item == null || item.getItemMeta() == null) return null;
         return item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
    }
}
