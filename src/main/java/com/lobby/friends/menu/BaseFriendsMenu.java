package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.Sound;

import java.util.Objects;
import java.util.UUID;

/**
 * Base implementation shared by every friends related inventory. It delegates
 * click and close handling to {@link FriendsMenuManager} so menus no longer
 * need to register their own listeners which previously caused inventory state
 * desynchronisation when players spam-clicked. The base class also exposes a
 * {@link #safeRefresh()} helper that performs the close/reopen cycle on the
 * main server thread while respecting a short delay to avoid duplicate
 * InventoryClick events.
 */
public abstract class BaseFriendsMenu {

    protected final LobbyPlugin plugin;
    protected final FriendsManager friendsManager;
    protected final FriendsMenuManager menuManager;
    protected final Player player;

    protected BaseFriendsMenu(final LobbyPlugin plugin,
                              final FriendsManager friendsManager,
                              final FriendsMenuManager menuManager,
                              final Player player) {
        this.plugin = plugin;
        this.friendsManager = friendsManager;
        this.menuManager = menuManager;
        this.player = player;
    }

    public Player getPlayer() {
        return player;
    }

    /**
     * Opens the menu for the associated player and registers it inside the
     * {@link FriendsMenuManager}. Implementations should create their
     * inventories and populate the default content from within
     * {@link #openMenu()}.
     */
    public final void open() {
        if (player == null || !player.isOnline()) {
            return;
        }
        menuManager.registerMenu(player, this);
        openMenu();
    }

    /**
     * Called by {@link #open()} once the menu has been registered. Concrete
     * implementations must create their inventory and call
     * {@link Player#openInventory(Inventory)} from within this method.
     */
    protected abstract void openMenu();

    /**
     * @return the Bukkit inventory currently displayed to the player.
     */
    public abstract Inventory getInventory();

    /**
     * @return the translated title of the inventory.
     */
    public abstract String getTitle();

    /**
     * Called by {@link FriendsMenuManager} whenever the player interacts with
     * the inventory. The event has already been cancelled so implementations
     * only need to focus on handling the action.
     */
    public abstract void handleMenuClick(InventoryClickEvent event);

    /**
     * Invoked when the inventory is closed either manually by the player or as
     * part of a refresh cycle. Subclasses can override to perform additional
     * cleanup but should always call {@code super.handleMenuClose(event)} to
     * unregister the menu.
     */
    public void handleMenuClose(final InventoryCloseEvent event) {
        final UUID viewerId = event.getPlayer().getUniqueId();
        menuManager.unregisterMenu(viewerId, this);
    }

    /**
     * Schedules a safe refresh of the inventory by closing it and re-opening
     * the menu after a short delay as recommended for Paper 1.21+. The delay
     * prevents the server from processing stale click packets while the menu is
     * rebuilding.
     */
    public void safeRefresh() {
        menuManager.safeRefresh(this);
    }

    /**
     * Called by {@link FriendsMenuManager#safeRefresh(BaseFriendsMenu)} once
     * the close/reopen cycle needs to re-open the menu. The default
     * implementation simply delegates to {@link #open()} but it can be
     * overridden when menus require custom refresh behaviour.
     */
    public void reopen() {
        open();
    }

    protected void playClickSound(final Sound sound, final float pitch) {
        if (sound == null) {
            return;
        }
        final BukkitScheduler scheduler = plugin.getServer().getScheduler();
        scheduler.runTask(plugin, () -> player.playSound(player.getLocation(), sound, 1.0f, pitch));
    }

    protected static boolean titlesMatch(final String expected, final String actual) {
        return Objects.equals(expected, actual);
    }
}
