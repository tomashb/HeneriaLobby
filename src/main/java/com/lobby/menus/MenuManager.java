package com.lobby.menus;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Lightweight menu manager responsible for instantiating menus and tracking
 * which players currently have a managed menu opened. Only the new generation
 * menus built on {@link Menu} are supported.
 */
public class MenuManager {

    private final LobbyPlugin plugin;
    private final AssetManager assetManager;
    private final com.lobby.friends.FriendManager friendManager;
    private final Map<UUID, Menu> openMenus = new ConcurrentHashMap<>();

    public MenuManager(final LobbyPlugin plugin,
                       final AssetManager assetManager,
                       final com.lobby.friends.FriendManager friendManager) {
        this.plugin = plugin;
        this.assetManager = assetManager;
        this.friendManager = friendManager;
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

        if (menuId.equals("amis_menu")) {
            return openFriendsMenu(player, 0);
        }
        if (menuId.equals("amis_requests_menu")) {
            return openFriendRequestsMenu(player);
        }

        if (isSimpleMenu(menuId)) {
            return buildAndOpenSimpleMenu(player, menuId, placeholders, context);
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

    public boolean openFriendsMenu(final Player player, final int page) {
        if (player == null) {
            return false;
        }
        if (friendManager == null) {
            player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cLe menu des amis est indisponible."));
            return false;
        }
        final UUID playerUuid = player.getUniqueId();
        final Set<UUID> onlineSnapshot = Bukkit.getOnlinePlayers().stream()
                .map(Player::getUniqueId)
                .collect(Collectors.toUnmodifiableSet());
        return buildAndOpenHeavyMenu(player,
                () -> new FriendMenuContext(friendManager.loadMenuData(playerUuid, onlineSnapshot), page),
                (viewer, context) -> new com.lobby.menus.friends.FriendsMenu(plugin, this, assetManager,
                        context.data().friends(), Math.max(0, context.page()), context.data().requests().size()));
    }

    public boolean openFriendRequestsMenu(final Player player) {
        if (player == null) {
            return false;
        }
        if (friendManager == null) {
            player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cLes demandes d'amis sont indisponibles."));
            return false;
        }
        final UUID playerUuid = player.getUniqueId();
        return buildAndOpenHeavyMenu(player,
                () -> friendManager.loadIncomingRequests(playerUuid),
                (viewer, requests) -> new com.lobby.menus.friends.FriendRequestsMenu(plugin, this, assetManager, requests));
    }

    public void toggleFriendFavorite(final Player player, final UUID friendUuid, final int currentPage) {
        if (player == null || friendUuid == null) {
            return;
        }
        if (friendManager == null) {
            player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cLe système d'amis est indisponible."));
            return;
        }
        final UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean updated = friendManager.toggleFavorite(playerUuid, friendUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (updated) {
                    player.sendMessage(com.lobby.utils.MessageUtils.colorize("&aFavori mis à jour."));
                } else {
                    player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cImpossible de mettre à jour ce favori."));
                }
                openFriendsMenu(player, currentPage);
            });
        });
    }

    public void handleFriendPrompt(final Player player, final String input) {
        if (player == null) {
            return;
        }
        if (friendManager == null) {
            player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cLe système d'amis est indisponible."));
            return;
        }
        final UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final com.lobby.friends.FriendOperationResult result = friendManager.sendFriendRequest(playerUuid, input);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                player.sendMessage(result.message());
                openFriendsMenu(player, 0);
            });
        });
    }

    public void handleFriendRequestDecision(final Player player, final UUID senderUuid, final boolean accept) {
        if (player == null || senderUuid == null) {
            return;
        }
        if (friendManager == null) {
            player.sendMessage(com.lobby.utils.MessageUtils.colorize("&cLe système d'amis est indisponible."));
            return;
        }
        final UUID playerUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean success = accept
                    ? friendManager.acceptRequest(playerUuid, senderUuid)
                    : friendManager.declineRequest(playerUuid, senderUuid);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }
                if (accept) {
                    player.sendMessage(com.lobby.utils.MessageUtils.colorize(success
                            ? "&aDemande d'ami acceptée !"
                            : "&cImpossible d'accepter cette demande."));
                } else {
                    player.sendMessage(com.lobby.utils.MessageUtils.colorize(success
                            ? "&eDemande d'ami refusée."
                            : "&cImpossible de refuser cette demande."));
                }
                openFriendRequestsMenu(player);
            });
        });
    }

    public <T> boolean buildAndOpenHeavyMenu(final Player player,
                                             final Supplier<T> dataSupplier,
                                             final BiFunction<Player, T, Menu> menuFactory) {
        if (player == null || dataSupplier == null || menuFactory == null) {
            return false;
        }
        final UUID uuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final T data;
            try {
                data = dataSupplier.get();
            } catch (final Exception exception) {
                plugin.getLogger().warning("Failed to prepare heavy menu: " + exception.getMessage());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    final Player target = Bukkit.getPlayer(uuid);
                    if (target != null && target.isOnline()) {
                        target.sendMessage(com.lobby.utils.MessageUtils.colorize("&cCe menu est temporairement indisponible."));
                    }
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                final Player target = Bukkit.getPlayer(uuid);
                if (target == null || !target.isOnline()) {
                    return;
                }
                final Menu menu = menuFactory.apply(target, data);
                if (menu != null) {
                    displayMenu(target, menu);
                }
            });
        });
        return true;
    }

    private record FriendMenuContext(com.lobby.friends.FriendMenuData data, int page) {
    }
}
