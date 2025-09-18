package com.lobby.commands;

import com.lobby.LobbyPlugin;
import com.lobby.data.PlayerData;
import com.lobby.economy.EconomyManager;
import com.lobby.holograms.HologramManager;
import com.lobby.npcs.NPCManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class AdminCommands implements CommandExecutor, TabExecutor {

    private final LobbyPlugin plugin;
    private final EconomyManager economyManager;
    private final HologramCommands hologramCommands;
    private final NPCCommands npcCommands;

    public AdminCommands(final LobbyPlugin plugin, final EconomyManager economyManager,
                         final HologramManager hologramManager, final NPCManager npcManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.hologramCommands = new HologramCommands(hologramManager);
        this.npcCommands = new NPCCommands(npcManager);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "economy.admin.usage", Collections.emptyMap());
            return true;
        }

        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("holo") || subCommand.equals("hologram")) {
            final String[] hologramArgs = Arrays.copyOfRange(args, 1, args.length);
            hologramCommands.handle(sender, hologramArgs);
            return true;
        }

        if (subCommand.equals("npc")) {
            final String[] npcArgs = Arrays.copyOfRange(args, 1, args.length);
            npcCommands.handle(sender, npcArgs);
            return true;
        }

        if (!sender.hasPermission("lobby.admin.economy")) {
            MessageUtils.sendPrefixedMessage(sender, plugin.getConfigManager().getMessagesConfig().getString("no_permission"));
            return true;
        }

        return switch (subCommand) {
            case "give" -> handleGive(sender, args);
            case "take" -> handleTake(sender, args);
            case "balance" -> handleBalance(sender, args);
            default -> {
                MessageUtils.sendConfigMessage(sender, "economy.admin.usage", Collections.emptyMap());
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        if (args.length >= 1) {
            final String first = args[0].toLowerCase(Locale.ROOT);
            if (first.equals("holo") || first.equals("hologram")) {
                final String[] hologramArgs = Arrays.copyOfRange(args, 1, args.length);
                return hologramCommands.tabComplete(sender, hologramArgs);
            }
        }

        if (args.length == 1) {
            final List<String> options = List.of("give", "take", "balance", "holo", "hologram", "npc");
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args.length == 2) {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "give", "take" -> List.of("coins", "tokens");
                case "balance" -> completePlayerNames(args[1]);
                case "npc" -> npcCommands.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
                default -> Collections.emptyList();
            };
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("npc")) {
                return npcCommands.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take")) {
                return completePlayerNames(args[2]);
            }
        }

        if (args.length > 3 && args[0].equalsIgnoreCase("npc")) {
            return npcCommands.tabComplete(sender, Arrays.copyOfRange(args, 1, args.length));
        }

        if (args.length == 4 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("take"))) {
            return List.of("100", "250", "1000", "5000");
        }

        return Collections.emptyList();
    }

    private boolean handleGive(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            MessageUtils.sendConfigMessage(sender, "economy.admin.usage", Collections.emptyMap());
            return true;
        }
        final String currency = args[1].toLowerCase(Locale.ROOT);
        final String targetName = args[2];
        final long amount = parseAmount(sender, args[3]);
        if (amount <= 0) {
            return true;
        }

        final Optional<PlayerData> targetData = resolvePlayerData(targetName);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "economy.player_not_found", Map.of("player", targetName));
            return true;
        }

        final PlayerData data = targetData.get();
        switch (currency) {
            case "coins" -> {
                final long before = economyManager.getCoins(data.uuid());
                economyManager.addCoins(data.uuid(), amount, "Admin give");
                final long after = economyManager.getCoins(data.uuid());
                final long delta = after - before;
                if (delta <= 0) {
                    MessageUtils.sendConfigMessage(sender, "economy.limit_reached", Map.of("player", data.username()));
                    return true;
                }
                notifyAdminAndPlayer(sender, data, delta, after, true, true);
            }
            case "tokens" -> {
                final long before = economyManager.getTokens(data.uuid());
                economyManager.addTokens(data.uuid(), amount, "Admin give");
                final long after = economyManager.getTokens(data.uuid());
                final long delta = after - before;
                if (delta <= 0) {
                    MessageUtils.sendConfigMessage(sender, "economy.limit_reached", Map.of("player", data.username()));
                    return true;
                }
                notifyAdminAndPlayer(sender, data, delta, after, true, false);
            }
            default -> MessageUtils.sendConfigMessage(sender, "economy.admin.invalid_currency", Map.of("currency", currency));
        }
        return true;
    }

    private boolean handleTake(final CommandSender sender, final String[] args) {
        if (args.length < 4) {
            MessageUtils.sendConfigMessage(sender, "economy.admin.usage", Collections.emptyMap());
            return true;
        }
        final String currency = args[1].toLowerCase(Locale.ROOT);
        final String targetName = args[2];
        final long amount = parseAmount(sender, args[3]);
        if (amount <= 0) {
            return true;
        }

        final Optional<PlayerData> targetData = resolvePlayerData(targetName);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "economy.player_not_found", Map.of("player", targetName));
            return true;
        }

        final PlayerData data = targetData.get();
        switch (currency) {
            case "coins" -> {
                final long before = economyManager.getCoins(data.uuid());
                economyManager.removeCoins(data.uuid(), amount, "Admin take");
                final long after = economyManager.getCoins(data.uuid());
                final long delta = before - after;
                if (delta <= 0) {
                    MessageUtils.sendConfigMessage(sender, "economy.limit_reached", Map.of("player", data.username()));
                    return true;
                }
                notifyAdminAndPlayer(sender, data, delta, after, false, true);
            }
            case "tokens" -> {
                final long before = economyManager.getTokens(data.uuid());
                economyManager.removeTokens(data.uuid(), amount, "Admin take");
                final long after = economyManager.getTokens(data.uuid());
                final long delta = before - after;
                if (delta <= 0) {
                    MessageUtils.sendConfigMessage(sender, "economy.limit_reached", Map.of("player", data.username()));
                    return true;
                }
                notifyAdminAndPlayer(sender, data, delta, after, false, false);
            }
            default -> MessageUtils.sendConfigMessage(sender, "economy.admin.invalid_currency", Map.of("currency", currency));
        }
        return true;
    }

    private boolean handleBalance(final CommandSender sender, final String[] args) {
        if (args.length < 2) {
            MessageUtils.sendConfigMessage(sender, "economy.admin.usage", Collections.emptyMap());
            return true;
        }
        final String targetName = args[1];
        final Optional<PlayerData> targetData = resolvePlayerData(targetName);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "economy.player_not_found", Map.of("player", targetName));
            return true;
        }
        final PlayerData data = targetData.get();
        MessageUtils.sendConfigMessage(sender, "economy.balance_other",
                Map.of("player", data.username(), "coins", formatNumber(data.coins()), "tokens", formatNumber(data.tokens())));
        return true;
    }

    private void notifyAdminAndPlayer(final CommandSender sender, final PlayerData target, final long delta, final long total,
                                      final boolean addition, final boolean coins) {
        final String formattedDelta = formatNumber(delta);
        final String formattedTotal = formatNumber(total);
        if (addition) {
            final String key = coins ? "economy.admin.coins_given" : "economy.admin.tokens_given";
            MessageUtils.sendConfigMessage(sender, key,
                    Map.of("player", target.username(), "amount", formattedDelta, "total", formattedTotal));
        } else {
            final String key = coins ? "economy.admin.coins_taken" : "economy.admin.tokens_taken";
            MessageUtils.sendConfigMessage(sender, key,
                    Map.of("player", target.username(), "amount", formattedDelta, "total", formattedTotal));
        }

        final Player online = Bukkit.getPlayer(target.uuid());
        if (online != null && online.isOnline()) {
            if (addition) {
                final String key = coins ? "economy.coins_added" : "economy.tokens_added";
                MessageUtils.sendConfigMessage(online, key,
                        Map.of("amount", formattedDelta, "total", formattedTotal));
            } else {
                final String key = coins ? "economy.coins_removed" : "economy.tokens_removed";
                MessageUtils.sendConfigMessage(online, key,
                        Map.of("amount", formattedDelta, "total", formattedTotal));
            }
        }
    }

    private Optional<PlayerData> resolvePlayerData(final String name) {
        final Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return Optional.of(economyManager.getPlayerData(online.getUniqueId()));
        }
        return economyManager.getPlayerDataByName(name);
    }

    private long parseAmount(final CommandSender sender, final String input) {
        try {
            final long value = Long.parseLong(input);
            if (value <= 0) {
                MessageUtils.sendConfigMessage(sender, "economy.invalid_amount", Map.of("amount", input));
                return -1;
            }
            return value;
        } catch (NumberFormatException exception) {
            MessageUtils.sendConfigMessage(sender, "economy.invalid_amount", Map.of("amount", input));
            return -1;
        }
    }

    private List<String> completePlayerNames(final String prefix) {
        final String lower = prefix == null ? "" : prefix.toLowerCase(Locale.ROOT);
        final List<String> suggestions = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(lower)) {
                suggestions.add(player.getName());
            }
        }
        suggestions.sort(String.CASE_INSENSITIVE_ORDER);
        return suggestions;
    }

    private String formatNumber(final long value) {
        final NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
        format.setGroupingUsed(true);
        return format.format(value);
    }
}
