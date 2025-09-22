package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.menus.confirmation.ConfirmationManager;
import com.lobby.menus.confirmation.ConfirmationRequest;
import com.lobby.settings.PlayerSettings;
import com.lobby.settings.PlayerSettingsManager;
import com.lobby.settings.SettingType;
import com.lobby.settings.VisibilitySetting;
import com.lobby.social.ChatInputManager;
import com.lobby.social.clans.Clan;
import com.lobby.social.clans.ClanManager;
import com.lobby.social.menus.ClanMenus;
import com.lobby.social.menus.FriendsMenus;
import com.lobby.social.friends.FriendManager;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        if (startsWithIgnoreCase(trimmed, "[SETTING_TOGGLE]")) {
            final String value = processed.substring("[SETTING_TOGGLE]".length()).trim();
            handleSettingToggle(player, value);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[SETTING_LANGUAGE]")) {
            final String language = processed.substring("[SETTING_LANGUAGE]".length()).trim();
            handleLanguageChange(player, language);
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[CONFIRM]")) {
            handleConfirmationAction(player, processed.substring("[CONFIRM]".length()).trim());
            return;
        }
        if (trimmed.equalsIgnoreCase("[CONFIRM_EXECUTE]")) {
            handleConfirmationExecution(player, true);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CONFIRM_CANCEL]")) {
            handleConfirmationExecution(player, false);
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
        if (trimmed.equalsIgnoreCase("[CLAN_PROMOTE_MEMBER]")) {
            handleClanRankChange(player, true);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_DEMOTE_MEMBER]")) {
            handleClanRankChange(player, false);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_KICK_MEMBER]")) {
            handleClanRemoval(player, false);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_BAN_MEMBER]")) {
            handleClanRemoval(player, true);
            return;
        }
        if (trimmed.equalsIgnoreCase("[CLAN_TRANSFER_LEADERSHIP]")) {
            handleClanLeadershipTransfer(player);
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
            if (sound == null) {
                LogUtils.warning(plugin, "Unknown sound in NPC action: " + soundName);
                return;
            }
            try {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (final Exception exception) {
                LogUtils.warning(plugin, "Failed to play sound '" + soundName + "' for player '"
                        + player.getName() + "': " + exception.getMessage());
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
                Bukkit.getScheduler().runTaskLater(plugin, (Runnable) () -> menuManager.openMenu(player, "clan_menu"), 40L);
            }
        }, () -> {
            player.sendMessage("§cTemps écoulé - Suppression annulée.");
            if (menuManager != null) {
                menuManager.openMenu(player, "clan_menu");
            }
        });
    }

    private void handleConfirmationExecution(final Player player, final boolean confirmed) {
        if (player == null) {
            return;
        }
        final ConfirmationManager confirmationManager = plugin.getConfirmationManager();
        if (confirmationManager == null) {
            LogUtils.warning(plugin, "Confirmation execution requested but ConfirmationManager is unavailable.");
            return;
        }
        confirmationManager.executeConfirmation(player, confirmed);
    }

    private void handleConfirmationAction(final Player player, final String parameterString) {
        if (player == null) {
            return;
        }
        final ConfirmationManager confirmationManager = plugin.getConfirmationManager();
        if (confirmationManager == null) {
            LogUtils.warning(plugin, "Confirmation requested but ConfirmationManager is unavailable.");
            return;
        }
        final Map<String, String> parameters = parseConfirmationParameters(parameterString);
        final ConfirmationRequest.Builder builder = ConfirmationRequest.builder();
        builder.templateId(firstNonBlank(parameters.get("template"), parameters.get("menu_id")));
        builder.previousMenuId(firstNonBlank(parameters.get("previous"), parameters.get("menu"), parameters.get("back"),
                parameters.get("previous_menu")));
        builder.actionDescription(firstNonBlank(parameters.get("description"), parameters.get("action"),
                parameters.get("text")));
        builder.actionTitle(firstNonBlank(parameters.get("title"), parameters.get("name")));
        builder.actionIcon(firstNonBlank(parameters.get("icon"), parameters.get("head"), parameters.get("skull")));

        splitValues(firstNonBlank(parameters.get("details"), parameters.get("detail"), parameters.get("info")))
                .forEach(builder::addDetail);

        List<String> confirmActions = new ArrayList<>(splitValues(parameters.get("confirm")));
        if (confirmActions.isEmpty() && parameters.isEmpty()
                && parameterString != null && !parameterString.isBlank()) {
            confirmActions = new ArrayList<>(splitValues(parameterString));
        }
        if (confirmActions.isEmpty()) {
            LogUtils.warning(plugin, "Confirmation action opened without any confirm actions defined.");
        } else {
            confirmActions.forEach(builder::addConfirmAction);
        }

        splitValues(parameters.get("cancel")).forEach(builder::addCancelAction);

        confirmationManager.openConfirmation(player, builder.build());
    }

    private Map<String, String> parseConfirmationParameters(final String parameterString) {
        final Map<String, String> parameters = new HashMap<>();
        if (parameterString == null || parameterString.isBlank()) {
            return parameters;
        }
        final String[] segments = parameterString.split(";");
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            final int separator = segment.indexOf('=');
            if (separator <= 0 || separator >= segment.length() - 1) {
                continue;
            }
            final String key = segment.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            final String value = segment.substring(separator + 1).trim();
            parameters.put(key, value);
        }
        return parameters;
    }

    private List<String> splitValues(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split("\\|\\||\\n"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String firstNonBlank(final String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    private void handleSettingToggle(final Player player, final String value) {
        final PlayerSettingsManager manager = plugin.getPlayerSettingsManager();
        if (manager == null || player == null || value == null || value.isBlank()) {
            return;
        }

        final SettingType type;
        try {
            type = SettingType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException exception) {
            LogUtils.warning(plugin, "Unknown setting toggle action: " + value);
            return;
        }

        manager.toggleSetting(player.getUniqueId(), type);
        final PlayerSettings settings = manager.getPlayerSettings(player.getUniqueId());
        sendSettingChangeMessage(player, type, settings);

        if (type == SettingType.PLAYER_VISIBILITY) {
            applyVisibilitySetting(player, settings.getVisibilitySetting());
        }
        if (type == SettingType.MUSIC) {
            handleMusicToggle(player, settings.isMusic());
        }

        refreshCurrentMenu(player);
    }

    private void handleLanguageChange(final Player player, final String language) {
        final PlayerSettingsManager manager = plugin.getPlayerSettingsManager();
        if (manager == null || player == null || language == null || language.isBlank()) {
            return;
        }

        manager.setLanguage(player.getUniqueId(), language.trim().toLowerCase(Locale.ROOT));
        final PlayerSettings settings = manager.getPlayerSettings(player.getUniqueId());
        final String message = MessageUtils.colorize("&8[&dParamètres&8] &7Langue sélectionnée : "
                + settings.getLanguageDisplay());
        player.sendMessage(message);
        refreshCurrentMenu(player);
    }

    private void sendSettingChangeMessage(final Player player, final SettingType type, final PlayerSettings settings) {
        final String prefix = "&8[&dParamètres&8] &7";
        final String message = switch (type) {
            case PRIVATE_MESSAGES -> prefix + "Messages privés " + settings.getPrivateMessagesDisplay() + "&7 !";
            case FRIEND_REQUESTS -> prefix + "Demandes d'amis : " + settings.getFriendRequestsDisplay() + "&7 !";
            case GROUP_REQUESTS -> prefix + "Demandes de groupe : " + settings.getGroupRequestsDisplay() + "&7 !";
            case PLAYER_VISIBILITY -> prefix + "Visibilité : " + settings.getVisibilityDisplay() + "&7 !";
            case UI_SOUNDS -> prefix + "Sons d'interface " + settings.getUiSoundsDisplay() + "&7 !";
            case PARTICLES -> prefix + "Effets de particules " + settings.getParticlesDisplay() + "&7 !";
            case MUSIC -> prefix + "Musique d'ambiance " + settings.getMusicDisplay() + "&7 !";
            case FRIEND_NOTIFICATIONS -> prefix + "Notifications d'amis " + settings.getFriendNotificationsDisplay() + "&7 !";
            case CLAN_NOTIFICATIONS -> prefix + "Notifications de clan " + settings.getClanNotificationsDisplay() + "&7 !";
            case SYSTEM_NOTIFICATIONS -> prefix + "Notifications système " + settings.getSystemNotificationsDisplay() + "&7 !";
        };
        player.sendMessage(MessageUtils.colorize(message));
    }

    private void applyVisibilitySetting(final Player player, final VisibilitySetting setting) {
        if (player == null || setting == null) {
            return;
        }

        final FriendManager friendManager = plugin.getFriendManager();
        switch (setting) {
            case EVERYONE -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        player.showPlayer(plugin, online);
                    }
                }
            }
            case FRIENDS_ONLY -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (online.equals(player)) {
                        continue;
                    }
                    final boolean isFriend = friendManager != null
                            && friendManager.areFriends(player.getUniqueId(), online.getUniqueId());
                    if (isFriend) {
                        player.showPlayer(plugin, online);
                    } else {
                        player.hidePlayer(plugin, online);
                    }
                }
            }
            case NOBODY -> {
                for (Player online : Bukkit.getOnlinePlayers()) {
                    if (!online.equals(player)) {
                        player.hidePlayer(plugin, online);
                    }
                }
            }
        }
    }

    private void handleMusicToggle(final Player player, final boolean enabled) {
        final String message = enabled
                ? "&7♪ Musique d'ambiance démarrée..."
                : "&7♪ Musique d'ambiance arrêtée.";
        player.sendMessage(MessageUtils.colorize(message));
    }

    private void refreshCurrentMenu(final Player player) {
        final MenuManager menuManager = plugin.getMenuManager();
        if (menuManager == null) {
            return;
        }
        menuManager.getOpenMenu(player.getUniqueId()).ifPresent(menu ->
                Bukkit.getScheduler().runTask(plugin, () -> menu.open(player)));
    }

    private void handleClanRankChange(final Player player, final boolean promote) {
        if (player == null) {
            return;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (clanManager == null || placeholderManager == null) {
            player.sendMessage("§cAction indisponible pour le moment.");
            return;
        }
        final UUID target = placeholderManager.getClanPermissionTarget(player.getUniqueId());
        if (target == null) {
            player.sendMessage("§cAucun membre sélectionné.");
            return;
        }
        final boolean success = promote
                ? clanManager.promotePlayer(player.getUniqueId(), target)
                : clanManager.demotePlayer(player.getUniqueId(), target);
        final String targetName = resolvePlayerName(target);
        if (success) {
            player.sendMessage(promote
                    ? "§a" + targetName + " a été promu."
                    : "§e" + targetName + " a été rétrogradé.");
        } else {
            player.sendMessage(promote
                    ? "§cImpossible de promouvoir " + targetName + "."
                    : "§cImpossible de rétrograder " + targetName + ".");
        }
        reopenClanManagementMenu(player);
    }

    private void handleClanRemoval(final Player player, final boolean ban) {
        if (player == null) {
            return;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (clanManager == null || placeholderManager == null) {
            player.sendMessage("§cAction indisponible pour le moment.");
            return;
        }
        final UUID target = placeholderManager.getClanPermissionTarget(player.getUniqueId());
        if (target == null) {
            player.sendMessage("§cAucun membre sélectionné.");
            return;
        }
        final boolean success = ban
                ? clanManager.banMember(player.getUniqueId(), target)
                : clanManager.kickMember(player.getUniqueId(), target);
        final String targetName = resolvePlayerName(target);
        if (success) {
            player.sendMessage(ban
                    ? "§c" + targetName + " a été banni du clan."
                    : "§c" + targetName + " a été expulsé du clan.");
            placeholderManager.clearClanPermissionTarget(player.getUniqueId());
            openClanMembersMenu(player);
        } else {
            player.sendMessage(ban
                    ? "§cImpossible de bannir " + targetName + "."
                    : "§cImpossible d'expulser " + targetName + ".");
            reopenClanManagementMenu(player);
        }
    }

    private void handleClanLeadershipTransfer(final Player player) {
        if (player == null) {
            return;
        }
        final ClanManager clanManager = plugin.getClanManager();
        final var placeholderManager = plugin.getSocialPlaceholderManager();
        if (clanManager == null || placeholderManager == null) {
            player.sendMessage("§cAction indisponible pour le moment.");
            return;
        }
        final UUID target = placeholderManager.getClanPermissionTarget(player.getUniqueId());
        if (target == null) {
            player.sendMessage("§cAucun membre sélectionné.");
            return;
        }
        final boolean success = clanManager.transferLeadership(player.getUniqueId(), target);
        final String targetName = resolvePlayerName(target);
        if (success) {
            player.sendMessage("§aLe leadership a été transféré à §e" + targetName + "§a.");
            placeholderManager.clearClanPermissionTarget(player.getUniqueId());
            openClanMembersMenu(player);
        } else {
            player.sendMessage("§cImpossible de transférer le leadership à " + targetName + ".");
            reopenClanManagementMenu(player);
        }
    }

    private void reopenClanManagementMenu(final Player player) {
        reopenMenu(player, "clan_member_management_menu");
    }

    private void openClanMembersMenu(final Player player) {
        if (player == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> ClanMenus.openClanMembersMenu(player));
    }

    private void reopenMenu(final Player player, final String menuId) {
        if (player == null || menuId == null || menuId.isBlank()) {
            return;
        }
        final MenuManager menuManager = plugin.getMenuManager();
        if (menuManager == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, (Runnable) () -> {
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

    private String resolvePlayerName(final UUID uuid) {
        if (uuid == null) {
            return "Inconnu";
        }
        final Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        final var offline = Bukkit.getOfflinePlayer(uuid);
        final String name = offline.getName();
        return name != null ? name : uuid.toString();
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
        if (soundName == null) {
            return null;
        }

        final String trimmed = soundName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        final String sanitized = sanitizeSoundInput(trimmed);
        final String normalized = normalizeSoundName(sanitized);
        boolean fallbackDueToUnknown = false;

        final Sound directMatch = lookupSound(sanitized.toLowerCase(Locale.ROOT));
        if (directMatch != null) {
            return directMatch;
        }

        if (sanitized.indexOf(':') >= 0) {
            final int separator = sanitized.indexOf(':');
            final String namespace = sanitized.substring(0, separator);
            final String value = sanitized.substring(separator + 1).replace('_', '.');
            final Sound namespacedDotted = lookupSound(namespace + ":" + value);
            if (namespacedDotted != null) {
                return namespacedDotted;
            }
        }

        final Sound normalizedMatch = lookupSound(normalized.toLowerCase(Locale.ROOT));
        if (normalizedMatch != null) {
            return normalizedMatch;
        }

        if (!normalized.contains(":")) {
            final String dotted = normalized.toLowerCase(Locale.ROOT).replace('_', '.');
            final Sound dottedMatch = lookupSound(dotted);
            if (dottedMatch != null) {
                return dottedMatch;
            }

            try {
                final String enumKey = normalized.replace('.', '_');
                return Sound.valueOf(enumKey);
            } catch (final IllegalArgumentException exception) {
                LogUtils.warning(plugin, "Unknown sound in NPC action: '" + soundName + "'. "
                        + exception.getMessage());
                fallbackDueToUnknown = true;
            } catch (final Exception exception) {
                LogUtils.severe(plugin, "Unexpected error while resolving sound '" + soundName + "'.",
                        exception);
                fallbackDueToUnknown = true;
            }
        }

        if (!fallbackDueToUnknown) {
            LogUtils.warning(plugin, "Unknown sound in NPC action: '" + soundName
                    + "'. Using default fallback sound.");
        }
        return getDefaultSound();
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

    private String sanitizeSoundInput(final String soundName) {
        final String replaced = soundName.replace(' ', '_').replace('-', '_');
        final int separatorIndex = replaced.indexOf(':');
        if (separatorIndex >= 0) {
            final String namespace = replaced.substring(0, separatorIndex).toLowerCase(Locale.ROOT);
            final String value = replaced.substring(separatorIndex + 1).toLowerCase(Locale.ROOT);
            return namespace + ":" + value;
        }
        return replaced;
    }

    private String normalizeSoundName(final String soundName) {
        final String base = soundName.contains(":")
                ? soundName.substring(soundName.indexOf(':') + 1)
                : soundName;
        final String upper = base.toUpperCase(Locale.ROOT);
        return switch (upper) {
            case "CLICK", "BUTTON_CLICK" -> "UI_BUTTON_CLICK";
            case "PLING", "NOTE_PLING" -> "BLOCK_NOTE_BLOCK_PLING";
            case "POP" -> "ENTITY_ITEM_PICKUP";
            case "VILLAGER_YES" -> "ENTITY_VILLAGER_YES";
            case "VILLAGER_NO" -> "ENTITY_VILLAGER_NO";
            case "ANVIL_USE" -> "BLOCK_ANVIL_USE";
            case "CHEST_OPEN" -> "BLOCK_CHEST_OPEN";
            case "CHEST_CLOSE" -> "BLOCK_CHEST_CLOSE";
            case "DOOR_OPEN" -> "BLOCK_WOODEN_DOOR_OPEN";
            case "DOOR_CLOSE" -> "BLOCK_WOODEN_DOOR_CLOSE";
            default -> upper;
        };
    }

    private Sound getDefaultSound() {
        try {
            return Sound.UI_BUTTON_CLICK;
        } catch (final Exception exception) {
            LogUtils.severe(plugin, "Failed to resolve default fallback sound.", exception);
            return null;
        }
    }
}
