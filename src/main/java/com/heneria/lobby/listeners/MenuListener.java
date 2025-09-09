package com.heneria.lobby.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.menu.GUIManager;
import com.heneria.lobby.menu.Menu;
import com.heneria.lobby.menu.MenuItem;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Handles clicks within configured menus.
 */
public class MenuListener implements Listener {

    private final GUIManager guiManager;
    private final HeneriaLobbyPlugin plugin;

    public MenuListener(HeneriaLobbyPlugin plugin, GUIManager guiManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Menu menu = guiManager.getMenuByTitle(event.getView().getTitle());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        MenuItem item = menu.getItems().get(event.getRawSlot());
        if (item == null) {
            return;
        }
        String action = item.getAction();
        if (action.startsWith("open_menu:")) {
            String name = action.split(":", 2)[1];
            guiManager.openMenu(player, name);
        } else if (action.startsWith("run_command:")) {
            String cmd = action.split(":", 2)[1];
            player.closeInventory();
            player.performCommand(cmd);
        } else if (action.startsWith("connect_server:")) {
            String server = action.split(":", 2)[1];
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(server);
            player.closeInventory();
            player.sendPluginMessage(plugin, "BungeeCord", out.toByteArray());
        }
    }
}

