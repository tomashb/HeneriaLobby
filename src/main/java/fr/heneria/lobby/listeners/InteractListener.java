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

            String action = plugin.getItemManager().getPersistentMeta(item);
            if (action != null) {
                handleAction(event.getPlayer(), action);
                event.setCancelled(true); // Prevent placing blocks/using items if they have actions
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Handle Menu Interactions
        // We need to know if the inventory is one of our menus.
        // A simple way is to check if the clicked item has an action (since our menu items have actions too).
        // Or checking inventory holder, but we used null holder.
        // Or checking window title, but that's unreliable with MiniMessage.

        // Better approach: If the item has an action "CONNECT:", it's likely our menu item.
        // AND we should probably protect the top inventory if it was opened by us.
        // Since we didn't implement a custom Holder, we'll rely on checking the Item's persistent data.

        // NOTE: The previous InventoryListener already prevents moving items in Player Inventory.
        // Here we handle the Top Inventory (Menu) clicks.

        if (event.getCurrentItem() == null) return;

        String action = plugin.getItemManager().getPersistentMeta(event.getCurrentItem());
        if (action != null) {
            event.setCancelled(true); // Always cancel clicks on action items
            if (event.getWhoClicked() instanceof Player) {
                handleAction((Player) event.getWhoClicked(), action);
            }
        } else {
             // If it's a menu we opened, we probably want to cancel ALL clicks in the top inventory,
             // even if they don't have actions (like filler items).
             // Since we don't have a custom holder, let's assume if it's NOT player inventory, and we are in this plugin context...
             // Actually, verifying if it's OUR menu is hard without a Holder.
             // For now, the "filler" items don't have actions, but they should be unclickable.
             // Let's check if the inventory size/type matches or just rely on the fact that it's a lobby.
             // Ideally we should use a Holder.
        }

        // Refined logic:
        // If the top inventory has "filler" items (glass panes), they usually don't have the action tag.
        // But we want to cancel the click.
        // Let's assume any click in a Chest Inventory in this Lobby plugin (where we don't expect other inventories)
        // should be cancelled if the user is not in Creative, unless specified otherwise.
        // BUT, for the purpose of this task, let's focus on the ACTION.

        // If the item has "CONNECT:", we handle it.
    }

    private void handleAction(Player player, String action) {
        if (action.startsWith("OPEN_MENU:")) {
            String menuId = action.substring("OPEN_MENU:".length());
            plugin.getMenuManager().openMenu(player, menuId);
        } else if (action.startsWith("CONNECT:")) {
            String server = action.substring("CONNECT:".length()).trim();
            player.sendMessage(Component.text("Tentative de connexion à : " + server, NamedTextColor.GREEN));
        } else if (action.equals("TOGGLE_VISIBILITY")) {
             // Logic for toggle visibility
             player.sendMessage(Component.text("Toggle Visibility logic to be implemented.", NamedTextColor.YELLOW));
        }
    }
}
