package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.menus.MenuManager;
import com.lobby.menus.confirmation.ConfirmationManager;
import com.lobby.menus.confirmation.ConfirmationRequest;
import com.lobby.settings.PlayerSettings;
import com.lobby.settings.PlayerSettingsManager;
import com.lobby.settings.SettingType;
import com.lobby.settings.VisibilitySetting;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import com.lobby.utils.PlaceholderUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
        if (isDeprecatedSocialAction(trimmed)) {
            MessageUtils.sendPrefixedMessage(player, "§cLes fonctionnalités sociales sont actuellement indisponibles.");
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
        if (startsWithIgnoreCase(trimmed, "[SOUND]")) {
            final String soundName = processed.substring(7).trim();
            LogUtils.info(plugin, "Sound action ignored temporarily: " + (soundName.isEmpty() ? "<empty>" : soundName));
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
        if (player != null) {
            MessageUtils.sendPrefixedMessage(player, "§cLes fonctionnalités de clan sont actuellement indisponibles.");
        }
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

    private boolean isDeprecatedSocialAction(final String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        final String upper = action.toUpperCase(Locale.ROOT);
        return upper.startsWith("[FRIEND")
                || upper.startsWith("[TOGGLE_FRIEND")
                || upper.startsWith("[CYCLE_FRIEND")
                || upper.startsWith("[GROUP")
                || upper.startsWith("[TOGGLE_GROUP")
                || upper.startsWith("[CLAN")
                || upper.startsWith("[TOGGLE_MEMBER")
                || upper.startsWith("[APPLY_MEMBER");
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
        if (type == SettingType.FRIEND_REQUESTS) {
            player.sendMessage(MessageUtils.colorize("&7Cette restriction est maintenant active pour toutes les futures demandes."));
        }
    }

    private void applyVisibilitySetting(final Player player, final VisibilitySetting setting) {
        if (player == null || setting == null) {
            return;
        }

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
                    if (!online.equals(player)) {
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









    private String extractArgument(final String processed, final String token) {
        if (processed == null || token == null) {
            return "";
        }
        if (processed.length() <= token.length()) {
            return "";
        }
        final String argument = processed.substring(token.length()).trim();
        if (argument.startsWith("\"") && argument.endsWith("\"") && argument.length() >= 2) {
            return argument.substring(1, argument.length() - 1);
        }
        return argument;
    }

    private UUID resolvePlayerUuidByName(final String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return null;
        }
        final Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            return online.getUniqueId();
        }
        final DatabaseManager databaseManager = plugin.getDatabaseManager();
        if (databaseManager != null) {
            final String query = "SELECT uuid FROM players WHERE LOWER(username) = ?";
            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(query)) {
                statement.setString(1, targetName.toLowerCase(Locale.ROOT));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return UUID.fromString(resultSet.getString("uuid"));
                    }
                }
            } catch (final SQLException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Failed to resolve player '" + targetName + "'", exception);
            }
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.getUniqueId();
        }
        return null;
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
        LogUtils.info(plugin, "Sound resolution skipped (temporarily disabled): "
                + (soundName == null || soundName.isBlank() ? "<empty>" : soundName.trim()));
        return null;
    }

}
