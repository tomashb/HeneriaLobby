package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class InteractListener implements Listener {

    private final HeneriaLobby plugin;
    private final Set<UUID> hiddenPlayers = new HashSet<>();

    public InteractListener(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = event.getItem();
            if (item == null) return;

            String itemId = plugin.getItemManager().getPersistentItemId(item);
            if (itemId != null) {
                String action = plugin.getConfig().getString("hotbar_items." + itemId + ".action");
                if (action != null) {
                    handleAction(event.getPlayer(), action);
                    event.setCancelled(true);
                    return;
                }
            }

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

        String action = plugin.getItemManager().getPersistentAction(event.getCurrentItem());
        if (action != null) {
            event.setCancelled(true);
            handleAction(player, action);
        } else {
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
             toggleVisibility(player);
        }
    }

    private void toggleVisibility(Player player) {
        boolean isHidden = hiddenPlayers.contains(player.getUniqueId());
        if (isHidden) {
            // Show players (Turn visibility ON)
            hiddenPlayers.remove(player.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                player.showPlayer(plugin, online);
            }
            player.sendMessage(Component.text("Joueurs visibles.", NamedTextColor.GREEN));
        } else {
            // Hide players (Turn visibility OFF)
            hiddenPlayers.add(player.getUniqueId());
            for (Player online : Bukkit.getOnlinePlayers()) {
                player.hidePlayer(plugin, online);
            }
            player.sendMessage(Component.text("Joueurs masqués.", NamedTextColor.RED));
        }

        // Update Item
        // New state is !isHidden (if it was hidden, now visible, so true)
        updateVisibilityItem(player, isHidden);
    }

    private void updateVisibilityItem(Player player, boolean isVisible) {
        ItemStack newItem = plugin.getConfigManager().getVisibilityItem(player, isVisible);
        // We need to find the visibility slot. ConfigManager has it.
        int slot = plugin.getConfigManager().getSlot("hotbar_items.visibility");
        if (slot >= 0 && slot < 9) {
            player.getInventory().setItem(slot, newItem);
        }
    }
}
