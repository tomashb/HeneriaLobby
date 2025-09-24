package com.lobby.menus;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

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

    public MenuManager(final LobbyPlugin plugin, final AssetManager assetManager) {
        this.plugin = plugin;
        this.assetManager = assetManager;
    }

    public AssetManager getAssetManager() {
        return assetManager;
    }

    public boolean openMenu(final Player player, final String rawMenuId) {
        if (player == null || rawMenuId == null || rawMenuId.isBlank()) {
            return false;
        }
        final String menuId = rawMenuId.toLowerCase(Locale.ROOT);

        if (isSimpleMenu(menuId)) {
            return buildAndOpenSimpleMenu(player, menuId);
        }

        if (isHeavyMenu(menuId)) {
            return buildAndOpenHeavyMenu(player, menuId);
        }

        return false;
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
        openMenus.remove(player.getUniqueId());
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

    private boolean isHeavyMenu(final String menuId) {
        return switch (menuId) {
            case "stats_detailed_menu" -> true;
            default -> false;
        };
    }

    private boolean buildAndOpenSimpleMenu(final Player player, final String menuId) {
        final Menu menu = ConfiguredMenu.fromConfiguration(plugin, this, assetManager, menuId);
        if (menu == null) {
            return false;
        }
        menu.open(player);
        openMenus.put(player.getUniqueId(), menu);
        return true;
    }

    private boolean buildAndOpenHeavyMenu(final Player player, final String menuId) {
        final Menu menu = ConfiguredMenu.fromConfiguration(plugin, this, assetManager, menuId);
        if (menu == null) {
            return false;
        }
        menu.open(player);
        openMenus.put(player.getUniqueId(), menu);
        return true;
    }
}
