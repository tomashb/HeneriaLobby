package com.lobby.friends.listeners;

import com.lobby.LobbyPlugin;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.manager.FriendsSettingsManager;
import com.lobby.friends.menu.FriendsListMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Advanced friends list click handler responsible for contextual actions such
 * as teleportation checks, favourites management and quick messaging. Keeping
 * the logic in a dedicated listener prevents the {@link FriendsListMenu} from
 * becoming bloated and makes the behaviour easier to evolve.
 */
public final class FriendsListClickHandler implements Listener {

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final FriendsSettingsManager friendsSettingsManager;

    public FriendsListClickHandler(final LobbyPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.friendsManager = Objects.requireNonNull(plugin.getFriendsManager(), "friendsManager");
        this.friendsSettingsManager = plugin.getFriendsSettingsManager();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        final String title = event.getView().getTitle();
        if (title == null || (!title.contains("Liste d'Amis") && !title.contains("Mes Amis"))) {
            return;
        }

        event.setCancelled(true);

        final ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || !clickedItem.hasItemMeta()) {
            return;
        }

        final String friendName = extractFriendName(clickedItem);
        if (friendName == null || friendName.isEmpty()) {
            return;
        }

        final ClickType clickType = event.getClick();
        plugin.getLogger().info("Clic détecté sur ami " + friendName + " par " + player.getName() + " - Type: " + clickType);

