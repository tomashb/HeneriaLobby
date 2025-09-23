package com.lobby.shop;

import com.lobby.LobbyPlugin;
import com.lobby.data.ShopData.ShopCategoryData;
import com.lobby.data.ShopData.ShopItemData;
import com.lobby.economy.EconomyManager;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ShopManager implements Listener {

    private static final String DEFAULT_HEAD = "hdb:35472";

    private final LobbyPlugin plugin;
    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private final Map<String, ShopItem> items = new LinkedHashMap<>();
    private final Map<UUID, MenuContext> openMenus = new ConcurrentHashMap<>();
    private final Map<UUID, String> pendingConfirmations = new ConcurrentHashMap<>();
    private final NamespacedKey categoryKey;
    private final NamespacedKey itemKey;

    public ShopManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.categoryKey = new NamespacedKey(plugin, "shop_category");
        this.itemKey = new NamespacedKey(plugin, "shop_item");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void initialize() {
        closeOpenMenus();
        loadShopData();
        LogUtils.info(plugin, "ShopManager initialized with " + categories.size() + " categories and " + items.size() + " items");
    }

    public void shutdown() {
        closeOpenMenus();
        categories.clear();
        items.clear();
        pendingConfirmations.clear();
    }

    public void openShopMainMenu(final Player player) {
        if (player == null) {
            return;
        }
        final Inventory inventory = Bukkit.createInventory(null, 54, MessageUtils.colorize("&6&lBoutique"));
        final ItemStack[] contents = new ItemStack[inventory.getSize()];
        final ItemStack borderItem = createBorderItem();
        fillBorder(contents, borderItem);

        int slot = 10;
        for (ShopCategory category : categories.values()) {
            if (!category.isVisible()) {
                continue;
            }
            if (slot >= 44) {
                break;
            }
            final ItemStack icon = createCategoryIcon(category);
            contents[slot] = icon;
            slot = nextSlot(slot);
        }

        inventory.setContents(contents);
        openMenus.put(player.getUniqueId(), new MenuContext(MenuType.MAIN, null, inventory));
        player.openInventory(inventory);
    }

    public void openCategoryMenu(final Player player, final String categoryId) {
        if (player == null) {
            return;
        }
        final ShopCategory category = categories.get(categoryId);
        if (category == null) {
            MessageUtils.sendConfigMessage(player, "shop.category_not_found");
            return;
        }

        final List<ShopItem> categoryItems = items.values().stream()
                .filter(item -> Objects.equals(item.getData().categoryId(), categoryId))
                .filter(ShopItem::isEnabled)
                .sorted(Comparator.comparing(item -> item.getData().displayName(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        final String title = MessageUtils.colorize("&6" + category.getData().displayName());
        final Inventory inventory = Bukkit.createInventory(null, 54, title);
        final ItemStack[] contents = new ItemStack[inventory.getSize()];
        final ItemStack borderItem = createBorderItem();
        fillBorder(contents, borderItem);

        int slot = 10;
        for (ShopItem item : categoryItems) {
            if (slot >= 44) {
                break;
            }
            final ItemStack icon = createShopItemIcon(item);
            contents[slot] = icon;
            slot = nextSlot(slot);
        }

        inventory.setContents(contents);
        openMenus.put(player.getUniqueId(), new MenuContext(MenuType.CATEGORY, categoryId, inventory));
        player.openInventory(inventory);
    }

    public boolean purchaseItem(final Player player, final ShopItem item) {
        if (player == null || item == null) {
            return false;
        }
        final EconomyManager economyManager = plugin.getEconomyManager();
        if (economyManager == null) {
            return false;
        }
        final ShopItemData data = item.getData();
        if (data.priceCoins() > 0 && !economyManager.hasCoins(player.getUniqueId(), data.priceCoins())) {
            MessageUtils.sendConfigMessage(player, "shop.insufficient_coins");
            return false;
        }
        if (data.priceTokens() > 0 && !economyManager.hasTokens(player.getUniqueId(), data.priceTokens())) {
            MessageUtils.sendConfigMessage(player, "shop.insufficient_tokens");
            return false;
        }

        if (data.priceCoins() > 0) {
            economyManager.removeCoins(player.getUniqueId(), data.priceCoins(), "Shop: " + data.displayName());
        }
        if (data.priceTokens() > 0) {
            economyManager.removeTokens(player.getUniqueId(), data.priceTokens(), "Shop: " + data.displayName());
        }

        for (String command : data.commands()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            final String parsed = command.replace("%player%", player.getName());
            Bukkit.getScheduler().runTask(plugin, (Runnable) () ->
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed));
        }

        MessageUtils.sendConfigMessage(player, "shop.purchase_success", Map.of("item", data.displayName()));
        return true;
    }

    public Set<String> getCategoryIds() {
        return Set.copyOf(categories.keySet());
    }

    public boolean categoryExists(final String categoryId) {
        return categoryId != null && categories.containsKey(categoryId);
    }

    private void closeOpenMenus() {
        openMenus.keySet().forEach(uuid -> {
            final Player target = Bukkit.getPlayer(uuid);
            if (target != null && target.isOnline()) {
                target.closeInventory();
            }
        });
        openMenus.clear();
        pendingConfirmations.clear();
    }

    private void loadShopData() {
        categories.clear();
        items.clear();
        try (Connection connection = plugin.getDatabaseManager().getConnection()) {
            loadCategories(connection);
            loadItems(connection);
        } catch (final SQLException exception) {
            LogUtils.severe(plugin, "Failed to load shop data", exception);
        }
    }

    private void loadCategories(final Connection connection) throws SQLException {
        final String sql = "SELECT id, display_name, description, icon_material, sort_order, visible FROM shop_categories "
                + "ORDER BY sort_order ASC, display_name ASC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                final String id = resultSet.getString("id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                final String displayName = resultSet.getString("display_name");
                final String description = resultSet.getString("description");
                final String iconMaterial = resultSet.getString("icon_material");
                final int sortOrder = resultSet.getInt("sort_order");
                final boolean visible = resultSet.getBoolean("visible");
                final ShopCategoryData data = new ShopCategoryData(id, displayName, description, iconMaterial, sortOrder, visible);
                categories.put(data.id(), new ShopCategory(data));
            }
        }
    }

    private void loadItems(final Connection connection) throws SQLException {
        final String sql = "SELECT * FROM shop_items";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            final ResultSetMetaData metaData = resultSet.getMetaData();
            final boolean hasCategoryId = hasColumn(metaData, "category_id");
            final boolean hasLegacyCategory = hasColumn(metaData, "category");
            final boolean hasDisplayName = hasColumn(metaData, "display_name");
            final boolean hasLegacyName = hasColumn(metaData, "item_name");
            final boolean hasDescription = hasColumn(metaData, "description");
            final boolean hasIconMaterial = hasColumn(metaData, "icon_material");
            final boolean hasIconHead = hasColumn(metaData, "icon_head_texture");
            final boolean hasPriceCoins = hasColumn(metaData, "price_coins");
            final boolean hasPriceTokens = hasColumn(metaData, "price_tokens");
            final boolean hasCommands = hasColumn(metaData, "commands");
            final boolean hasConfirm = hasColumn(metaData, "confirm_required");
            final boolean hasEnabled = hasColumn(metaData, "enabled");

            while (resultSet.next()) {
                final String id = resultSet.getString("id");
                if (id == null || id.isBlank()) {
                    continue;
                }
                String categoryId = hasCategoryId ? resultSet.getString("category_id")
                        : hasLegacyCategory ? resultSet.getString("category") : "";
                if (categoryId == null) {
                    categoryId = "";
                }
                final String displayName = hasDisplayName ? resultSet.getString("display_name")
                        : hasLegacyName ? resultSet.getString("item_name") : id;
                final String description = hasDescription ? resultSet.getString("description") : "";
                final String iconMaterial = hasIconMaterial ? resultSet.getString("icon_material") : "PLAYER_HEAD";
                final String iconHeadTexture = hasIconHead ? resultSet.getString("icon_head_texture") : DEFAULT_HEAD;
                final long priceCoins = hasPriceCoins ? resultSet.getLong("price_coins") : 0L;
                final long priceTokens = hasPriceTokens ? resultSet.getLong("price_tokens") : 0L;
                final List<String> commands = hasCommands ? parseCommands(resultSet.getString("commands")) : List.of();
                final boolean confirmRequired = hasConfirm && resultSet.getBoolean("confirm_required");
                final boolean enabled = !hasEnabled || resultSet.getBoolean("enabled");

                final ShopItemData data = new ShopItemData(id, categoryId, displayName, description, iconMaterial,
                        iconHeadTexture, priceCoins, priceTokens, commands, confirmRequired, enabled);
                items.put(data.id(), new ShopItem(data));
            }
        }
    }

    private List<String> parseCommands(final String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        final String[] split = raw.split("\\r?\\n|;");
        final List<String> commands = new ArrayList<>();
        for (String entry : split) {
            if (entry == null) {
                continue;
            }
            final String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                commands.add(trimmed);
            }
        }
        return List.copyOf(commands);
    }

    private boolean hasColumn(final ResultSetMetaData metaData, final String column) throws SQLException {
        final int columnCount = metaData.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            final String name = metaData.getColumnName(i);
            if (name != null && name.equalsIgnoreCase(column)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createCategoryIcon(final ShopCategory category) {
        final ShopCategoryData data = category.getData();
        final Material material = matchMaterial(data.iconMaterial(), Material.CHEST);
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize("&e" + data.displayName()));
            final List<String> lore = new ArrayList<>();
            if (data.description() != null && !data.description().isBlank()) {
                lore.add(MessageUtils.colorize("&7" + data.description()));
            }
            lore.add(MessageUtils.colorize("&aCliquez pour ouvrir"));
            meta.setLore(lore);
            final PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(categoryKey, PersistentDataType.STRING, data.id());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ItemStack createShopItemIcon(final ShopItem item) {
        final ShopItemData data = item.getData();
        ItemStack baseItem;
        if (data.iconHeadTexture() != null && !data.iconHeadTexture().isBlank()) {
            baseItem = HeadItemBuilder.createHeadItem(data.iconHeadTexture());
        } else {
            baseItem = HeadItemBuilder.createHeadItem(DEFAULT_HEAD);
        }
        if (baseItem.getType() == Material.PLAYER_HEAD && data.iconMaterial() != null && !data.iconMaterial().isBlank()
                && !data.iconMaterial().equalsIgnoreCase("PLAYER_HEAD")) {
            final Material override = matchMaterial(data.iconMaterial(), Material.PLAYER_HEAD);
            baseItem = new ItemStack(override);
        }
        final ItemMeta meta = baseItem.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(MessageUtils.colorize("&a" + data.displayName()));
            final List<String> lore = new ArrayList<>();
            if (data.description() != null && !data.description().isBlank()) {
                lore.add(MessageUtils.colorize("&7" + data.description()));
            }
            lore.add(MessageUtils.colorize(""));
            lore.add(MessageUtils.colorize("&ePrix:"));
            if (data.priceCoins() > 0) {
                lore.add(MessageUtils.colorize("&6  " + data.priceCoins() + " coins"));
            }
            if (data.priceTokens() > 0) {
                lore.add(MessageUtils.colorize("&b  " + data.priceTokens() + " tokens"));
            }
            if (data.priceCoins() <= 0 && data.priceTokens() <= 0) {
                lore.add(MessageUtils.colorize("&aGratuit"));
            }
            lore.add(MessageUtils.colorize(""));
            if (data.confirmRequired()) {
                lore.add(MessageUtils.colorize("&cConfirmation requise"));
            }
            lore.add(MessageUtils.colorize("&aCliquez pour acheter !"));
            meta.setLore(lore);
            final PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(itemKey, PersistentDataType.STRING, data.id());
            baseItem.setItemMeta(meta);
        }
        return baseItem;
    }

    private ItemStack createBorderItem() {
        final ItemStack itemStack = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private void fillBorder(final ItemStack[] contents, final ItemStack itemStack) {
        if (contents == null || itemStack == null) {
            return;
        }
        final int size = contents.length;
        final int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            final int row = slot / 9;
            final int column = slot % 9;
            if (row == 0 || row == rows - 1 || column == 0 || column == 8) {
                contents[slot] = itemStack.clone();
            }
        }
    }

    private int nextSlot(final int current) {
        int next = current + 1;
        if ((next + 1) % 9 == 0) {
            next += 2;
        }
        return next;
    }

    private Material matchMaterial(final String name, final Material fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        final Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
        return material != null ? material : fallback;
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final MenuContext context = openMenus.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        event.setCancelled(true);
        final Inventory topInventory = event.getView().getTopInventory();
        if (topInventory == null || !topInventory.equals(context.inventory())) {
            return;
        }
        final ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) {
            return;
        }
        final ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        if (context.type() == MenuType.MAIN) {
            final String categoryId = container.get(categoryKey, PersistentDataType.STRING);
            if (categoryId != null && !categoryId.isBlank()) {
                Bukkit.getScheduler().runTask(plugin, (Runnable) () -> openCategoryMenu(player, categoryId));
            }
            return;
        }
        if (context.type() == MenuType.CATEGORY) {
            final String itemId = container.get(itemKey, PersistentDataType.STRING);
            if (itemId == null || itemId.isBlank()) {
                return;
            }
            final ShopItem shopItem = items.get(itemId);
            if (shopItem == null || !shopItem.isEnabled()) {
                return;
            }
            handleItemClick(player, shopItem);
        }
    }

    private void handleItemClick(final Player player, final ShopItem shopItem) {
        final ShopItemData data = shopItem.getData();
        if (data.confirmRequired()) {
            final String pending = pendingConfirmations.get(player.getUniqueId());
            if (!data.id().equals(pending)) {
                pendingConfirmations.put(player.getUniqueId(), data.id());
                MessageUtils.sendConfigMessage(player, "shop.confirmation_required", Map.of("item", data.displayName()));
                return;
            }
            pendingConfirmations.remove(player.getUniqueId());
        }
        final boolean purchased = purchaseItem(player, shopItem);
        if (purchased) {
            pendingConfirmations.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryDrag(final InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (openMenus.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(final InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        openMenus.remove(player.getUniqueId());
        pendingConfirmations.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        openMenus.remove(event.getPlayer().getUniqueId());
        pendingConfirmations.remove(event.getPlayer().getUniqueId());
    }

    private enum MenuType {
        MAIN,
        CATEGORY
    }

    private record MenuContext(MenuType type, String categoryId, Inventory inventory) {
    }
}
