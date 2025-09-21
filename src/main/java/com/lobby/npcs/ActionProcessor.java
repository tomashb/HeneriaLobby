package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.menus.MenuManager;
import com.lobby.social.ChatInputManager;
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
        if (trimmed.equalsIgnoreCase("[GROUP_CREATE]")) {
            ChatInputManager.startGroupCreateFlow(player);
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
        if (trimmed.equalsIgnoreCase("[CLAN_INVITE]")) {
            ChatInputManager.startClanInviteFlow(player);
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
