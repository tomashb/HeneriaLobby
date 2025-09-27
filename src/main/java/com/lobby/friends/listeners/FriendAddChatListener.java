package com.lobby.friends.listeners;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.friends.data.FriendData;
import com.lobby.friends.data.FriendRequest;
import com.lobby.friends.manager.BlockedPlayersManager;
import com.lobby.friends.manager.FriendCodeManager;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.friends.menu.BlockedPlayersMenu;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FriendAddChatListener implements Listener {

    private static final String MODE_SEARCH_PLAYER_NAME = "search_player_name";
    private static final String MODE_FRIEND_CODE = "friend_code";

    private final LobbyPlugin plugin;
    private final FriendsManager friendsManager;
    private final Map<UUID, String> pendingRequests = new ConcurrentHashMap<>();
    private final Map<UUID, String> messageTargets = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> pendingBlockReasons = new ConcurrentHashMap<>();

    public FriendAddChatListener(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.friendsManager = plugin.getFriendsManager();
    }

    public void activateMessageMode(final Player player, final String friendName) {
        if (player == null || friendName == null || friendName.isBlank()) {
            return;
        }
        messageTargets.put(player.getUniqueId(), friendName);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

        if (messageTargets.containsKey(playerUUID)) {
            event.setCancelled(true);
            handlePrivateMessageInput(player, event.getMessage());
            return;
        }

        if (pendingBlockReasons.containsKey(playerUUID)) {
            event.setCancelled(true);
            handleBlockReasonChange(event);
            return;
        }

        if (!pendingRequests.containsKey(playerUUID)) {
            return;
        }

        event.setCancelled(true);

        final String mode = pendingRequests.get(playerUUID);
        final String input = event.getMessage().trim();

        if (input.equalsIgnoreCase("annuler") || input.equalsIgnoreCase("cancel")) {
            pendingRequests.remove(playerUUID);
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Ajout d'ami annulé"));
            return;
        }

        pendingRequests.remove(playerUUID);

        if (MODE_SEARCH_PLAYER_NAME.equals(mode)) {
            handleAddByName(player, input);
            return;
        }

        if (MODE_FRIEND_CODE.equals(mode)) {
            handleAddByCode(player, input);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Mode d'ajout non reconnu !"));
    }

    public void activateBlockReasonMode(final Player player, final UUID blockedPlayerUUID) {
        if (player == null || blockedPlayerUUID == null) {
            return;
        }

        final UUID playerUUID = player.getUniqueId();
        pendingBlockReasons.put(playerUUID, blockedPlayerUUID);

        final String blockedPlayerName = getPlayerName(blockedPlayerUUID);

        player.sendMessage("§e📝 §6Modification de la raison de blocage");
        player.sendMessage("§7Joueur bloqué: §c" + blockedPlayerName);
        player.sendMessage("");
        player.sendMessage("§6Tapez la nouvelle raison de blocage :");
        player.sendMessage("§7Exemple: §eComportement toxique en jeu");
        player.sendMessage("§7Exemple: §eSpam de messages répétés");
        player.sendMessage("§7Exemple: §eLanguage inapproprié");
        player.sendMessage("");
        player.sendMessage("§7Tapez §c'annuler'§7 pour annuler la modification");

        plugin.getLogger().info("Mode modification raison activé pour " + player.getName()
                + " -> " + blockedPlayerName);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingBlockReasons.containsKey(playerUUID)) {
                pendingBlockReasons.remove(playerUUID);
                player.sendMessage("§c⏰ §7Modification de raison expirée - §cAucun changement effectué");
            }
        }, 20L * 60);
    }

    private void handleBlockReasonChange(final AsyncPlayerChatEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();
        final String rawMessage = event.getMessage();
        final String newReason = rawMessage == null ? "" : rawMessage.trim();

        final UUID blockedPlayerUUID = pendingBlockReasons.remove(playerUUID);
        if (blockedPlayerUUID == null) {
            plugin.getLogger().warning("Aucune cible pour la modification de raison de " + player.getName());
            return;
        }

        final String blockedPlayerName = getPlayerName(blockedPlayerUUID);

        plugin.getLogger().info("Tentative modification raison par " + player.getName()
                + " pour " + blockedPlayerName + ": " + newReason);

        if ("annuler".equalsIgnoreCase(newReason) || "cancel".equalsIgnoreCase(newReason)) {
            player.sendMessage("§c❌ Modification de raison annulée");
            player.sendMessage("§7Aucun changement n'a été effectué");
            return;
        }

        if (newReason.length() < 3) {
            player.sendMessage("§c❌ La raison doit contenir au moins 3 caractères !");
            player.sendMessage("§7Tapez une raison plus détaillée pour justifier le blocage");
            activateBlockReasonMode(player, blockedPlayerUUID);
            return;
        }

        if (newReason.length() > 100) {
            player.sendMessage("§c❌ La raison ne peut pas dépasser 100 caractères !");
            player.sendMessage("§7Actuel: " + newReason.length() + " caractères");
            player.sendMessage("§7Tapez une raison plus courte et concise");
            activateBlockReasonMode(player, blockedPlayerUUID);
            return;
        }

        final BlockedPlayersManager blockedPlayersManager = plugin.getBlockedPlayersManager();
        if (blockedPlayersManager == null
                || !blockedPlayersManager.isPlayerBlocked(playerUUID, blockedPlayerUUID)) {
            player.sendMessage("§c❌ Ce joueur n'est plus bloqué !");
            player.sendMessage("§7Impossible de modifier la raison d'un joueur non-bloqué");
            return;
        }

        player.sendMessage("§e⏳ Mise à jour de la raison en cours...");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final boolean success = blockedPlayersManager.updateBlockReason(playerUUID, blockedPlayerUUID, newReason);

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§a✅ §2Raison de blocage mise à jour avec succès !");
                    player.sendMessage("§7Joueur: §c" + blockedPlayerName);
                    player.sendMessage("§7Nouvelle raison: §f\"" + newReason + "\"");
                    player.sendMessage("§7Mise à jour: §a" + getCurrentTimestamp());

                    player.playSound(player.getLocation(), "block.note_block.chime", 0.7f, 1.2f);

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        try {
                            player.sendMessage("§7Réouverture du menu des joueurs bloqués...");
                            new BlockedPlayersMenu(plugin, friendsManager, player).open();
                        } catch (final Exception exception) {
                            plugin.getLogger().warning("Erreur réouverture menu bloqués: " + exception.getMessage());
                            player.sendMessage("§7Utilisez §e/friends blocked§7 pour voir vos joueurs bloqués");
                        }
                    }, 40L);
                } else {
                    player.sendMessage("§c❌ §4Erreur lors de la mise à jour de la raison !");
                    player.sendMessage("§7La base de données n'a pas pu être mise à jour");
                    player.sendMessage("§7Veuillez réessayer plus tard ou contacter un administrateur");
                    player.playSound(player.getLocation(), "block.note_block.bass", 0.7f, 0.8f);
                }
            });
        });
    }

    public boolean isInBlockReasonMode(final Player player) {
        return player != null && pendingBlockReasons.containsKey(player.getUniqueId());
    }

    public void cancelBlockReasonMode(final Player player) {
        if (player == null) {
            return;
        }
        final UUID removed = pendingBlockReasons.remove(player.getUniqueId());
        if (removed != null) {
            player.sendMessage("§c❌ Modification de raison annulée pour " + getPlayerName(removed));
        }
    }

    public UUID getCurrentBlockReasonTarget(final Player player) {
        if (player == null) {
            return null;
        }
        return pendingBlockReasons.get(player.getUniqueId());
    }

    private void handlePrivateMessageInput(final Player player, final String rawInput) {
        final UUID playerUuid = player.getUniqueId();
        final String friendName = messageTargets.remove(playerUuid);

        if (friendName == null) {
            return;
        }

        final String message = rawInput == null ? "" : rawInput.trim();
        if (message.equalsIgnoreCase("annuler") || message.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Message annulé"));
            return;
        }

        if (message.isEmpty()) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ Message vide ignoré"));
            return;
        }

        final Player target = Bukkit.getPlayerExact(friendName);
        if (target == null) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§c❌ " + friendName + " n'est plus en ligne"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            final String formatted = "§d✉ §5" + player.getName() + "§7: §f" + message;
            target.sendMessage(formatted);
            target.playSound(target.getLocation(), "entity.experience_orb.pickup", 0.6f, 1.5f);
            player.sendMessage("§d✉ §7Vous → §5" + friendName + "§7: §f" + message);
            player.playSound(player.getLocation(), "block.note_block.pling", 0.8f, 1.6f);
        });
    }

    private void handleAddByName(final Player player, final String targetName) {
        if (targetName.length() < 3 || targetName.length() > 16) {
            player.sendMessage("§c❌ Nom d'utilisateur invalide ! (3-16 caractères requis)");
            return;
        }

        if (!targetName.matches("[a-zA-Z0-9_]+")) {
            player.sendMessage("§c❌ Le nom ne peut contenir que des lettres, chiffres et _ !");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final UUID targetUUID = getPlayerUUID(targetName);

            if (targetUUID == null) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    player.sendMessage("§c❌ Joueur '" + targetName + "' introuvable !");
                    player.sendMessage("§7Vérifiez l'orthographe du nom d'utilisateur");
                });
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> processFriendRequest(player, targetUUID, targetName));
        });
    }

    private void handleAddByCode(final Player player, final String rawInput) {
        final FriendCodeManager friendCodeManager = plugin.getFriendCodeManager();
        if (friendCodeManager == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§c❌ Le système de codes d'amis est indisponible."));
            return;
        }

        final String normalized = normalizeFriendCode(rawInput);
        if (normalized == null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§c❌ Format de code invalide !");
                player.sendMessage("§7Le format correct est §dXXXX-YYYY§7.");
            });
            return;
        }

        final UUID senderUuid = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            final UUID targetUuid = friendCodeManager.getPlayerByCode(normalized);
            if (targetUuid == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§c❌ Code d'ami invalide ou inexistant !"));
                return;
            }

            if (targetUuid.equals(senderUuid)) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§c❌ Vous ne pouvez pas utiliser votre propre code !"));
                return;
            }

            final String targetName = fetchPlayerName(targetUuid);
            if (targetName == null) {
                Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§c❌ Impossible de trouver le joueur associé à ce code."));
                return;
            }

        Bukkit.getScheduler().runTask(plugin, () ->
                processFriendRequest(player, targetUuid, targetName));
        });
    }

    private String getPlayerName(final UUID playerUUID) {
        if (playerUUID == null) {
            return "Joueur-inconnu";
        }

        final Player onlinePlayer = Bukkit.getPlayer(playerUUID);
        if (onlinePlayer != null) {
            return onlinePlayer.getName();
        }

        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager != null) {
            try {
                final String databaseName = databaseManager.getPlayerName(playerUUID);
                if (databaseName != null && !databaseName.isBlank()) {
                    return databaseName.trim();
                }
            } catch (final Exception exception) {
                plugin.getLogger().warning("Erreur récupération nom pour " + playerUUID + ": "
                        + exception.getMessage());
            }
        }

        try {
            final String offlineName = Bukkit.getOfflinePlayer(playerUUID).getName();
            if (offlineName != null && !offlineName.isBlank()) {
                return offlineName;
            }
        } catch (final Exception exception) {
            plugin.getLogger().warning("Erreur OfflinePlayer pour " + playerUUID + ": " + exception.getMessage());
        }

        return "Joueur-" + playerUUID.toString().substring(0, 8);
    }

    private String getCurrentTimestamp() {
        final java.time.LocalDateTime now = java.time.LocalDateTime.now();
        final java.time.format.DateTimeFormatter formatter =
                java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return now.format(formatter);
    }

    private void processFriendRequest(final Player sender, final UUID targetUUID, final String targetName) {
        final UUID senderUUID = sender.getUniqueId();

        if (targetUUID.equals(senderUUID)) {
            sender.sendMessage("§c❌ Vous ne pouvez pas vous ajouter vous-même !");
            return;
        }

        friendsManager.getFriends(sender)
                .thenCompose(friends -> {
                    if (isAlreadyFriend(friends, targetUUID)) {
                        Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Vous êtes déjà amis avec §e" + targetName + "§c !"));
                        return CompletableFuture.completedFuture(null);
                    }

                    return friendsManager.getPendingRequests(sender).thenCompose(requests -> {
                        if (hasIncomingRequest(requests, targetUUID)) {
                            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Une demande de §e" + targetName + "§c est déjà en attente !"));
                            return CompletableFuture.completedFuture(null);
                        }

                        return hasOutgoingRequest(sender, targetUUID).thenCompose(hasOutgoing -> {
                            if (hasOutgoing) {
                                Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Vous avez déjà envoyé une demande à §e" + targetName + "§c !"));
                                return CompletableFuture.completedFuture(null);
                            }

                            return friendsManager.sendFriendRequest(sender, targetName, "Demande d'ami");
                        });
                    });
                })
                .thenAccept(success -> {
                    if (success == null) {
                        return;
                    }

                    if (success) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            sender.sendMessage("§a✅ Demande d'ami envoyée à §2" + targetName + "§a !");
                            sender.sendMessage("§7La demande expire dans §a7 jours");

                            final Player target = Bukkit.getPlayer(targetUUID);
                            if (target != null) {
                                target.sendMessage("§e📬 §6" + sender.getName() + "§e vous a envoyé une demande d'ami !");
                                target.sendMessage("§7Tapez §a/friend accept " + sender.getName() + "§7 pour accepter");
                                target.sendMessage("§7Ou ouvrez le menu amis §a/friends");
                                target.playSound(target.getLocation(), "entity.experience_orb.pickup", 1.0f, 1.2f);
                            }

                            if (plugin.getMenuUpdateManager() != null) {
                                plugin.getMenuUpdateManager().forceUpdate(sender);
                                if (target != null) {
                                    plugin.getMenuUpdateManager().forceUpdate(target);
                                }
                            }
                        });
                        return;
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        sender.sendMessage("§c❌ Erreur lors de l'envoi de la demande !");
                        sender.sendMessage("§7Veuillez réessayer plus tard");
                    });
                })
                .exceptionally(exception -> {
                    plugin.getLogger().severe("Erreur lors de l'envoi de demande d'ami: " + exception.getMessage());
                    Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage("§c❌ Erreur lors de l'envoi de la demande !"));
                    return null;
                });
    }

    public void enableAddMode(final Player player, final String mode) {
        final UUID playerUUID = player.getUniqueId();
        pendingRequests.put(playerUUID, mode);

        switch (mode) {
            case MODE_SEARCH_PLAYER_NAME -> {
                player.sendMessage("§e🔍 §6Tapez le nom exact du joueur à ajouter:");
                player.sendMessage("§7Exemple: §aSteve §7ou §aPlayer123");
                player.sendMessage("§7Tapez §c'annuler'§7 pour annuler");
            }
            case MODE_FRIEND_CODE -> {
                player.sendMessage("§d💾 Ajout via code d'ami activé !");
                player.sendMessage("§7Saisissez un code au format §dXXXX-YYYY§7.");
                player.sendMessage("§7Tapez §c'annuler'§7 pour annuler");

                final FriendCodeManager codeManager = plugin.getFriendCodeManager();
                if (codeManager == null) {
                    player.sendMessage("§c❌ Le système de codes d'amis est indisponible.");
                    break;
                }

                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    String code = codeManager.getPlayerCode(playerUUID);
                    if (code == null) {
                        code = codeManager.generateUniqueCode(playerUUID);
                    }
                    final String resolvedCode = code;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!player.isOnline()) {
                            return;
                        }
                        if (!MODE_FRIEND_CODE.equals(pendingRequests.get(playerUUID))) {
                            return;
                        }
                        if (resolvedCode != null) {
                            player.sendMessage("§d▸ Votre code : §f" + resolvedCode);
                        } else {
                            player.sendMessage("§c❌ Impossible de récupérer votre code d'ami pour le moment.");
                        }
                    });
                });
            }
            default -> player.sendMessage("§c❌ Mode d'ajout non reconnu !");
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(playerUUID)) {
                pendingRequests.remove(playerUUID);
                player.sendMessage("§c⏰ Temps d'attente écoulé - Ajout d'ami annulé");
            }
        }, 20L * 30);
    }

    public void enableFriendCodeMode(final Player player) {
        enableAddMode(player, MODE_FRIEND_CODE);
    }

    private String normalizeFriendCode(final String input) {
        if (input == null) {
            return null;
        }

        String sanitized = input.trim().toUpperCase(Locale.ROOT).replace(" ", "");
        if (sanitized.length() == 8 && !sanitized.contains("-")) {
            sanitized = sanitized.substring(0, 4) + "-" + sanitized.substring(4);
        }

        if (!sanitized.matches("[A-Z0-9]{4}-[A-Z0-9]{4}")) {
            return null;
        }

        return sanitized;
    }

    private String fetchPlayerName(final UUID uuid) {
        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager == null) {
            return null;
        }

        final String query = "SELECT username FROM players WHERE uuid = ? ORDER BY last_seen DESC LIMIT 1";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(query)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("username");
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Erreur recherche nom pour UUID " + uuid + ": " + exception.getMessage());
        }

        return null;
    }

    private UUID getPlayerUUID(final String playerName) {
        final Player onlinePlayer = Bukkit.getPlayerExact(playerName);
        if (onlinePlayer != null) {
            return onlinePlayer.getUniqueId();
        }

        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager != null) {
            final UUID databaseUuid = databaseManager.getPlayerUUID(playerName);
            if (databaseUuid != null) {
                return databaseUuid;
            }
        }

        try {
            return Bukkit.getOfflinePlayer(playerName).getUniqueId();
        } catch (final Exception exception) {
            plugin.getLogger().warning("Impossible de trouver l'UUID pour " + playerName + ": " + exception.getMessage());
            return null;
        }
    }

    public boolean isInAddMode(final Player player) {
        return pendingRequests.containsKey(player.getUniqueId());
    }

    public void cancelAddMode(final Player player) {
        if (pendingRequests.remove(player.getUniqueId()) != null) {
            player.sendMessage("§c❌ Ajout d'ami annulé");
        }
    }

    private boolean isAlreadyFriend(final List<FriendData> friends, final UUID targetUUID) {
        if (friends == null) {
            return false;
        }
        final String target = targetUUID.toString();
        return friends.stream().anyMatch(data -> target.equalsIgnoreCase(data.getUuid()));
    }

    private boolean hasIncomingRequest(final List<FriendRequest> requests, final UUID targetUUID) {
        if (requests == null) {
            return false;
        }
        final String target = targetUUID.toString();
        return requests.stream().anyMatch(request -> target.equalsIgnoreCase(request.getSenderUuid()));
    }

    private CompletableFuture<Boolean> hasOutgoingRequest(final Player sender, final UUID targetUUID) {
        final Player target = Bukkit.getPlayer(targetUUID);
        if (target == null) {
            return CompletableFuture.completedFuture(false);
        }

        return friendsManager.getPendingRequests(target).thenApply(requests -> {
            if (requests == null) {
                return false;
            }
            final String senderId = sender.getUniqueId().toString();
            return requests.stream().anyMatch(request -> senderId.equalsIgnoreCase(request.getSenderUuid()));
        });
    }
}
