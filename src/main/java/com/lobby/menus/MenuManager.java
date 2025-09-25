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

        if (isSimpleMenu(menuId)) {
            return buildAndOpenSimpleMenu(player, menuId, placeholders, context);
        }

        if (isHeavyMenu(menuId)) {
            return buildAndOpenHeavyMenu(player, menuId, placeholders, context);
        }

        final Menu menu = ConfiguredMenu.fromConfiguration(plugin, this, assetManager, menuId, placeholders, context);
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
            case "stats_detailed_menu", "amis_menu", "friend_requests_menu", "friend_settings_menu",
                    "groupe_menu", "party_invites_menu", "clan_menu", "clan_members_menu",
                    "clan_bank_menu", "clan_management_menu" -> true;
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

    private boolean buildAndOpenHeavyMenu(final Player player,
                                          final String menuId,
                                          final Map<String, String> placeholders,
                                          final MenuRenderContext context) {
        return com.lobby.social.menus.SocialHeavyMenus.open(menuId, this, player, placeholders, context)
                || openConfiguredMenu(player, menuId, placeholders, context);
    }

    private boolean openConfiguredMenu(final Player player,
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
