package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import me.arcaniax.hdb.api.HeadDatabaseAPI;

public class ItemManager extends Manager {

    private HeadDatabaseAPI hdbApi;

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

    // Helper method to get item via ConfigManager for a specific key
    public ItemStack getConfigItem(String key, Player player) {
        return plugin.getConfigManager().getItem("hotbar_items." + key, player);
    }

    // Helper to get slot
    public int getConfigSlot(String key) {
        return plugin.getConfigManager().getSlot("hotbar_items." + key);
    }
}
