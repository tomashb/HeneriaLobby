package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class InteractListener implements Listener {

    private final HeneriaLobby plugin;

    public InteractListener(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item == null) return;

            // 1. Check for Item ID (Hotbar Items)
            String itemId = plugin.getItemManager().getPersistentItemId(item);
            if (itemId != null) {
                // Lookup Action from Config based on ID
                String action = plugin.getConfig().getString("hotbar_items." + itemId + ".action");
                if (action != null) {
                    handleAction(event.getPlayer(), action);
                    event.setCancelled(true);
                    return;
                }
            }

            // 2. Check for direct Action (Menu Items or direct hotbar action if configured)
            String action = plugin.getItemManager().getPersistentAction(item);
            if (action != null) {
                handleAction(event.getPlayer(), action);
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Check for direct Action (Menu Items)
        String action = plugin.getItemManager().getPersistentAction(event.getCurrentItem());
        if (action != null) {
            event.setCancelled(true);
            handleAction(player, action);
        }
        // Also check Item ID just in case
        else {
             String itemId = plugin.getItemManager().getPersistentItemId(event.getCurrentItem());
             if (itemId != null) {
                 String confAction = plugin.getConfig().getString("hotbar_items." + itemId + ".action");
                 if (confAction != null) {
                     event.setCancelled(true);
                     handleAction(player, confAction);
                 }
             }
        }
    }

    private void handleAction(Player player, String action) {
        if (action.startsWith("OPEN_MENU:")) {
            String menuId = action.substring("OPEN_MENU:".length());
            plugin.getMenuManager().openMenu(player, menuId);
        } else if (action.startsWith("CONNECT:")) {
            String server = action.substring("CONNECT:".length()).trim();
            player.sendMessage(Component.text("Connecting to " + server + "...", NamedTextColor.GREEN));
        } else if (action.equals("TOGGLE_VISIBILITY")) {
             player.sendMessage(Component.text("Visibilité des joueurs basculée.", NamedTextColor.YELLOW));
        }
    }
}
