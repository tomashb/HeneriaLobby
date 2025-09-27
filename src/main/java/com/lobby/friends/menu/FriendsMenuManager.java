package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralises every friends related inventory interaction. The manager runs at
 * {@link EventPriority#HIGHEST} to ensure all move attempts are cancelled even
 * when Paper processes asynchronous clicks. It also delegates the actual menu
 * logic to {@link BaseFriendsMenu} instances registered via
 * {@link #registerMenu(Player, BaseFriendsMenu)}.
 */
public class FriendsMenuManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, BaseFriendsMenu> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClick = new ConcurrentHashMap<>();

    public FriendsMenuManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerMenu(final Player player, final BaseFriendsMenu menu) {
        if (player == null || menu == null) {
            return;
        }
        openMenus.put(player.getUniqueId(), menu);
    }

    public void unregisterMenu(final UUID playerId, final BaseFriendsMenu menu) {
        if (playerId == null) {
            return;
        }
        openMenus.remove(playerId, menu);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(final InventoryClickEvent event) {
        final HumanEntity clicker = event.getWhoClicked();
        if (!(clicker instanceof Player player)) {
            return;
        }
        final String title = event.getView().getTitle();
        if (title == null || !title.contains("§8» §")) {
            return;
        }

        event.setCancelled(true);
        event.setResult(Event.Result.DENY);

        final long now = System.currentTimeMillis();
        final long last = lastClick.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 100L) {
            return;
        }
        lastClick.put(player.getUniqueId(), now);

        final BaseFriendsMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> menu.handleMenuClick(event));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClose(final InventoryCloseEvent event) {
        final HumanEntity human = event.getPlayer();
        if (!(human instanceof Player player)) {
            return;
        }
        final BaseFriendsMenu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        final String title = event.getView().getTitle();
        if (title == null || !title.contains("§8» §")) {
            unregisterMenu(player.getUniqueId(), menu);
            return;
        }
        menu.handleMenuClose(event);
    }

    public void safeRefresh(final BaseFriendsMenu menu) {
        final Player player = menu.getPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, menu::reopen, 1L);
        }, 1L);
    }
}
