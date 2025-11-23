package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import fr.heneria.lobby.manager.ItemManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import me.arcaniax.hdb.api.DatabaseLoadEvent;

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
        // Reload items for all online players when HDB is ready
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            giveLobbyItems(player);
        }
    }

    private void giveLobbyItems(Player player) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) return;

        // Clear inventory first? Prompt implies "donne ces items", usually clear is good for lobby.
        player.getInventory().clear();

        // We iterate over known keys because we need to handle specific logic for them (like visibility default)
        // Or we just load them all.
        // Based on the prompt, we have specific items: selector, profile, menu, cosmetics, visibility.

        // Selector
        setItem(player, "selector");

        // Profile
        setItem(player, "profile");

        // Main Menu
        setItem(player, "menu");

        // Cosmetics
        setItem(player, "cosmetics");

        // Visibility (Default ON)
        // The prompt says "Switch". We need to decide which one to give.
        // Assuming default is ON.
        // We check if player has a preference? For now, just give ON.
        setItem(player, "visibility_on");
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
