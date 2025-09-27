package com.lobby.friends.manager;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Periodically refreshes open friends related inventories so that the counters
 * displayed to the player remain accurate even when actions are performed from
 * outside the current menu. Menus register themselves with the manager when
 * they are opened and provide a callback used to recompute their dynamic
 * content.
 */
public final class MenuUpdateManager {

    /**
     * Types of menus that can be tracked. Using an enum makes it easy to extend
     * the system in the future (statistics menu, friends list, etc.).
     */
    public enum MenuType {
        FRIENDS_MAIN,
        FRIENDS_STATISTICS,
        FRIENDS_LIST
    }

    @FunctionalInterface
    public interface MenuUpdater {
        void update(Player player);
    }

    private final LobbyPlugin plugin;
    private final Map<UUID, TrackedMenu> openMenus = new ConcurrentHashMap<>();
    private final BukkitTask updateTask;

    public MenuUpdateManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAllOpenMenus, 100L, 100L);
    }

    public void registerMenu(final Player player,
                              final MenuType menuType,
                              final Inventory inventory,
                              final MenuUpdater updater) {
        if (player == null || menuType == null || inventory == null || updater == null) {
            return;
        }
        final UUID playerId = player.getUniqueId();
        openMenus.put(playerId, new TrackedMenu(menuType, inventory, updater));
    }

    public void unregisterMenu(final Player player) {
        if (player == null) {
            return;
        }
        openMenus.remove(player.getUniqueId());
    }

    public void forceUpdate(final Player player) {
        if (player == null) {
            return;
        }
        final TrackedMenu tracked = openMenus.get(player.getUniqueId());
        if (tracked == null) {
            return;
        }
        final Runnable task = () -> updateTrackedMenu(player, tracked, true);
        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private void updateAllOpenMenus() {
        if (openMenus.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, TrackedMenu> entry : openMenus.entrySet()) {
            final UUID playerId = entry.getKey();
            final Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                openMenus.remove(playerId);
                continue;
            }
            final TrackedMenu tracked = entry.getValue();
            updateTrackedMenu(player, tracked, false);
        }
    }

    private void updateTrackedMenu(final Player player,
                                   final TrackedMenu tracked,
                                   final boolean removeIfClosed) {
        if (player == null || tracked == null) {
            return;
        }
        final Inventory trackedInventory = tracked.inventoryRef.get();
        if (trackedInventory == null) {
            openMenus.remove(player.getUniqueId(), tracked);
            return;
        }
        final Inventory openInventory = player.getOpenInventory() != null
                ? player.getOpenInventory().getTopInventory()
                : null;
        if (openInventory == null || openInventory != trackedInventory) {
            if (removeIfClosed) {
                openMenus.remove(player.getUniqueId(), tracked);
            }
            return;
        }
        try {
            tracked.updater.update(player);
        } catch (final Exception exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Impossible de mettre à jour le menu " + tracked.menuType + " pour " + player.getName(), exception);
        }
    }

    public void shutdown() {
        openMenus.clear();
        if (updateTask != null) {
            updateTask.cancel();
        }
    }

    private static final class TrackedMenu {

        private final MenuType menuType;
        private final WeakReference<Inventory> inventoryRef;
        private final MenuUpdater updater;

        private TrackedMenu(final MenuType menuType,
                            final Inventory inventory,
                            final MenuUpdater updater) {
            this.menuType = menuType;
            this.inventoryRef = new WeakReference<>(inventory);
            this.updater = updater;
        }
    }
}
