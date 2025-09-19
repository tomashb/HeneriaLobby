package com.lobby.commands;

import com.lobby.LobbyPlugin;
import com.lobby.shop.ShopManager;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class ShopCommands implements CommandExecutor, TabExecutor {

    private final LobbyPlugin plugin;
    private final ShopManager shopManager;

    public ShopCommands(final LobbyPlugin plugin, final ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final String commandName = command.getName().toLowerCase(Locale.ROOT);
        if (commandName.equals("shop")) {
            if (!(sender instanceof Player player)) {
                MessageUtils.sendConfigMessage(sender, "commands.player_only");
                return true;
            }
            shopManager.openShopMainMenu(player);
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        return Collections.emptyList();
    }

    public boolean handleAdminCommand(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.shop")) {
            MessageUtils.sendConfigMessage(sender, "no_permission");
            return true;
        }
        if (args.length == 0) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.usage");
            return true;
        }
        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subCommand) {
            case "addcategory" -> handleAddCategory(sender, args);
            case "additem" -> handleAddItem(sender, args);
            case "reload" -> handleReload(sender);
            default -> {
                MessageUtils.sendConfigMessage(sender, "shop.admin.usage");
                yield true;
            }
        };
    }

    public List<String> tabCompleteAdmin(final CommandSender sender, final String[] args) {
        if (!sender.hasPermission("lobby.admin.shop")) {
            return Collections.emptyList();
        }
        if (args.length <= 1) {
            final String prefix = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
            return Arrays.asList("addcategory", "additem", "reload").stream()
                    .filter(option -> option.startsWith(prefix))
                    .collect(Collectors.toList());
        }
        final String subCommand = args[0].toLowerCase(Locale.ROOT);
        if (subCommand.equals("additem") && args.length == 2) {
            final String prefix = args[1].toLowerCase(Locale.ROOT);
            return shopManager.getCategoryIds().stream()
                    .filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean handleReload(final CommandSender sender) {
        shopManager.initialize();
        MessageUtils.sendConfigMessage(sender, "shop.admin.reloaded");
        return true;
    }

    private boolean handleAddCategory(final CommandSender sender, final String[] args) {
        final List<String> parameters = parseArguments(args, 1);
        if (parameters.size() < 2) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.addcategory_usage");
            return true;
        }
        final String id = parameters.get(0);
        final String displayName = parameters.get(1);
        final String description = parameters.size() >= 3 ? parameters.get(2) : "";
        final String iconMaterial = parameters.size() >= 4 ? parameters.get(3) : "CHEST";
        final int sortOrder = parameters.size() >= 5 ? parseInteger(parameters.get(4)) : 0;
        final boolean visible = parameters.size() < 6 || parseBoolean(parameters.get(5), true);

        if (id.isBlank()) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.invalid_category_id");
            return true;
        }
        if (shopManager.categoryExists(id)) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.category_exists", Map.of("id", id));
            return true;
        }

        final String sql = "INSERT INTO shop_categories (id, display_name, description, icon_material, sort_order, visible) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, displayName);
            statement.setString(3, description);
            statement.setString(4, iconMaterial);
            statement.setInt(5, sortOrder);
            statement.setBoolean(6, visible);
            statement.executeUpdate();
            MessageUtils.sendConfigMessage(sender, "shop.admin.category_created", Map.of("id", id));
            shopManager.initialize();
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to create shop category", exception);
            MessageUtils.sendConfigMessage(sender, "shop.admin.category_error");
        }
        return true;
    }

    private boolean handleAddItem(final CommandSender sender, final String[] args) {
        final List<String> parameters = parseArguments(args, 1);
        if (parameters.size() < 6) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.additem_usage");
            return true;
        }
        final String id = parameters.get(0);
        final String categoryId = parameters.get(1);
        final String displayName = parameters.get(2);
        if (id == null || id.isBlank()) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.invalid_item_id");
            return true;
        }
        if (displayName == null || displayName.isBlank()) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.invalid_item_name");
            return true;
        }
        final long priceCoins = parseLong(parameters.get(3));
        final long priceTokens = parseLong(parameters.get(4));
        if (priceCoins < 0 || priceTokens < 0) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.invalid_price");
            return true;
        }
        if (!shopManager.categoryExists(categoryId)) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.unknown_category", Map.of("category", categoryId));
            return true;
        }

        String description = "";
        String iconMaterial = "PLAYER_HEAD";
        String iconHead = "hdb:35472";
        boolean confirmRequired = false;
        boolean enabled = true;
        final List<String> commands = new ArrayList<>();

        for (int index = 5; index < parameters.size(); index++) {
            final String parameter = parameters.get(index);
            final int equalsIndex = parameter.indexOf('=');
            if (equalsIndex > 0) {
                final String key = parameter.substring(0, equalsIndex).toLowerCase(Locale.ROOT);
                final String value = parameter.substring(equalsIndex + 1);
                switch (key) {
                    case "description" -> description = value;
                    case "icon" -> iconMaterial = value;
                    case "head" -> iconHead = value;
                    case "confirm" -> confirmRequired = parseBoolean(value, false);
                    case "enabled" -> enabled = parseBoolean(value, true);
                    default -> commands.add(parameter);
                }
            } else {
                commands.add(parameter);
            }
        }

        if (commands.isEmpty()) {
            MessageUtils.sendConfigMessage(sender, "shop.admin.no_commands");
            return true;
        }

        final String commandPayload = joinCommands(commands);
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            final boolean hasNameColumn = tableHasColumn(connection, "shop_items", "name");
            final String sql = hasNameColumn
                    ? "INSERT INTO shop_items (id, name, category_id, display_name, description, icon_material, icon_head_texture, "
                    + "price_coins, price_tokens, commands, confirm_required, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                    : "INSERT INTO shop_items (id, category_id, display_name, description, icon_material, icon_head_texture, "
                    + "price_coins, price_tokens, commands, confirm_required, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int parameterIndex = 1;
                statement.setString(parameterIndex++, id);
                if (hasNameColumn) {
                    statement.setString(parameterIndex++, displayName);
                }
                statement.setString(parameterIndex++, categoryId);
                statement.setString(parameterIndex++, displayName);
                statement.setString(parameterIndex++, description);
                statement.setString(parameterIndex++, iconMaterial);
                statement.setString(parameterIndex++, iconHead);
                statement.setLong(parameterIndex++, priceCoins);
                statement.setLong(parameterIndex++, priceTokens);
                statement.setString(parameterIndex++, commandPayload);
                statement.setBoolean(parameterIndex++, confirmRequired);
                statement.setBoolean(parameterIndex, enabled);
                statement.executeUpdate();
                MessageUtils.sendConfigMessage(sender, "shop.admin.item_created", Map.of("id", id));
                shopManager.initialize();
            }
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to create shop item", exception);
            MessageUtils.sendConfigMessage(sender, "shop.admin.item_error");
        }
        return true;
    }

    private boolean tableHasColumn(final Connection connection, final String tableName, final String columnName) throws SQLException {
        final DatabaseMetaData metaData = connection.getMetaData();
        final String[] tableCandidates = {
                tableName,
                tableName.toLowerCase(Locale.ROOT),
                tableName.toUpperCase(Locale.ROOT)
        };
        final String[] columnCandidates = {
                columnName,
                columnName.toLowerCase(Locale.ROOT),
                columnName.toUpperCase(Locale.ROOT)
        };
        for (final String tableCandidate : tableCandidates) {
            for (final String columnCandidate : columnCandidates) {
                try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, tableCandidate, columnCandidate)) {
                    if (resultSet.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private List<String> parseArguments(final String[] args, final int startIndex) {
        if (args.length <= startIndex) {
            return List.of();
        }
        final String joined = Arrays.stream(args, startIndex, args.length).collect(Collectors.joining(" "));
        if (joined.isEmpty()) {
            return List.of();
        }
        final List<String> tokens = new ArrayList<>();
        final StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < joined.length(); i++) {
            final char c = joined.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private long parseLong(final String value) {
        try {
            return Long.parseLong(value);
        } catch (final NumberFormatException exception) {
            return -1L;
        }
    }

    private int parseInteger(final String value) {
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException exception) {
            return 0;
        }
    }

    private boolean parseBoolean(final String value, final boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        final String normalized = value.toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("1")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("0")) {
            return false;
        }
        return defaultValue;
    }

    private String joinCommands(final List<String> commands) {
        final StringJoiner joiner = new StringJoiner("\n");
        commands.stream()
                .map(String::trim)
                .filter(command -> !command.isBlank())
                .forEach(joiner::add);
        return joiner.toString();
    }
}
