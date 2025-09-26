package com.lobby.menus.friends;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendEntry;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.menus.prompt.ChatPromptManager;
import com.lobby.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FriendsMenu implements Menu, InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());
    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "(-?\\d+(?:\\.\\d+)?)\\s*(>=|<=|==|!=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)");

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendsMenuConfig config;
    private final List<FriendEntry> friends;
    private final int page;
    private final int requestsCount;
    private Inventory inventory;
    private final Map<Integer, FriendEntry> friendSlots = new HashMap<>();

    public FriendsMenu(final LobbyPlugin plugin,
                       final MenuManager menuManager,
                       final AssetManager assetManager,
                       final List<FriendEntry> friends,
                       final int page,
                       final int requestsCount) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.config = FriendsMenuConfig.load(plugin);
        this.friends = friends == null ? List.of() : List.copyOf(friends);
        this.page = Math.max(0, page);
        this.requestsCount = Math.max(0, requestsCount);
    }

    @Override
    public void open(final Player player) {
        if (player == null) {
            return;
        }
        final Component title = LEGACY_SERIALIZER.deserialize(MessageUtils.colorize(config.title()));
        inventory = Bukkit.createInventory(this, config.size(), title);
        friendSlots.clear();

        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%friend_requests_count%", Integer.toString(requestsCount));

        placeItems(config.designItems(), placeholders, player, false);
        placeItems(List.of(config.addFriendButton()), placeholders, player, false);
        placeItems(List.of(config.requestsButton()), placeholders, player, true);
        placeItems(List.of(config.previousPageButton(), config.nextPageButton(), config.returnButton()), placeholders,
                player, false);

        fillFriends(player);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        final String normalizedAction = findActionForSlot(slot);
        if (normalizedAction != null) {
            handleAction(player, normalizedAction);
            return;
        }
        final FriendEntry entry = friendSlots.get(slot);
        if (entry == null) {
            return;
        }
        if (event.isShiftClick()) {
            handleFavoriteToggle(player, entry);
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin,
                () -> player.performCommand("msg " + entry.name()));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillFriends(final Player player) {
        final Set<Integer> reservedSlots = new HashSet<>();
        config.designItems().forEach(item -> reservedSlots.addAll(item.slots()));
        reservedSlots.addAll(config.addFriendButton().slots());
        reservedSlots.addAll(config.requestsButton().slots());
        reservedSlots.addAll(config.previousPageButton().slots());
        reservedSlots.addAll(config.nextPageButton().slots());
        reservedSlots.addAll(config.returnButton().slots());

        final List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < config.size(); i++) {
            if (!reservedSlots.contains(i)) {
                availableSlots.add(i);
            }
        }
        if (availableSlots.isEmpty()) {
            return;
        }
        final int itemsPerPage = availableSlots.size();
        final int totalPages = Math.max(1, (int) Math.ceil((double) friends.size() / (double) itemsPerPage));
        final int currentPage = Math.min(page, totalPages - 1);
        final int startIndex = currentPage * itemsPerPage;
        final int endIndex = Math.min(friends.size(), startIndex + itemsPerPage);

        final List<FriendEntry> pageEntries = friends.subList(startIndex, endIndex);
        final FriendsMenuConfig.MenuItemDefinition template = config.friendTemplate();
        for (int index = 0; index < pageEntries.size(); index++) {
            final FriendEntry entry = pageEntries.get(index);
            final int slot = availableSlots.get(index);
            final ItemStack item = buildFriendItem(template, entry, player);
            inventory.setItem(slot, item);
            friendSlots.put(slot, entry);
        }
    }

    private ItemStack buildFriendItem(final FriendsMenuConfig.MenuItemDefinition template,
                                      final FriendEntry entry,
                                      final Player viewer) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%friend_name%", (entry.online() ? "&a" : "&7") + entry.name());
        placeholders.put("%friend_status%", entry.online() ? "&aEn ligne" : "&cHors ligne");
        placeholders.put("%friend_since_date%", DATE_FORMAT.format(entry.since()));
        final ItemStack item = createItem(template, placeholders, viewer);
        if (item.getType() == Material.PLAYER_HEAD) {
            final ItemMeta meta = item.getItemMeta();
            if (meta instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.uuid()));
                item.setItemMeta(skullMeta);
            }
        }
        if (entry.favorite()) {
            final ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    private void placeItems(final List<FriendsMenuConfig.MenuItemDefinition> definitions,
                            final Map<String, String> placeholders,
                            final Player viewer,
                            final boolean evaluateEnchant) {
        for (FriendsMenuConfig.MenuItemDefinition definition : definitions) {
            final ItemStack item = createItem(definition, placeholders, viewer, evaluateEnchant);
            for (int slot : definition.slots()) {
                inventory.setItem(slot, item);
            }
        }
    }

    private ItemStack createItem(final FriendsMenuConfig.MenuItemDefinition definition,
                                 final Map<String, String> placeholders,
                                 final Player viewer) {
        return createItem(definition, placeholders, viewer, true);
    }

    private ItemStack createItem(final FriendsMenuConfig.MenuItemDefinition definition,
                                 final Map<String, String> placeholders,
                                 final Player viewer,
                                 final boolean evaluateEnchant) {
        final String materialKey = definition.material();
        final ItemStack base = resolveBaseItem(materialKey);
        base.setAmount(Math.max(1, definition.amount()));
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            final String displayName = applyPlaceholders(definition.name(), placeholders);
            if (!displayName.isBlank()) {
                meta.setDisplayName(colorize(displayName));
            }
            final List<String> lore = renderLore(definition.lore(), placeholders);
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (meta instanceof SkullMeta skullMeta) {
                if (definition.playerHead() && viewer != null) {
                    skullMeta.setOwningPlayer(viewer);
                } else if (definition.skullOwner() != null && !definition.skullOwner().isBlank()) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(applyPlaceholders(definition.skullOwner(), placeholders)));
                }
            }
            if (evaluateEnchant && shouldEnchant(definition.enchantExpression(), placeholders)) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            base.setItemMeta(meta);
        }
        return base;
    }

    private ItemStack resolveBaseItem(final String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            return new ItemStack(Material.BARRIER);
        }
        final String trimmed = materialKey.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            return assetManager.getHead(trimmed);
        }
        final Material material = Material.matchMaterial(trimmed.toUpperCase(Locale.ROOT));
        if (material == null) {
            return new ItemStack(Material.BARRIER);
        }
        return new ItemStack(material);
    }

    private List<String> renderLore(final List<String> rawLore, final Map<String, String> placeholders) {
        if (rawLore == null || rawLore.isEmpty()) {
            return List.of();
        }
        final List<String> rendered = new ArrayList<>(rawLore.size());
        for (String line : rawLore) {
            if (line == null) {
                continue;
            }
            rendered.add(colorize(applyPlaceholders(line, placeholders)));
        }
        return rendered;
    }

    private String applyPlaceholders(final String input, final Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    private String colorize(final String input) {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(input, ""));
    }

    private boolean shouldEnchant(final String expression, final Map<String, String> placeholders) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String processed = expression;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            processed = processed.replace(entry.getKey(), entry.getValue());
        }
        processed = processed.trim();
        if (processed.equalsIgnoreCase("true")) {
            return true;
        }
        if (processed.equalsIgnoreCase("false")) {
            return false;
        }
        final Matcher matcher = COMPARISON_PATTERN.matcher(processed);
        if (!matcher.matches()) {
            return false;
        }
        final double left = Double.parseDouble(matcher.group(1));
        final String operator = matcher.group(2);
        final double right = Double.parseDouble(matcher.group(3));
        return switch (operator) {
            case ">" -> left > right;
            case ">=" -> left >= right;
            case "<" -> left < right;
            case "<=" -> left <= right;
            case "==" -> Double.compare(left, right) == 0;
            case "!=" -> Double.compare(left, right) != 0;
            default -> false;
        };
    }

    private String findActionForSlot(final int slot) {
        if (config.addFriendButton().slots().contains(slot)) {
            return config.addFriendButton().action();
        }
        if (config.requestsButton().slots().contains(slot)) {
            return config.requestsButton().action();
        }
        if (config.previousPageButton().slots().contains(slot)) {
            return config.previousPageButton().action();
        }
        if (config.nextPageButton().slots().contains(slot)) {
            return config.nextPageButton().action();
        }
        if (config.returnButton().slots().contains(slot)) {
            return config.returnButton().action();
        }
        return null;
    }

    private void handleAction(final Player player, final String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        final String trimmed = action.trim();
        if (!trimmed.startsWith("[")) {
            return;
        }
        final int closingIndex = trimmed.indexOf(']');
        if (closingIndex <= 0) {
            return;
        }
        final String type = trimmed.substring(1, closingIndex).trim().toUpperCase(Locale.ROOT);
        final String argument = trimmed.substring(closingIndex + 1).trim();
        switch (type) {
            case "MENU" -> {
                if (!menuManager.openMenu(player, argument)) {
                    player.sendMessage(MessageUtils.colorize("&cCe menu est indisponible."));
                }
            }
            case "CHAT_PROMPT" -> openChatPrompt(player, argument);
            case "PAGE" -> handlePageChange(player, argument);
            default -> {
            }
        }
    }

    private void handlePageChange(final Player player, final String argument) {
        final int targetPage = switch (argument.toLowerCase(Locale.ROOT)) {
            case "previous" -> Math.max(0, page - 1);
            case "next" -> page + 1;
            default -> page;
        };
        menuManager.openFriendsMenu(player, targetPage);
    }

    private void openChatPrompt(final Player player, final String message) {
        final ChatPromptManager promptManager = plugin.getChatPromptManager();
        if (promptManager == null) {
            player.sendMessage(MessageUtils.colorize("&cLe système de saisie est indisponible."));
            return;
        }
        promptManager.openPrompt(player, message, (target, input) -> menuManager.handleFriendPrompt(target, input));
    }

    private void handleFavoriteToggle(final Player player, final FriendEntry entry) {
        menuManager.toggleFriendFavorite(player, entry.uuid(), page);
    }
}
