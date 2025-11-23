package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import fr.heneria.lobby.manager.ItemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.arcaniax.hdb.api.DatabaseLoadEvent;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerJoinListener implements Listener {

    private final HeneriaLobby plugin;

    public PlayerJoinListener(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        giveLobbyItems(event.getPlayer());
    }

    @EventHandler
    public void onDatabaseLoad(DatabaseLoadEvent event) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            giveLobbyItems(player);
        }
    }

    private void giveLobbyItems(Player player) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) return;

        player.getInventory().clear();

        // Selector
        setItem(player, "selector");

        // Profile
        setItem(player, "profile");

        // Main Menu
        setItem(player, "games_menu");

        // Cosmetics
        setItem(player, "cosmetics");

        // Visibility (Default ON)
        // Special handling because it's not a standard key in 'hotbar_items' with one HDB ID
        // It has on/off. Default is ON.
        ItemStack visItem = plugin.getConfigManager().getVisibilityItem(player, true);
        int slot = plugin.getConfigManager().getSlot("hotbar_items.visibility");
        if (visItem != null && slot >= 0) {
            player.getInventory().setItem(slot, visItem);
        }
    }

    private void setItem(Player player, String key) {
        ItemManager itemManager = plugin.getItemManager();
        ItemStack item = itemManager.getConfigItem(key, player);
        int slot = itemManager.getConfigSlot(key);

        if (item != null && slot >= 0 && slot < 36) {
            player.getInventory().setItem(slot, item);
        }
    }
}
