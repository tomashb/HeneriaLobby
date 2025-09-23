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
        final Menu menu = createMenu(rawMenuId);
        if (menu == null) {
            return false;
        }
        menu.open(player);
        openMenus.put(player.getUniqueId(), menu);
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

    private Menu createMenu(final String rawMenuId) {
        final String menuId = rawMenuId.toLowerCase(Locale.ROOT);
        return switch (menuId) {
            case "jeux_menu" -> new JeuxMenu(assetManager);
            default -> null;
        };
    }
}
