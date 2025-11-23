package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import org.bukkit.entity.Player;

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
            // so the caller can use a fallback item.
            return null;
        }
    }

    public ItemStack getPlayerHead(Player player) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            head.setItemMeta(meta);
        }
        return head;
    }
}
