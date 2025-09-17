package com.lobby.commands;

import com.lobby.LobbyPlugin;
import com.lobby.data.PlayerData;
import com.lobby.economy.CurrencyType;
import com.lobby.economy.EconomyManager;
import com.lobby.economy.LeaderboardManager;
import com.lobby.economy.TransactionManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class EconomyCommands implements CommandExecutor, TabExecutor {

    private final LobbyPlugin plugin;
    private final EconomyManager economyManager;

    public EconomyCommands(final LobbyPlugin plugin, final EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "coins" -> handleCoinsCommand(sender, args);
            case "tokens" -> handleTokensCommand(sender, args);
            case "pay" -> handlePayCommand(sender, args);
            case "top" -> handleTopCommand(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "coins", "tokens" -> completePlayerNames(args);
            case "pay" -> completePay(sender, args);
            case "top" -> completeTop(args);
            default -> Collections.emptyList();
        };
    }

    private boolean handleCoinsCommand(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtils.sendConfigMessage(sender, "economy.player_only", Collections.emptyMap());
                return true;
            }
            final long coins = economyManager.getCoins(player.getUniqueId());
            MessageUtils.sendConfigMessage(player, "economy.balance_coins", Map.of("coins", formatNumber(coins)));
            return true;
        }

        if (!sender.hasPermission("lobby.economy.view")) {
            MessageUtils.sendPrefixedMessage(sender, plugin.getConfigManager().getMessagesConfig().getString("no_permission"));
            return true;
        }

        final Optional<PlayerData> targetData = economyManager.getPlayerDataByName(args[0]);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "economy.player_not_found", Map.of("player", args[0]));
            return true;
        }

        final PlayerData data = targetData.get();
        MessageUtils.sendConfigMessage(sender, "economy.balance_other",
                Map.of("player", data.username(), "coins", formatNumber(data.coins()), "tokens", formatNumber(data.tokens())));
        return true;
    }

    private boolean handleTokensCommand(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                MessageUtils.sendConfigMessage(sender, "economy.player_only", Collections.emptyMap());
                return true;
            }
            final long tokens = economyManager.getTokens(player.getUniqueId());
            MessageUtils.sendConfigMessage(player, "economy.balance_tokens", Map.of("tokens", formatNumber(tokens)));
            return true;
        }

        if (!sender.hasPermission("lobby.economy.view")) {
            MessageUtils.sendPrefixedMessage(sender, plugin.getConfigManager().getMessagesConfig().getString("no_permission"));
            return true;
        }

        final Optional<PlayerData> targetData = economyManager.getPlayerDataByName(args[0]);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "economy.player_not_found", Map.of("player", args[0]));
            return true;
        }
        final PlayerData data = targetData.get();
        MessageUtils.sendConfigMessage(sender, "economy.balance_other",
                Map.of("player", data.username(), "coins", formatNumber(data.coins()), "tokens", formatNumber(data.tokens())));
        return true;
    }

    private boolean handlePayCommand(final CommandSender sender, final String[] args) {
        if (!(sender instanceof Player player)) {
            MessageUtils.sendConfigMessage(sender, "economy.player_only", Collections.emptyMap());
            return true;
        }

        if (!economyManager.isTransfersAllowed()) {
            MessageUtils.sendConfigMessage(player, "economy.transfers_disabled", Collections.emptyMap());
            return true;
        }

        if (!player.hasPermission("lobby.economy.pay")) {
            MessageUtils.sendPrefixedMessage(player, plugin.getConfigManager().getMessagesConfig().getString("no_permission"));
            return true;
        }

        if (args.length < 2) {
            MessageUtils.sendConfigMessage(player, "economy.pay_usage", Collections.emptyMap());
            return true;
        }

        final String targetName = args[0];
        final long amount;
        try {
            amount = Long.parseLong(args[1]);
        } catch (NumberFormatException exception) {
            MessageUtils.sendConfigMessage(player, "economy.invalid_amount", Map.of("amount", args[1]));
            return true;
        }

        if (amount <= 0) {
            MessageUtils.sendConfigMessage(player, "economy.invalid_amount", Map.of("amount", args[1]));
            return true;
        }

        final Optional<PlayerData> targetData = resolvePlayerData(targetName);
        if (targetData.isEmpty()) {
            MessageUtils.sendConfigMessage(player, "economy.player_not_found", Map.of("player", targetName));
            return true;
        }

        final PlayerData data = targetData.get();
        if (data.uuid().equals(player.getUniqueId())) {
            MessageUtils.sendConfigMessage(player, "economy.transfer_self_error", Collections.emptyMap());
            return true;
        }

        if (!economyManager.hasCoins(player.getUniqueId(), amount)) {
            final long current = economyManager.getCoins(player.getUniqueId());
            MessageUtils.sendConfigMessage(player, "economy.insufficient_coins",
                    Map.of("needed", formatNumber(amount), "current", formatNumber(current)));
            return true;
        }

        final Optional<TransactionManager.TransferResult> transferResult = economyManager.transferDetailed(
                player.getUniqueId(), data.uuid(), amount, "Player transfer");
        if (transferResult.isEmpty()) {
            MessageUtils.sendConfigMessage(player, "economy.transfer_failed", Collections.emptyMap());
            return true;
        }

        final TransactionManager.TransferResult result = transferResult.get();
        MessageUtils.sendConfigMessage(player, "economy.transfer_sent",
                Map.of("amount", formatNumber(result.transferredAmount()), "player", data.username()));

        final Player onlineTarget = Bukkit.getPlayer(data.uuid());
        if (onlineTarget != null && onlineTarget.isOnline()) {
            MessageUtils.sendConfigMessage(onlineTarget, "economy.transfer_received",
                    Map.of("amount", formatNumber(result.transferredAmount()), "player", player.getName()));
        }
        return true;
    }

    private boolean handleTopCommand(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "economy.top_usage", Collections.emptyMap());
            return true;
        }
        final String type = args[0].toLowerCase(Locale.ROOT);
        final List<LeaderboardManager.LeaderboardEntry> entries;
        final CurrencyType currencyType;
        switch (type) {
            case "coins" -> {
                entries = economyManager.getTopCoinsEntries(10);
                currencyType = CurrencyType.COINS;
            }
            case "tokens" -> {
                entries = economyManager.getTopTokenEntries(10);
                currencyType = CurrencyType.TOKENS;
            }
            default -> {
                MessageUtils.sendConfigMessage(sender, "economy.top_usage", Collections.emptyMap());
                return true;
            }
        }

        final String header = MessageUtils.getConfigMessage("economy.top_header", Map.of("type", type.toUpperCase(Locale.ROOT)));
        if (header != null && !header.isEmpty()) {
            sender.sendMessage(MessageUtils.applyPrefix(header));
        }

        for (int index = 0; index < entries.size(); index++) {
            final LeaderboardManager.LeaderboardEntry entry = entries.get(index);
            final int rank = index + 1;
            final String line = MessageUtils.getConfigMessage("economy.top_line",
                    Map.of("rank", String.valueOf(rank), "player", entry.username(), "amount", entry.formattedAmount()));
            if (line != null) {
                sender.sendMessage(MessageUtils.applyPrefix(line));
            }
        }

        if (sender instanceof Player player) {
            final int rank = economyManager.getPlayerRank(player.getUniqueId(), currencyType);
            if (rank > 0) {
                final long balance = currencyType.isCoins()
                        ? economyManager.getCoins(player.getUniqueId())
                        : economyManager.getTokens(player.getUniqueId());
                final String message = MessageUtils.getConfigMessage("economy.top_your_rank",
                        Map.of("rank", String.valueOf(rank), "amount", formatNumber(balance)));
                if (message != null) {
                    sender.sendMessage(MessageUtils.applyPrefix(message));
                }
            }
        }
        return true;
    }

    private Optional<PlayerData> resolvePlayerData(final String targetName) {
        final Player online = Bukkit.getPlayerExact(targetName);
        if (online != null) {
            return Optional.of(economyManager.getPlayerData(online.getUniqueId()));
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
        if (offlinePlayer != null && offlinePlayer.hasPlayedBefore()) {
            return Optional.of(economyManager.getPlayerData(offlinePlayer.getUniqueId()));
        }
        return economyManager.getPlayerDataByName(targetName);
    }

    private List<String> completePlayerNames(final String[] args) {
        if (args.length != 1) {
            return Collections.emptyList();
        }
        final String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());
    }

    private List<String> completePay(final CommandSender sender, final String[] args) {
        if (args.length == 1) {
            return completePlayerNames(args);
        }
        if (args.length == 2) {
            return List.of("100", "250", "500", "1000");
        }
        return Collections.emptyList();
    }

    private List<String> completeTop(final String[] args) {
        if (args.length == 1) {
            final String prefix = args[0].toLowerCase(Locale.ROOT);
            final List<String> options = List.of("coins", "tokens");
            return options.stream()
                    .filter(option -> option.startsWith(prefix))
                    .toList();
        }
        return Collections.emptyList();
    }

    private String formatNumber(final long value) {
        final NumberFormat format = NumberFormat.getInstance(Locale.FRANCE);
        format.setGroupingUsed(true);
        return format.format(value);
    }
}
