package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendsDataProvider;
import com.lobby.friends.FriendsPlaceholderData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight menu manager responsible for instantiating menus and tracking
 * which players currently have a managed menu opened. Only the new generation
 * menus built on {@link Menu} are supported.
 */
public class MenuManager {

    private final LobbyPlugin plugin;
    private final AssetManager assetManager;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    public MenuManager(final LobbyPlugin plugin,
                       final AssetManager assetManager) {
        this.plugin = plugin;
        this.assetManager = assetManager;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public boolean openMenu(final Player player, final String rawMenuId) {
        return openMenu(player, rawMenuId, Map.of(), MenuRenderContext.EMPTY);
    }

    public boolean openMenu(final Player player,
                            final String rawMenuId,
                            final Map<String, String> placeholders) {
        return openMenu(player, rawMenuId, placeholders, MenuRenderContext.EMPTY);
    }

    public boolean openMenu(final Player player,
                            final String rawMenuId,
                            final Map<String, String> placeholders,
                            final MenuRenderContext context) {
        if (player == null || rawMenuId == null || rawMenuId.isBlank()) {
            return false;
        }
        final String menuId = rawMenuId.toLowerCase(Locale.ROOT);
        final Map<String, String> resolvedPlaceholders = new HashMap<>();
        if (placeholders != null && !placeholders.isEmpty()) {
            resolvedPlaceholders.putAll(placeholders);
        }
        populateMenuSpecificPlaceholders(player, menuId, resolvedPlaceholders);

        if (isSimpleMenu(menuId)) {
            return buildAndOpenSimpleMenu(player, menuId, resolvedPlaceholders, context);
        }

        final Menu menu = ConfiguredMenu.fromConfiguration(plugin, this, assetManager, menuId, resolvedPlaceholders, context);
        if (menu == null) {
            return false;
        }
        displayMenu(player, menu);
        return true;
    }

    public Optional<Menu> getOpenMenu(final UUID uuid) {
        if (uuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(openMenus.get(uuid));
    }

    public void handleMenuClosed(final Player player) {
        if (player == null) {
            return;
        }
        final Menu menu = openMenus.remove(player.getUniqueId());
        if (menu instanceof CloseableMenu closeableMenu) {
            closeableMenu.handleClose(player);
        }
    }

    public void closeAll() {
        if (openMenus.isEmpty()) {
            return;
        }
        final Runnable closer = () -> openMenus.keySet().forEach(uuid -> {
            final Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                target.closeInventory();
            }
        });
        if (Bukkit.isPrimaryThread()) {
            closer.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, closer);
        }
        openMenus.clear();
    }

    public void reloadMenus() {
        assetManager.reload();
    }

    public void shutdown() {
        closeAll();
        assetManager.shutdown();
    }

    private boolean isSimpleMenu(final String menuId) {
        return switch (menuId) {
            case "jeux_menu", "profil_menu" -> true;
            default -> false;
        };
    }

    private boolean buildAndOpenSimpleMenu(final Player player,
                                           final String menuId,
                                           final Map<String, String> placeholders,
                                           final MenuRenderContext context) {
        final Menu menu = ConfiguredMenu.fromConfiguration(plugin, this, assetManager, menuId, placeholders, context);
        if (menu == null) {
            return false;
        }
        displayMenu(player, menu);
        return true;
    }

    private void populateMenuSpecificPlaceholders(final Player player,
                                                  final String menuId,
                                                  final Map<String, String> placeholders) {
        if (player == null || menuId == null || placeholders == null) {
            return;
        }
        if (!"profil_menu".equals(menuId)) {
            return;
        }
        final FriendsDataProvider dataProvider = plugin.getFriendsDataProvider();
        if (dataProvider == null) {
            return;
        }
        final FriendsPlaceholderData data = dataProvider.resolve(player);
        if (data == null) {
            return;
        }
        data.toPlaceholderMap().forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                return;
            }
            placeholders.put('%' + key + '%', value == null ? "" : value);
        });
    }

    public void displayMenu(final Player player, final Menu menu) {
        if (player == null || menu == null) {
            return;
        }
        final Runnable opener = () -> {
            if (!player.isOnline()) {
                return;
            }
            menu.open(player);
            openMenus.put(player.getUniqueId(), menu);
        };
        if (Bukkit.isPrimaryThread()) {
            opener.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, opener);
        }
    }

}
