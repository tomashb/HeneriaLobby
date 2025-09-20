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
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ShopCommands implements CommandExecutor, TabExecutor {

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

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
            case "additem", "add" -> handleAddItem(sender, args);
            case "list", "ls" -> handleListItems(sender);
            case "reload" -> handleReload(sender);
            case "remove", "delete", "rm", "enable", "disable" -> {
                sender.sendMessage("§cFonctionnalité pas encore implémentée.");
                yield true;
            }
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
            return Arrays.asList("addcategory", "additem", "list", "reload", "remove", "enable", "disable").stream()
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
            sendAddItemUsage(sender);
            return true;
        }

        final String name = parameters.get(0);
        final String categoryId = parameters.get(1);
        final String description = parameters.get(2);

        final long priceCoins;
        final long priceTokens;
        try {
            priceCoins = Long.parseLong(parameters.get(3));
            priceTokens = Long.parseLong(parameters.get(4));
        } catch (final NumberFormatException exception) {
            sender.sendMessage("§cPrix invalide ! Les prix doivent être des nombres entiers positifs.");
            sender.sendMessage("§cExemple: price_coins=1000 price_tokens=5");
            return true;
        }

        final List<String> commandParts = new ArrayList<>(parameters.subList(5, parameters.size()));
        if (name == null || name.trim().isEmpty()) {
            sender.sendMessage("§cLe nom de l'item ne peut pas être vide !");
            return true;
        }
        if (categoryId == null || categoryId.trim().isEmpty()) {
            sender.sendMessage("§cLa catégorie ne peut pas être vide !");
            return true;
        }
        if (!shopManager.categoryExists(categoryId)) {
            sender.sendMessage("§cCatégorie inconnue: §6" + categoryId + "§c.");
            return true;
        }
        if (priceCoins < 0 || priceTokens < 0) {
            sender.sendMessage("§cLes prix ne peuvent pas être négatifs !");
            return true;
        }
        if (priceCoins == 0 && priceTokens == 0) {
            sender.sendMessage("§cL'item doit coûter au moins 1 coin ou 1 token !");
            return true;
        }
        final String commandPayload = joinCommands(commandParts);
        if (commandPayload.isBlank()) {
            sender.sendMessage("§cVous devez spécifier au moins une commande à exécuter !");
            return true;
        }

        final String itemId = generateItemId(name);
        if (itemId.isEmpty()) {
            sender.sendMessage("§cImpossible de générer un identifiant valide pour cet item. Utilisez un nom différent.");
            return true;
        }

        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            if (itemExists(connection, itemId, name)) {
                sender.sendMessage("§cUn item avec le nom '§6" + name + "§c' existe déjà !");
                return true;
            }

            final boolean hasCategoryId = tableHasColumn(connection, "shop_items", "category_id");
            final boolean hasDisplayName = tableHasColumn(connection, "shop_items", "display_name");
            final boolean hasDescription = tableHasColumn(connection, "shop_items", "description");
            final boolean hasIconMaterial = tableHasColumn(connection, "shop_items", "icon_material");
            final boolean hasIconHead = tableHasColumn(connection, "shop_items", "icon_head_texture");
            final boolean hasConfirm = tableHasColumn(connection, "shop_items", "confirm_required");
            final boolean hasEnabled = tableHasColumn(connection, "shop_items", "enabled");
            final boolean hasNameColumn = tableHasColumn(connection, "shop_items", "name");

            final String sql;
            if (hasCategoryId && hasDisplayName) {
                final StringBuilder builder = new StringBuilder("INSERT INTO shop_items (id, category_id, display_name");
                if (hasDescription) {
                    builder.append(", description");
                }
                if (hasIconMaterial) {
                    builder.append(", icon_material");
                }
                if (hasIconHead) {
                    builder.append(", icon_head_texture");
                }
                builder.append(", price_coins, price_tokens, commands");
                if (hasConfirm) {
                    builder.append(", confirm_required");
                }
                if (hasEnabled) {
                    builder.append(", enabled");
                }
                builder.append(") VALUES (?, ?, ?");
                if (hasDescription) {
                    builder.append(", ?");
                }
                if (hasIconMaterial) {
                    builder.append(", ?");
                }
                if (hasIconHead) {
                    builder.append(", ?");
                }
                builder.append(", ?, ?, ?");
                if (hasConfirm) {
                    builder.append(", ?");
                }
                if (hasEnabled) {
                    builder.append(", ?");
                }
                builder.append(')');
                sql = builder.toString();
            } else {
                sql = "INSERT INTO shop_items (name, category, description, price_coins, price_tokens, commands, enabled) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)";
            }

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                int index = 1;
                if (hasCategoryId && hasDisplayName) {
                    statement.setString(index++, itemId);
                    statement.setString(index++, categoryId);
                    statement.setString(index++, name);
                    if (hasDescription) {
                        statement.setString(index++, description);
                    }
                    if (hasIconMaterial) {
                        statement.setString(index++, "PLAYER_HEAD");
                    }
                    if (hasIconHead) {
                        statement.setString(index++, "hdb:35472");
                    }
                    statement.setLong(index++, priceCoins);
                    statement.setLong(index++, priceTokens);
                    statement.setString(index++, commandPayload);
                    if (hasConfirm) {
                        statement.setBoolean(index++, false);
                    }
                    if (hasEnabled) {
                        statement.setBoolean(index, true);
                    }
                } else {
                    if (hasNameColumn) {
                        statement.setString(index++, name);
                    } else {
                        statement.setString(index++, itemId);
                    }
                    statement.setString(index++, categoryId);
                    statement.setString(index++, description);
                    statement.setLong(index++, priceCoins);
                    statement.setLong(index++, priceTokens);
                    statement.setString(index++, commandPayload);
                    statement.setBoolean(index, true);
                }

                final int affected = statement.executeUpdate();
                if (affected > 0) {
                    sender.sendMessage("§a§l✓ Item créé avec succès !");
                    sender.sendMessage("§7┌─ §6" + name);
                    sender.sendMessage("§7├─ Catégorie: §e" + categoryId);
                    sender.sendMessage("§7├─ Description: §f" + description);
                    final String priceDisplay = buildPriceDisplay(priceCoins, priceTokens);
                    sender.sendMessage("§7├─ Prix: " + priceDisplay);
                    sender.sendMessage("§7└─ Commandes: §f" + commandPayload.replace("\n", "§7, §f"));
                    plugin.getLogger().info("Shop item created: '" + name + "' by " + sender.getName()
                            + " (Coins: " + priceCoins + ", Tokens: " + priceTokens + ")");
                    shopManager.initialize();
                } else {
                    sender.sendMessage("§cErreur: Aucune ligne affectée lors de l'insertion !");
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Failed to create shop item '" + name + "': " + exception.getMessage());
            LogUtils.severe(plugin, "Failed to create shop item", exception);
            sender.sendMessage("§cErreur SQL lors de la création de l'item !");
            sender.sendMessage("§cDétails: " + exception.getMessage());
        }
        return true;
    }

    private void sendAddItemUsage(final CommandSender sender) {
        sender.sendMessage("§c§lUsage incorrect !");
        sender.sendMessage("§c/ladmin shop additem <name> <category> <description> <price_coins> <price_tokens> <commands...>");
        sender.sendMessage("§7Exemples:");
        sender.sendMessage("§e/ladmin shop additem \"Grade VIP\" \"grades\" \"Devenez VIP !\" 0 10 \"lp user %player% parent add vip\"");
        sender.sendMessage("§e/ladmin shop additem \"Épée Diamond\" \"armes\" \"Épée puissante\" 1000 0 \"give %player% diamond_sword 1\"");
    }

    private String buildPriceDisplay(final long priceCoins, final long priceTokens) {
        if (priceCoins > 0 && priceTokens > 0) {
            return "§e" + priceCoins + " coins §7+ §b" + priceTokens + " tokens";
        }
        if (priceCoins > 0) {
            return "§e" + priceCoins + " coins";
        }
        return "§b" + priceTokens + " tokens";
    }

    private String generateItemId(final String name) {
        if (name == null) {
            return "";
        }
        final String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        final String sanitized = NON_ALPHANUMERIC.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("-");
        final String compact = sanitized.replaceAll("-+", "-").replaceAll("(^-|-$)", "");
        return compact;
    }

    private boolean itemExists(final Connection connection, final String itemId, final String name) throws SQLException {
        final boolean hasDisplayName = tableHasColumn(connection, "shop_items", "display_name");
        final boolean hasNameColumn = tableHasColumn(connection, "shop_items", "name");
        final boolean hasId = tableHasColumn(connection, "shop_items", "id");

        if (hasDisplayName) {
            final String sql = "SELECT COUNT(*) FROM shop_items WHERE LOWER(display_name) = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        return true;
                    }
                }
            }
        }

        if (hasNameColumn) {
            final String sql = "SELECT COUNT(*) FROM shop_items WHERE LOWER(name) = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name.toLowerCase(Locale.ROOT));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next() && resultSet.getInt(1) > 0) {
                        return true;
                    }
                }
            }
        }

        if (hasId) {
            final String sql = "SELECT COUNT(*) FROM shop_items WHERE LOWER(id) = ?";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, itemId.toLowerCase(Locale.ROOT));
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getInt(1) > 0;
                    }
                }
            }
        }

        return false;
    }

    private boolean handleListItems(final CommandSender sender) {
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            final boolean hasCategoryId = tableHasColumn(connection, "shop_items", "category_id");
            final boolean hasCategory = tableHasColumn(connection, "shop_items", "category");
            final boolean hasDisplayName = tableHasColumn(connection, "shop_items", "display_name");
            final boolean hasName = tableHasColumn(connection, "shop_items", "name");
            final boolean hasDescription = tableHasColumn(connection, "shop_items", "description");
            final boolean hasPriceCoins = tableHasColumn(connection, "shop_items", "price_coins");
            final boolean hasPriceTokens = tableHasColumn(connection, "shop_items", "price_tokens");
            final boolean hasEnabled = tableHasColumn(connection, "shop_items", "enabled");

            final String categoryColumn = hasCategoryId ? "category_id" : hasCategory ? "category" : "";
            final String nameColumn = hasDisplayName ? "display_name" : hasName ? "name" : "id";

            final StringBuilder query = new StringBuilder("SELECT id");
            if (!categoryColumn.isEmpty()) {
                query.append(", ").append(categoryColumn).append(" AS category");
            } else {
                query.append(", '' AS category");
            }
            query.append(", ").append(nameColumn).append(" AS item_name");
            if (hasDescription) {
                query.append(", description");
            }
            if (hasPriceCoins) {
                query.append(", price_coins");
            }
            if (hasPriceTokens) {
                query.append(", price_tokens");
            }
            if (hasEnabled) {
                query.append(", enabled");
            }
            query.append(" FROM shop_items ORDER BY ");
            if (!categoryColumn.isEmpty()) {
                query.append(categoryColumn).append(", ");
            }
            query.append(nameColumn);

            try (PreparedStatement statement = connection.prepareStatement(query.toString());
                 ResultSet resultSet = statement.executeQuery()) {
                sender.sendMessage("§6§l╔══════════════════════════════════════╗");
                sender.sendMessage("§6§l║            ITEMS BOUTIQUE            ║");
                sender.sendMessage("§6§l╚══════════════════════════════════════╝");

                String currentCategory = null;
                int count = 0;

                while (resultSet.next()) {
                    final String category = !categoryColumn.isEmpty() ? resultSet.getString("category") : "";
                    final String itemName = resultSet.getString("item_name");
                    final String description = hasDescription ? resultSet.getString("description") : "";
                    final long priceCoins = hasPriceCoins ? resultSet.getLong("price_coins") : 0L;
                    final long priceTokens = hasPriceTokens ? resultSet.getLong("price_tokens") : 0L;
                    final boolean enabled = !hasEnabled || resultSet.getBoolean("enabled");

                    if (currentCategory == null || !currentCategory.equalsIgnoreCase(category)) {
                        if (count > 0) {
                            sender.sendMessage("");
                        }
                        final String categoryLabel = (category == null || category.isBlank()) ? "Divers" : category;
                        sender.sendMessage("§e§l▶ " + categoryLabel.toUpperCase(Locale.ROOT) + ":");
                        currentCategory = category;
                    }

                    final String status = enabled ? "§a✓" : "§c✗";
                    final StringBuilder priceText = new StringBuilder();
                    if (priceCoins > 0) {
                        priceText.append("§e").append(priceCoins).append("c");
                    }
                    if (priceTokens > 0) {
                        if (priceText.length() > 0) {
                            priceText.append(" §7+ ");
                        }
                        priceText.append("§b").append(priceTokens).append("t");
                    }
                    if (priceText.length() == 0) {
                        priceText.append("§aGratuit");
                    }

                    sender.sendMessage("  " + status + " §f" + itemName + " §7(" + priceText + "§7)");
                    if (description != null && !description.isBlank()) {
                        sender.sendMessage("    §8└─ " + description);
                    }
                    count++;
                }

                sender.sendMessage("");
                if (count == 0) {
                    sender.sendMessage("§7Aucun item trouvé dans la boutique.");
                    sender.sendMessage("§7Utilisez §e/ladmin shop additem§7 pour en ajouter.");
                } else {
                    sender.sendMessage("§7Total: §e" + count + " item" + (count > 1 ? "s" : ""));
                }
            }
        } catch (final SQLException exception) {
            plugin.getLogger().severe("Failed to list shop items: " + exception.getMessage());
            sender.sendMessage("§cErreur lors de la récupération des items !");
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
