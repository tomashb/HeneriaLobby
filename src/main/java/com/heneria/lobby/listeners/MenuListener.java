package com.heneria.lobby.listeners;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.heneria.lobby.HeneriaLobbyPlugin;
import com.heneria.lobby.menu.GUIManager;
import com.heneria.lobby.menu.Menu;
import com.heneria.lobby.menu.MenuItem;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Handles clicks within configured menus.
 */
public class MenuListener implements Listener {

    private final GUIManager guiManager;
    private final HeneriaLobbyPlugin plugin;

    private static final Sound CLICK_SOUND = Sound.UI_BUTTON_CLICK;
    private static final Sound PURCHASE_SOUND = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
    private static final Sound ERROR_SOUND = Sound.ENTITY_VILLAGER_NO;

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
        int slot = event.getRawSlot();
        int base = event.getInventory().getSize() - 9;

        if (slot == base && guiManager.isSubMenu(player)) {
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            guiManager.back(player);
            return;
        }
        if (slot == base + 3) { // profile
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            guiManager.openMenu(player, "profile");
            return;
        }
        if (slot == base + 4) { // coins - informational
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            return;
        }
        if (slot == base + 5) { // shop shortcut
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            guiManager.openMenu(player, "shop");
            return;
        }
        if (slot == base + 7) { // networks
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            player.sendMessage("§9Discord: §fdiscord.gg/heneria");
            player.sendMessage("§bSite: §fplay.heneria.net");
            return;
        }
        if (slot == base + 8 && !guiManager.isSubMenu(player)) { // close
            player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
            guiManager.clearHistory(player);
            player.closeInventory();
            return;
        }

        MenuItem item = menu.getItems().get(slot);
        if (item == null) {
            player.playSound(player.getLocation(), ERROR_SOUND, 1f, 1f);
            return;
        }
        String action = item.getAction();
        player.playSound(player.getLocation(), CLICK_SOUND, 1f, 1f);
        if (action.startsWith("open_menu:")) {
            String name = action.split(":", 2)[1];
            if (name.equalsIgnoreCase("unlocked")) {
                plugin.getCosmeticsManager().openOwnedMenu(player);
            } else if (!plugin.getCosmeticsManager().openCategoryMenu(player, name)) {
                guiManager.openMenu(player, name);
            }
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
        } else {
            player.playSound(player.getLocation(), ERROR_SOUND, 1f, 1f);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            guiManager.stopBorderAnimation(player);
            guiManager.clearHistory(player);
        }
    }
}