        switch (clickType) {
            case LEFT -> handleLeftClick(player, friendName);
            case RIGHT -> handleRightClick(player, friendName);
            case MIDDLE -> handleMiddleClick(player, friendName);
            case SHIFT_LEFT -> handleShiftLeftClick(player, friendName);
            case SHIFT_RIGHT -> handleShiftRightClick(player, friendName);
            default -> plugin.getLogger().fine("Type de clic non géré: " + clickType);
        }
    }

    private String extractFriendName(final ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        final String displayName = meta.getDisplayName();
        if (displayName == null || displayName.isEmpty()) {
            return null;
        }

        String cleanName = displayName.replaceAll("§[0-9a-fk-or]", "");
        if (cleanName.contains("●")) {
            final String[] parts = cleanName.split("●");
            if (parts.length >= 2) {
                cleanName = parts[1].trim();
            }
        } else if (cleanName.contains("★")) {
            final String[] parts = cleanName.split("★");
            if (parts.length >= 2) {
                cleanName = parts[1].trim();
            }
        }
        if (cleanName.contains("(")) {
            cleanName = cleanName.split("\\(")[0].trim();
        }
        return cleanName.trim();
    }

    private void handleLeftClick(final Player player, final String friendName) {
        plugin.getLogger().info("Clic gauche sur " + friendName + " par " + player.getName());

        final UUID playerId = player.getUniqueId();
        final CompletableFuture<String> teleportFuture;
        if (friendsSettingsManager != null) {
            teleportFuture = friendsSettingsManager.getTeleportationSetting(playerId)
                    .exceptionally(throwable -> {
                        plugin.getLogger().warning("Erreur paramètres de téléportation pour " + player.getName() + ": " + throwable.getMessage());
                        return "DISABLED";
                    });
        } else {
            teleportFuture = CompletableFuture.completedFuture("ASK_PERMISSION");
        }

        teleportFuture.thenAccept(setting -> Bukkit.getScheduler().runTask(plugin, () ->
                handleTeleportSetting(player, friendName, setting)));
    }

    private void handleTeleportSetting(final Player player,
                                       final String friendName,
                                       final String settingRaw) {
        final String teleportSetting = settingRaw == null ? "DISABLED" : settingRaw.toUpperCase();
        plugin.getLogger().info("Paramètre téléportation de " + player.getName() + ": " + teleportSetting);

        switch (teleportSetting) {
            case "DISABLED" -> {
                player.sendMessage("§c❌ Téléportation désactivée dans vos paramètres !");
                player.sendMessage("§7Modifiez vos paramètres d'amitié pour activer la téléportation");
                player.sendMessage("§7Ou utilisez §e/friends settings§7 pour changer cette option");
            }
            case "ASK_PERMISSION" -> requestTeleportPermission(player, friendName);
            case "FAVORITES" -> friendsManager.isFavorite(player.getUniqueId(), friendName)
                    .thenAccept(isFavorite -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (Boolean.TRUE.equals(isFavorite)) {
                            teleportToFriend(player, friendName);
                        } else {
                            player.sendMessage("§c❌ Téléportation limitée aux favoris !");
                            player.sendMessage("§7Ajoutez §e" + friendName + "§7 en favori ou changez vos paramètres");
                            player.sendMessage("§7Utilisez §eShift+Clic gauche§7 pour ajouter en favori");
                        }
                    }));
            case "FREE" -> teleportToFriend(player, friendName);
            default -> {
                player.sendMessage("§c❌ Paramètres de téléportation non configurés !");
                player.sendMessage("§7Configurez vos paramètres dans §e/friends settings");
            }
        }
    }

    private void handleRightClick(final Player player, final String friendName) {
        plugin.getLogger().info("Clic droit sur " + friendName + " par " + player.getName());
        player.sendMessage("§e⚙ Ouverture du menu d'options pour §6" + friendName + "§e...");
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openFriendOptionsMenu(player, friendName), 3L);
    }

    private void handleMiddleClick(final Player player, final String friendName) {
        plugin.getLogger().info("Clic milieu sur " + friendName + " par " + player.getName());

        final Player target = Bukkit.getPlayerExact(friendName);
        if (target == null) {
            player.sendMessage("§c❌ " + friendName + " n'est pas en ligne pour recevoir des messages !");
            return;
        }

        canSendPrivateMessage(player, target).thenAccept(allowed -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (!allowed) {
                player.sendMessage("§c❌ " + friendName + " n'accepte pas les messages privés !");
                player.sendMessage("§7Leurs paramètres bloquent les messages");
                return;
            }
            player.closeInventory();
            activateMessageMode(player, friendName);
        }));
    }

    private void handleShiftLeftClick(final Player player, final String friendName) {
        plugin.getLogger().info("Shift+Clic gauche sur " + friendName + " par " + player.getName());

        friendsManager.isFavorite(player.getUniqueId(), friendName).whenComplete((isFavorite, error) -> {
            if (error != null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c❌ Erreur lors de la récupération des favoris");
                    plugin.getLogger().warning("Erreur favoris pour " + player.getName() + " -> " + friendName + ": " + error.getMessage());
                });
                return;
            }

            final boolean currentlyFavorite = Boolean.TRUE.equals(isFavorite);
            final CompletableFuture<Boolean> future = currentlyFavorite
                    ? friendsManager.removeFromFavorites(player.getUniqueId(), friendName)
                    : friendsManager.addToFavorites(player.getUniqueId(), friendName);

            future.whenComplete((success, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (throwable != null || success == null || !success) {
                    player.sendMessage("§c❌ Erreur lors de la mise à jour des favoris");
                    plugin.getLogger().warning("Erreur favoris pour " + player.getName() + " -> " + friendName + ": "
                            + (throwable != null ? throwable.getMessage() : "action annulée"));
                    return;
                }

                if (currentlyFavorite) {
                    player.sendMessage("§c★ §e" + friendName + "§c retiré de vos favoris");
                    player.playSound(player.getLocation(), "block.note_block.bass", 0.5f, 0.8f);
                } else {
                    player.sendMessage("§6★ §e" + friendName + "§6 ajouté à vos favoris !");
                    player.playSound(player.getLocation(), "block.note_block.chime", 0.5f, 1.2f);
                }
                refreshFriendsListMenu(player);
            }));
        });
    }

    private void handleShiftRightClick(final Player player, final String friendName) {
        plugin.getLogger().info("Shift+Clic droit sur " + friendName + " par " + player.getName());
        player.sendMessage("§b📊 Chargement du profil détaillé de §3" + friendName + "§b...");
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> openFriendProfileMenu(player, friendName), 3L);
    }

    private void requestTeleportPermission(final Player player, final String friendName) {
        final Player target = Bukkit.getPlayerExact(friendName);
        if (target == null) {
            player.sendMessage("§c❌ " + friendName + " n'est pas en ligne !");
            return;
        }

        player.sendMessage("§e📤 Demande de téléportation envoyée à §6" + friendName + "§e !");
        target.sendMessage("§e📥 §6" + player.getName() + "§e souhaite se téléporter à vous");
        target.sendMessage("§7Tapez §a/tpaccept " + player.getName() + "§7 pour accepter");
        target.sendMessage("§7Tapez §c/tpdeny " + player.getName() + "§7 pour refuser");
        target.sendMessage("§7La demande expire dans §e30 secondes");
        target.playSound(target.getLocation(), "block.note_block.pling", 0.7f, 1.0f);
        player.playSound(player.getLocation(), "entity.experience_orb.pickup", 0.5f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> player.sendMessage(
                "§c⏰ Demande de téléportation à " + friendName + " expirée"), 20L * 30);
    }

    private void teleportToFriend(final Player player, final String friendName) {
        final Player target = Bukkit.getPlayerExact(friendName);
        if (target == null) {
            player.sendMessage("§c❌ " + friendName + " n'est pas en ligne !");
            return;
        }

        player.sendMessage("§a✈ Téléportation vers §2" + friendName + "§a...");
        player.playSound(player.getLocation(), "entity.enderman.teleport", 0.7f, 1.0f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !target.isOnline()) {
                if (player.isOnline()) {
                    player.sendMessage("§c❌ Téléportation annulée - " + friendName + " s'est déconnecté");
                }
                return;
            }
            try {
                player.teleport(target.getLocation());
                player.sendMessage("§a✅ Téléporté vers §2" + friendName + "§a !");
                target.sendMessage("§b👤 §3" + player.getName() + "§b s'est téléporté à vous");
                target.playSound(target.getLocation(), "entity.player.levelup", 0.3f, 2.0f);
            } catch (final Exception exception) {
                player.sendMessage("§c❌ Erreur lors de la téléportation !");
                plugin.getLogger().warning("Erreur téléportation " + player.getName() + " vers " + friendName + ": "
                        + exception.getMessage());
            }
        }, 20L);
    }

    private CompletableFuture<Boolean> canSendPrivateMessage(final Player sender, final Player target) {
        if (friendsSettingsManager == null) {
            return CompletableFuture.completedFuture(true);
        }

        return friendsSettingsManager.getPrivateMessagesSetting(target.getUniqueId())
                .thenCompose(settingRaw -> {
                    final String setting = settingRaw == null ? "FRIENDS" : settingRaw.toUpperCase();
                    return switch (setting) {
                        case "ALL" -> CompletableFuture.completedFuture(true);
                        case "FRIENDS" -> friendsManager.areFriends(sender, target);
                        case "FAVORITES" -> friendsManager.isFavorite(target.getUniqueId(), sender.getName());
                        case "DISABLED" -> CompletableFuture.completedFuture(false);
                        default -> CompletableFuture.completedFuture(true);
                    };
                }).exceptionally(throwable -> {
                    plugin.getLogger().warning("Erreur vérification paramètres messages: " + throwable.getMessage());
                    return true;
                });
    }

    private void activateMessageMode(final Player player, final String friendName) {
        player.sendMessage("§d💬 §6Tapez votre message pour §e" + friendName + "§6 :");
        player.sendMessage("§7Exemple: §fSalut ! Comment ça va ?");
        player.sendMessage("§7Tapez §c'annuler'§7 pour annuler");

        final FriendAddChatListener chatListener = plugin.getFriendAddChatListener();
        if (chatListener != null) {
            chatListener.activateMessageMode(player, friendName);
        }
    }

    private void refreshFriendsListMenu(final Player player) {
        if (!player.isOnline()) {
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, () -> new FriendsListMenu(plugin, friendsManager, player), 20L);
    }

    private void openFriendOptionsMenu(final Player player, final String friendName) {
        final Inventory inventory = Bukkit.createInventory(null, 27, "§8» §6Options pour " + friendName);

        final ItemStack filler = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        final ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName("§7");
            filler.setItemMeta(fillerMeta);
        }
        final int[] borders = {0, 1, 2, 6, 7, 8, 9, 17, 18, 19, 25, 26};
        for (int slot : borders) {
            inventory.setItem(slot, filler);
        }

        inventory.setItem(10, createOptionItem(Material.ENDER_PEARL, "§5🌀 Se téléporter", List.of(
                "§7Se téléporter vers " + friendName,
                "",
                "§7Statut: " + (Bukkit.getPlayerExact(friendName) != null ? "§aEn ligne" : "§cHors ligne"),
                "",
                "§8» §5Cliquez pour vous téléporter")));

        inventory.setItem(11, createOptionItem(Material.WRITABLE_BOOK, "§d💬 Envoyer un message", List.of(
                "§7Envoyer un message privé à " + friendName,
                "",
                "§7Statut: " + (Bukkit.getPlayerExact(friendName) != null ? "§aDisponible" : "§cHors ligne"),
                "",
                "§8» §dCliquez pour envoyer un message")));

        inventory.setItem(12, createFavoriteOptionItem(false, friendName));

        final CompletableFuture<Boolean> favoriteFuture = friendsManager.isFavorite(player.getUniqueId(), friendName);
        favoriteFuture.thenAccept(isFavorite -> Bukkit.getScheduler().runTask(plugin, () -> {
            final boolean favourite = Boolean.TRUE.equals(isFavorite);
            inventory.setItem(12, createFavoriteOptionItem(favourite, friendName));
        }));

        inventory.setItem(13, createOptionItem(Material.PLAYER_HEAD, "§b📊 Voir le profil détaillé", List.of(
                "§7Voir les statistiques complètes de " + friendName,
                "",
                "§b▸ Temps de jeu ensemble",
                "§b▸ Messages échangés",
                "§b▸ Historique d'amitié",
                "§b▸ Activités communes",
                "",
                "§8» §bCliquez pour voir le profil")));

        inventory.setItem(14, createOptionItem(Material.PAPER, "§a📨 Inviter à jouer", List.of(
                "§7Inviter " + friendName + " à rejoindre votre partie",
                "",
                "§a▸ Invitations de jeu",
                "§a▸ Rejoindre votre serveur",
                "§a▸ Activités communes",
                "",
                "§8» §aCliquez pour inviter")));

        inventory.setItem(15, createOptionItem(Material.REDSTONE_BLOCK, "§4🚫 Bloquer", List.of(
                "§7Bloquer " + friendName,
                "",
                "§c⚠ ATTENTION ⚠",
                "§c▸ Cette action supprimera l'amitié",
                "§c▸ " + friendName + " ne pourra plus vous contacter",
                "§c▸ Action réversible depuis les paramètres",
                "",
                "§8» §4Cliquez pour bloquer")));

        inventory.setItem(16, createOptionItem(Material.BARRIER, "§c❌ Supprimer l'ami", List.of(
                "§7Supprimer " + friendName + " de vos amis",
                "",
                "§c⚠ Cette action est irréversible",
                "§7Vous devrez renvoyer une demande d'ami",
                "",
                "§8» §cCliquez pour supprimer")));

        inventory.setItem(22, createOptionItem(Material.ARROW, "§c« Retour à la liste d'amis", List.of(
                "§7Revenir au menu précédent",
                "",
                "§8» §cCliquez pour retourner")));

        player.openInventory(inventory);
        player.playSound(player.getLocation(), "block.chest.open", 0.5f, 1.2f);
    }

    private ItemStack createFavoriteOptionItem(final boolean favourite, final String friendName) {
        final ItemStack item = new ItemStack(Material.NETHER_STAR);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (favourite) {
                meta.setDisplayName("§c★ Retirer des favoris");
                meta.setLore(List.of(
                        "§7Retirer " + friendName + " de vos favoris",
                        "",
                        "§c▸ Statut actuel: §6★ EN FAVORIS",
                        "",
                        "§7Les favoris bénéficient de:",
                        "§8• §7Notifications prioritaires",
                        "§8• §7Téléportation rapide (selon paramètres)",
                        "§8• §7Auto-acceptation des demandes",
                        "",
                        "§8» §cCliquez pour retirer des favoris"
                ));
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.setDisplayName("§6★ Ajouter aux favoris");
                meta.setLore(List.of(
                        "§7Ajouter " + friendName + " à vos favoris",
                        "",
                        "§7▸ Statut actuel: §8Ami normal",
                        "",
                        "§7Les favoris bénéficient de:",
                        "§8• §6Notifications prioritaires",
                        "§8• §6Téléportation rapide (selon paramètres)",
                        "§8• §6Auto-acceptation des demandes",
                        "",
                        "§8» §6Cliquez pour ajouter aux favoris"
                ));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createOptionItem(final Material material,
                                       final String name,
                                       final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openFriendProfileMenu(final Player player, final String friendName) {
        final Inventory inventory = Bukkit.createInventory(null, 54, "§8» §3Profil de " + friendName);
        player.openInventory(inventory);
        player.sendMessage("§b📊 Profil de §3" + friendName + "§b chargé !");
        player.sendMessage("§7Fonctionnalité en développement - Plus de détails bientôt disponibles");
    }
}
