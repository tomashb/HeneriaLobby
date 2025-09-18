package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

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
        if (startsWithIgnoreCase(trimmed, "[SOUND]")) {
            final String soundName = processed.substring(7).trim();
            try {
                final Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (final IllegalArgumentException exception) {
                LogUtils.warning(plugin, "Unknown sound in NPC action: " + soundName);
            }
            return;
        }
        if (startsWithIgnoreCase(trimmed, "[COMMAND]")) {
            final String command = processed.substring(9).trim();
            if (!command.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
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

    private String replacePlaceholders(final String text, final Player player) {
        if (text == null) {
            return "";
        }
        final var economyManager = plugin.getEconomyManager();
        final long coins = economyManager != null ? economyManager.getCoins(player.getUniqueId()) : 0L;
        final long tokens = economyManager != null ? economyManager.getTokens(player.getUniqueId()) : 0L;
        return text
                .replace("%player_name%", player.getName())
                .replace("%player_coins%", String.valueOf(coins))
                .replace("%player_tokens%", String.valueOf(tokens));
    }

    private boolean startsWithIgnoreCase(final String text, final String prefix) {
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
