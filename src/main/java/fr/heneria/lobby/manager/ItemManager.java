package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import me.arcaniax.hdb.api.HeadDatabaseAPI;

public class ItemManager extends Manager {

    private HeadDatabaseAPI hdbApi;
    public static final NamespacedKey ACTION_KEY = new NamespacedKey("heneria", "action");
    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("heneria", "item_id");

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
            return null;
        }
    }

    public ItemStack getConfigItem(String key, Player player) {
        return plugin.getConfigManager().getItem("hotbar_items." + key, player, key);
    }

    public int getConfigSlot(String key) {
        return plugin.getConfigManager().getSlot("hotbar_items." + key);
    }

    public void addPersistentAction(ItemStack item, String action) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ACTION_KEY, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
    }

    public void addPersistentItemId(ItemStack item, String id) {
        if (item == null || item.getItemMeta() == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
    }

    public String getPersistentAction(ItemStack item) {
         if (item == null || item.getItemMeta() == null) return null;
         return item.getItemMeta().getPersistentDataContainer().get(ACTION_KEY, PersistentDataType.STRING);
    }

    public String getPersistentItemId(ItemStack item) {
         if (item == null || item.getItemMeta() == null) return null;
         return item.getItemMeta().getPersistentDataContainer().get(ITEM_ID_KEY, PersistentDataType.STRING);
    }
}
