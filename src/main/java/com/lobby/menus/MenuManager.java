package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    public MenuManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean openMenu(final Player player, final String menuId) {
        if (player == null || menuId == null || menuId.isBlank()) {
            return false;
        }
        final FileConfiguration menusConfig = plugin.getConfigManager().getMenusConfig();
        final ConfigurationSection menuSection = menusConfig.getConfigurationSection("menus." + menuId);
        if (menuSection == null) {
            MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", menuId));
            return false;
        }

        final Menu menu = new ConfiguredMenu(plugin, menuId, menuSection);
        openMenus.put(player.getUniqueId(), menu);
        menu.open(player);
        if (menu.getInventory() == null) {
            openMenus.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public Optional<Menu> getOpenMenu(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(openMenus.get(uuid));
    }

    public void closeAll() {
        final var uuids = openMenus.keySet().stream().toList();
        uuids.forEach(uuid -> {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.closeInventory();
            }
        });
        openMenus.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final Menu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        event.setCancelled(true);
        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null || !topInventory.equals(menu.getInventory())) {
            return;
        }
        menu.handleClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (openMenus.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        final Menu menu = openMenus.get(player.getUniqueId());
        if (menu == null) {
            return;
        }
        final Inventory inventory = menu.getInventory();
        if (inventory == null || inventory.equals(event.getInventory())) {
            openMenus.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
    }
}
