package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.menus.ClanMenus;
import com.lobby.social.menus.FriendsMenus;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import com.lobby.utils.PlaceholderUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class ActionProcessor {

    private final LobbyPlugin plugin;

    public ActionProcessor(final LobbyPlugin plugin) {
        this.plugin = plugin;
    }

    public void processActions(final List<String> actions, final Player player, final NPC npc) {
        if (actions == null || actions.isEmpty() || player == null) {
            return;
        }
        for (final String action : actions) {
            if (action == null || action.isEmpty()) {
                continue;
            }
            processAction(action, player, npc);
        }
    }

    private void processAction(final String action, final Player player, final NPC npc) {
        final String trimmed = action.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        final String processed = replacePlaceholders(trimmed, player);
        if (startsWithIgnoreCase(trimmed, "[MESSAGE]")) {
            final String message = processed.substring(9).trim();
            MessageUtils.sendPrefixedMessage(player, message);
            return;
        }
        if (trimmed.equalsIgnoreCase("[FRIENDS_ONLINE]")) {
            FriendsMenus.openFriendsOnlineMenu(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[FRIEND_REQUESTS]")) {
            FriendsMenus.openFriendRequestsMenu(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[FRIEND_ADD]")) {
            ChatInputManager.startFriendAddFlow(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[TOGGLE_FRIEND_NOTIFICATIONS]")) {
            toggleAndRefresh(player, "friend_notifications", "friend_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[CYCLE_FRIEND_REQUESTS]")) {
            cycleAndRefresh(player, "friend_requests", "friend_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[CYCLE_FRIEND_VISIBILITY]")) {
            cycleAndRefresh(player, "friend_visibility", "friend_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[TOGGLE_FRIEND_AUTO_FAVORITES]")) {
            toggleAndRefresh(player, "friend_auto_favorites", "friend_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[TOGGLE_FRIEND_MESSAGES]")) {
            toggleAndRefresh(player, "friend_messages", "friend_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[GROUP_CREATE]")) {
            ChatInputManager.startGroupCreateFlow(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[TOGGLE_GROUP_AUTO_ACCEPT]")) {
            toggleAndRefresh(player, "group_auto_accept", "group_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[CYCLE_GROUP_VISIBILITY]")) {
            cycleAndRefresh(player, "group_visibility", "group_settings_menu");
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_MEMBERS]")) {
            ClanMenus.openClanMembersMenu(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_VAULT]")) {
            ClanMenus.openClanVaultMenu(player);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[TOGGLE_MEMBER_PERMISSION]")) {
            final String value = processed.substring("[TOGGLE_MEMBER_PERMISSION]".length()).trim();
            toggleMemberPermission(player, value);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[APPLY_MEMBER_PERMISSION_PRESET]")) {
            final String value = processed.substring("[APPLY_MEMBER_PERMISSION_PRESET]".length()).trim();
            applyMemberPreset(player, value);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_INVITE]")) {
            ChatInputManager.startClanInviteFlow(player);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_DELETE_CONFIRM]")) {
            handleClanDeleteConfirmation(player);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[SOUND]")) {
            final String soundName = processed.substring(7).trim();
            final Sound sound = resolveSound(soundName);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } else {
                LogUtils.warning(plugin, "Unknown sound in NPC action: " + soundName);
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[CLOSE]")) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[CLOSE_MENU]")) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) player::closeInventory);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[COMMAND]")) {
            final String command = processed.substring(9).trim();
            if (!command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[PLAYER_COMMAND]")) {
            final String command = processed.substring(15).trim();
            if (!command.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, (Runnable) () -> player.performCommand(command));
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[MENU]")) {
            final String menuId = processed.substring(6).trim();
            if (!menuId.isEmpty()) {
                final MenuManager menuManager = plugin.getMenuManager();
                if (menuManager != null) {
                    Bukkit.getScheduler().runTask(plugin, (Runnable) () -> {
                        menuManager.openMenu(player, menuId);
                    });
                } else {
                    LogUtils.warning(plugin, "Menu action requested but MenuManager is not available: " + menuId);
                }
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[OPEN_MENU]")) {
            final String menuId = processed.substring(10).trim();
            if (!menuId.isEmpty()) {
                final MenuManager menuManager = plugin.getMenuManager();
                if (menuManager != null) {
                    Bukkit.getScheduler().runTask(plugin, (Runnable) () -> menuManager.openMenu(player, menuId));
                } else {
                    LogUtils.warning(plugin, "Menu action requested but MenuManager is not available: " + menuId);
                }
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[COINS_ADD]")) {
            final String amountRaw = processed.substring(11).trim();
            parseAmountAndApply(amountRaw, value -> plugin.getEconomyManager().addCoins(player.getUniqueId(), value,
                    "NPC interaction"));
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[TOKENS_ADD]")) {
            final String amountRaw = processed.substring(12).trim();
            parseAmountAndApply(amountRaw, value -> plugin.getEconomyManager().addTokens(player.getUniqueId(), value,
                    "NPC interaction"));
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[SERVER_SEND]")) {
            final String server = processed.substring(12).trim();
            if (!server.isEmpty()) {
                sendToServer(player, server);
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[TELEPORT]")) {
            final String coordinates = processed.substring(10).trim();
            handleTeleport(coordinates, player, npc);
        }
    }

    public static void openClanDeleteConfirmation(final Player player) {
        final LobbyPlugin plugin = LobbyPlugin.getInstance();
        if (plugin == null) {
            return;
        }
        final var npcManager = plugin.getNpcManager();
        if (npcManager == null) {
            return;
        }
        final ActionProcessor processor = npcManager.getActionProcessor();
        if (processor == null) {
            return;
        }
        processor.handleClanDeleteConfirmation(player);
    }

    private void handleClanDeleteConfirmation(final Player player) {
        if (player == null) {
            return;
        }
        final ClanManager clanManager = plugin.getClanManager();
        if (clanManager == null) {
            return;
        }
        final Clan clan = clanManager.getPlayerClan(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§cVous n'êtes dans aucun clan.");
            return;
        }
        if (!clan.isLeader(player.getUniqueId())) {
            player.sendMessage("§cSeul le leader peut supprimer le clan.");
            return;
        }

        player.closeInventory();
        player.sendMessage("§c§l⚠ SUPPRESSION DE CLAN ⚠");
        player.sendMessage("§7Vous êtes sur le point de supprimer définitivement votre clan.");
        player.sendMessage("§7Cette action est §cIRRÉVERSIBLE§7 !");
        player.sendMessage("§r");
        player.sendMessage("§7Conséquences :");
        player.sendMessage("§8▸ §7Tous les membres seront expulsés");
        player.sendMessage("§8▸ §7Le trésor du clan sera perdu");
        player.sendMessage("§8▸ §7Toutes les données seront supprimées");
        player.sendMessage("§r");
        player.sendMessage("§7Tapez §c'SUPPRIMER'§7 pour confirmer");
        player.sendMessage("§7Tapez §a'annuler'§7 pour annuler");

        final var menuManager = plugin.getMenuManager();
        ChatInputManager.startInputFlow(player, inputRaw -> {
            final String input = inputRaw.trim();
            if (input.equalsIgnoreCase("SUPPRIMER")) {
                final boolean success = clanManager.deleteClan(player.getUniqueId());
                if (success) {
                    player.sendMessage("§c§lClan supprimé avec succès !");
                    player.sendMessage("§7Toutes les données ont été effacées.");
                } else {
                    player.sendMessage("§cErreur lors de la suppression du clan.");
                }
            } else if (input.equalsIgnoreCase("annuler")) {
                player.sendMessage("§aSuppression annulée.");
            } else {
                player.sendMessage("§cCommande non reconnue - Suppression annulée.");
            }

            if (menuManager != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> menuManager.openMenu(player, "clan_menu"), 40L);
            }
        }, () -> {
            player.sendMessage("§cTemps écoulé - Suppression annulée.");
            if (menuManager != null) {
                menuManager.openMenu(player, "clan_menu");
            }
        });
    }

    private void toggleAndRefresh(final Player player, final String key, final String menuId) {
        if (player == null || key == null) {
            return;
        }
        switch (key) {
            case "friend_notifications" -> {
                final var friendManager = plugin.getFriendManager();
                if (friendManager == null) {
                    return;
                }
                final boolean enabled = friendManager.toggleNotifications(player.getUniqueId());
                player.sendMessage(enabled
                        ? "§aNotifications d'amis activées"
                        : "§cNotifications d'amis désactivées");
                reopenMenu(player, menuId);
            }
            case "friend_auto_favorites" -> {
                final var friendManager = plugin.getFriendManager();
                if (friendManager == null) {
                    return;
                }
                final boolean enabled = friendManager.toggleAutoAcceptFavorites(player.getUniqueId());
                player.sendMessage(enabled
                        ? "§aAcceptation auto des favoris activée"
                        : "§cAcceptation auto des favoris désactivée");
                reopenMenu(player, menuId);
            }
            case "friend_messages" -> {
                final var friendManager = plugin.getFriendManager();
                if (friendManager == null) {
                    return;
                }
                final boolean enabled = friendManager.togglePrivateMessages(player.getUniqueId());
                player.sendMessage(enabled
                        ? "§aMessages privés autorisés"
                        : "§cMessages privés désactivés");
                reopenMenu(player, menuId);
            }
            case "group_auto_accept" -> {
                final var groupManager = plugin.getGroupManager();
                if (groupManager == null) {
                    return;
                }
                final boolean enabled = groupManager.toggleAutoAccept(player.getUniqueId());
                player.sendMessage(enabled
                        ? "§aInvitations automatiques activées"
                        : "§cInvitations automatiques désactivées");
                reopenMenu(player, menuId);
            }
            default -> {
            }
        }
    }

    private void cycleAndRefresh(final Player player, final String key, final String menuId) {
        if (player == null || key == null) {
            return;
        }
        switch (key) {
            case "friend_requests" -> {
                final var friendManager = plugin.getFriendManager();
                if (friendManager == null) {
                    return;
                }
                final String mode = friendManager.cycleRequestAcceptance(player.getUniqueId());
                player.sendMessage("§aDemandes d'amis: §f" + mode);
                reopenMenu(player, menuId);
            }
            case "friend_visibility" -> {
                final var friendManager = plugin.getFriendManager();
                if (friendManager == null) {
                    return;
                }
                final String visibility = friendManager.cycleFriendVisibility(player.getUniqueId());
                player.sendMessage("§aVisibilité: §f" + visibility);
                reopenMenu(player, menuId);
            }
            case "group_visibility" -> {
                final var groupManager = plugin.getGroupManager();
                if (groupManager == null) {
                    return;
                }
                final String visibility = groupManager.cycleGroupVisibility(player.getUniqueId());
                player.sendMessage("§aVisibilité du groupe: §f" + visibility);
                reopenMenu(player, menuId);
            }
            default -> {
            }
        }
    }

    private void toggleMemberPermission(final Player player, final String actionValue) {
        if (player == null || actionValue == null || actionValue.isBlank()) {
            return;
        }
        final String[] parts = actionValue.split("\\|");
        if (parts.length < 2) {
            player.sendMessage("§cAction de permission invalide.");
            return;
        }
        final UUID memberUuid = parseUuid(parts[0]);
        if (memberUuid == null) {
            player.sendMessage("§cMembre introuvable.");
            return;
        }
        final String permissionKey = parts[1].trim();
        if (permissionKey.isEmpty()) {
            player.sendMessage("§cPermission invalide.");
            return;
        }
        final String menuId = parts.length > 2 && !parts[2].trim().isEmpty()
                ? parts[2].trim()
                : "clan_member_permissions_menu";
        final ClanManager clanManager = plugin.getClanManager();
        if (clanManager == null) {
            return;
        }
        final boolean enabled = clanManager.toggleMemberPermission(player.getUniqueId(), memberUuid, permissionKey);
        final String name = formatPermissionName(permissionKey);
        player.sendMessage(enabled
                ? "§aPermission activée: §f" + name
                : "§cPermission désactivée: §f" + name);
        reopenMenu(player, menuId);
    }

    private void applyMemberPreset(final Player player, final String actionValue) {
        if (player == null || actionValue == null || actionValue.isBlank()) {
            return;
        }
        final String[] parts = actionValue.split("\\|");
        if (parts.length < 2) {
            player.sendMessage("§cPreset de permissions invalide.");
            return;
        }
        final UUID memberUuid = parseUuid(parts[0]);
        if (memberUuid == null) {
            player.sendMessage("§cMembre introuvable.");
            return;
        }
        final String presetKey = parts[1].trim();
        if (presetKey.isEmpty()) {
            player.sendMessage("§cPreset invalide.");
            return;
        }
        final String menuId = parts.length > 2 && !parts[2].trim().isEmpty()
                ? parts[2].trim()
                : "clan_member_permissions_menu";
        final ClanManager clanManager = plugin.getClanManager();
        if (clanManager == null) {
            return;
        }
        final boolean success = clanManager.applyPermissionPreset(player.getUniqueId(), memberUuid, presetKey);
        final String name = formatPresetName(presetKey);
        player.sendMessage(success
                ? "§aPreset appliqué: §f" + name
                : "§cImpossible d'appliquer le preset: §f" + name);
        reopenMenu(player, menuId);
    }

    private void reopenMenu(final Player player, final String menuId) {
        if (player == null || menuId == null || menuId.isBlank()) {
            return;
        }
        final MenuManager menuManager = plugin.getMenuManager();
        if (menuManager == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                menuManager.openMenu(player, menuId);
            }
        });
    }

    private UUID parseUuid(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private String formatPermissionName(final String permissionKey) {
        if (permissionKey == null || permissionKey.isBlank()) {
            return "Permission";
        }
        final String normalized = permissionKey.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "clan.invite" -> "Invitations";
            case "clan.kick" -> "Exclusions";
            case "clan.promote" -> "Promotions";
            case "clan.demote" -> "Rétrogradations";
            case "clan.manage_ranks" -> "Gestion des rangs";
            case "clan.withdraw", "clan.manage_bank" -> "Banque";
            case "clan.disband" -> "Dissolution";
            default -> {
                final String raw = permissionKey.contains(".")
                        ? permissionKey.substring(permissionKey.indexOf('.') + 1)
                        : permissionKey;
                final String lower = raw.replace('_', ' ').toLowerCase(Locale.ROOT);
                yield Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
            }
        };
    }

    private String formatPresetName(final String presetKey) {
        if (presetKey == null || presetKey.isBlank()) {
            return "Preset";
        }
        final String normalized = presetKey.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "default", "aucun", "none" -> "Défaut";
            case "moderateur", "moderator", "mod" -> "Modérateur";
            case "officier", "officer" -> "Officier";
            case "gestion", "manager" -> "Gestion";
            case "banquier", "banker" -> "Banquier";
            case "admin", "toutes", "all" -> "Administrateur";
            default -> {
                final String cleaned = normalized.replace('-', ' ').replace('_', ' ');
                final String[] parts = cleaned.split(" ");
                final StringBuilder builder = new StringBuilder();
                for (String part : parts) {
                    if (part.isEmpty()) {
                        continue;
                    }
                    builder.append(Character.toUpperCase(part.charAt(0)))
                            .append(part.substring(1));
                    builder.append(' ');
                }
                final String result = builder.toString().trim();
                yield result.isEmpty() ? presetKey : result;
            }
        };
    }

    private void parseAmountAndApply(final String raw, final java.util.function.LongConsumer consumer) {
        if (raw == null || raw.isEmpty()) {
            return;
        }
        try {
            final long value = Long.parseLong(raw);
            if (value > 0) {
                consumer.accept(value);
            }
        } catch (final NumberFormatException exception) {
            LogUtils.warning(plugin, "Invalid numeric value in NPC action: " + raw);
        }
    }

    private void handleTeleport(final String coordinates, final Player player, final NPC npc) {
        if (coordinates.isEmpty() || player.getWorld() == null) {
            return;
        }
        final String[] parts = coordinates.split(",");
        if (parts.length < 3) {
            LogUtils.warning(plugin, "Invalid teleport action for NPC '" + (npc != null ? npc.getData().name() : "unknown")
                    + "': " + coordinates);
            return;
        }
        try {
            final double x = Double.parseDouble(parts[0].trim());
            final double y = Double.parseDouble(parts[1].trim());
            final double z = Double.parseDouble(parts[2].trim());
            final Location target = new Location(player.getWorld(), x, y, z);
            player.teleport(target);
        } catch (final NumberFormatException exception) {
            LogUtils.warning(plugin, "Invalid teleport coordinates for NPC '" + (npc != null ? npc.getData().name()
                    : "unknown") + "': " + coordinates);
        }
    }

    private void sendToServer(final Player player, final String server) {
        if (player == null || server == null || server.isBlank()) {
            return;
        }
        final String target = server.trim();
        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (DataOutputStream dataOutputStream = new DataOutputStream(outputStream)) {
                dataOutputStream.writeUTF("Connect");
                dataOutputStream.writeUTF(target);
            }
            final byte[] payload = outputStream.toByteArray();
            player.sendPluginMessage(plugin, "BungeeCord", payload);
            player.sendPluginMessage(plugin, "bungeecord:main", payload);
        } catch (final IOException exception) {
            LogUtils.warning(plugin, "Failed to send player '" + player.getName() + "' to server '" + target + "': "
                    + exception.getMessage());
        }
    }

    private String replacePlaceholders(final String text, final Player player) {
        if (text == null) {
            return "";
        }
        return PlaceholderUtils.applyPlaceholders(plugin, text, player);
    }

    private boolean startsWithIgnoreCase(final String text, final String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private Sound resolveSound(final String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }
        final String trimmed = soundName.trim();
        final String lower = trimmed.toLowerCase(Locale.ROOT);

        final Sound namespacedMatch = lookupSound(lower);
        if (namespacedMatch != null) {
            return namespacedMatch;
        }

        if (lower.startsWith("minecraft:")) {
            final Sound minecraftMatch = lookupSound(lower.substring("minecraft:".length()));
            if (minecraftMatch != null) {
                return minecraftMatch;
            }
        }

        if (!lower.contains(".")) {
            final String dotted = lower.replace('_', '.');
            final Sound dottedMatch = lookupSound(dotted);
            if (dottedMatch != null) {
                return dottedMatch;
            }
        }

        try {
            return Sound.valueOf(trimmed.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }

    private Sound lookupSound(final String key) {
        try {
            final NamespacedKey directKey = NamespacedKey.fromString(key);
            if (directKey != null) {
                final Sound sound = Registry.SOUNDS.get(directKey);
                if (sound != null) {
                    return sound;
                }
            }
            if (key.indexOf(':') >= 0) {
                return null;
            }
            return Registry.SOUNDS.get(NamespacedKey.minecraft(key));
        } catch (final IllegalArgumentException ignored) {
            return null;
        }
    }
}
