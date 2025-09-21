package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager implements Listener {

    private final LobbyPlugin plugin;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();
    private final Map<String, ConfigurationSection> menuDefinitions = new ConcurrentHashMap<>();

    public MenuManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        reloadMenus();
    }

    public boolean openMenu(final Player player, final String menuId) {
        if (player == null || menuId == null || menuId.isBlank()) {
            return false;
        }
        final String normalizedId = menuId.toLowerCase(java.util.Locale.ROOT);
        ConfigurationSection menuSection = menuDefinitions.get(normalizedId);
        if (menuSection == null) {
            reloadMenus();
            menuSection = menuDefinitions.get(normalizedId);
            if (menuSection == null) {
                MessageUtils.sendConfigMessage(player, "menus.not_found", Map.of("menu", menuId));
                return false;
            }
        }

        final Menu menu = new ConfiguredMenu(plugin, normalizedId, menuSection);
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

    public void reloadMenus() {
        menuDefinitions.clear();
        loadMenusFromMainConfig();
        loadMenusFromDirectory();
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

    private void loadMenusFromMainConfig() {
        final FileConfiguration menusConfig = plugin.getConfigManager().getMenusConfig();
        final ConfigurationSection menusSection = menusConfig.getConfigurationSection("menus");
        if (menusSection == null) {
            return;
        }
        for (String key : menusSection.getKeys(false)) {
            final ConfigurationSection section = menusSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            menuDefinitions.put(key.toLowerCase(java.util.Locale.ROOT), section);
        }
    }

    private void loadMenusFromDirectory() {
        final File menusDirectory = ensureMenusDirectory();
        final File[] files = menusDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) {
            return;
        }
        for (File file : files) {
            final YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection menuSection = configuration.getConfigurationSection("menu");
            if (menuSection == null) {
                menuSection = configuration;
            }
            final String id = menuSection.getString("id", stripExtension(file.getName()));
            if (id == null || id.isBlank()) {
                continue;
            }
            menuDefinitions.put(id.toLowerCase(java.util.Locale.ROOT), menuSection);
        }
    }

    private File ensureMenusDirectory() {
        final File directory = new File(plugin.getDataFolder(), "config/menus");
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Unable to create config/menus directory for menu definitions.");
        }
        final Set<String> defaults = Set.of(
                "jeux_menu.yml",
                "profil_menu.yml",
                "shop_menu.yml",
                "cosmetiques_menu.yml",
                "hub_menu.yml",
                "settings_menu.yml",
                "language_menu.yml",
                "friends_menu.yml",
                "friend_management.yml",
                "groups_menu.yml",
                "clan_menu.yml"
        );
        for (String fileName : defaults) {
            final File target = new File(directory, fileName);
            if (!target.exists()) {
                plugin.saveResource("config/menus/" + fileName, false);
            }
        }
        return directory;
    }

    private String stripExtension(final String fileName) {
        final int index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }
}
