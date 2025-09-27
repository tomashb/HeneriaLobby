package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendsDataProvider;
import com.lobby.friends.FriendsPlaceholderData;
import com.lobby.friends.manager.FriendsManager;
import com.lobby.menus.AssetManager;
import com.lobby.menus.CloseableMenu;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.friends.menu.statistics.FriendStatisticsMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class FriendsMainMenu implements Menu, InventoryHolder, CloseableMenu {

    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacySection();

    private final LobbyPlugin plugin;
    private final AssetManager assetManager;
    private final FriendsMenuConfiguration configuration;
    private final FriendsDataProvider dataProvider;
    private final FriendsManager friendsManager;
    private final FriendsMenuActionHandler actionHandler;
    private final Map<Integer, FriendsMenuItem> itemBySlot = new HashMap<>();

    private Inventory inventory;
    private Player viewer;
    private BukkitTask updateTask;

    public FriendsMainMenu(final LobbyPlugin plugin,
                           final AssetManager assetManager,
                           final FriendsMenuConfiguration configuration,
                           final FriendsDataProvider dataProvider,
                           final FriendsManager friendsManager,
                           final FriendsMenuActionHandler actionHandler) {
        this.plugin = plugin;
        this.assetManager = assetManager;
        this.configuration = configuration;
        this.dataProvider = dataProvider;
        this.friendsManager = friendsManager;
        this.actionHandler = actionHandler;
        for (FriendsMenuItem item : configuration.getItems()) {
            itemBySlot.put(item.getSlot(), item);
        }
    }

    @Override
    public void open(final Player player) {
        if (player == null) {
            return;
        }
        this.viewer = player;
        final Component title = SERIALIZER.deserialize(colorize(configuration.getTitle()));
        this.inventory = Bukkit.createInventory(this, configuration.getSize(), title);
        buildStaticLayout();
        refreshDynamicContent();
        scheduleUpdates();
        playSound(player, configuration.getOpenSound(), 1.5f);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        final FriendsMenuItem item = itemBySlot.get(event.getSlot());
        if (item == null || item.getAction() == null || item.getAction().isBlank()) {
            playSound(player, configuration.getErrorSound());
            return;
        }
        final String action = item.getAction();
        if ("back_to_profile".equalsIgnoreCase(action)) {
            playSound(player, configuration.getClickSound());
            player.closeInventory();
            Bukkit.getScheduler().runTaskLater(plugin, () -> openProfileMenu(player), 1L);
            return;
        }
        if (handleInternalAction(player, action)) {
            playSound(player, configuration.getClickSound());
            return;
        }
        final boolean handled = actionHandler != null && actionHandler.handle(player, action);
        playSound(player, handled ? configuration.getClickSound() : configuration.getErrorSound());
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @Override
    public void handleClose(final Player player) {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private void scheduleUpdates() {
        if (configuration.getUpdateIntervalSeconds() <= 0) {
            return;
        }
        updateTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshDynamicContent,
                configuration.getUpdateIntervalTicks(), configuration.getUpdateIntervalTicks());
    }

    private void buildStaticLayout() {
        if (inventory == null) {
            return;
        }
        for (FriendsMenuDecoration decoration : configuration.getDecorations()) {
            final ItemStack pane = new ItemStack(decoration.material());
            final ItemMeta meta = pane.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(decoration.displayName());
                pane.setItemMeta(meta);
            }
            for (Integer slot : decoration.slots()) {
                if (slot == null || slot < 0 || slot >= configuration.getSize()) {
                    continue;
                }
                inventory.setItem(slot, pane);
            }
        }
    }

    private void refreshDynamicContent() {
        if (inventory == null || viewer == null) {
            return;
        }
        final FriendsPlaceholderData data = dataProvider == null
                ? FriendsPlaceholderData.empty()
                : Objects.requireNonNullElse(dataProvider.resolve(viewer), FriendsPlaceholderData.empty());
        final Map<String, String> placeholders = data.toPlaceholderMap();

        for (FriendsMenuItem definition : configuration.getItems()) {
            final ItemStack itemStack = createItem(definition, placeholders);
            if (itemStack == null) {
                continue;
            }
            inventory.setItem(definition.getSlot(), itemStack);
        }
        viewer.updateInventory();
    }

    private ItemStack createItem(final FriendsMenuItem definition, final Map<String, String> placeholders) {
        final ItemStack base = resolveItem(definition.getMaterialKey());
        if (base == null) {
            return null;
        }
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize(apply(definition.getDisplayName(), placeholders)));
            final List<String> lore = definition.getLore().stream()
                    .map(line -> colorize(apply(line, placeholders)))
                    .toList();
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            final boolean shouldEnchant = shouldApplyEnchantment(definition, placeholders);
            if (shouldEnchant) {
                boolean applied = addEnchantment(meta, Enchantment.UNBREAKING);
                if (!applied) {
                    final Enchantment legacy = Enchantment.getByName("DURABILITY");
                    if (legacy != null) {
                        applied = addEnchantment(meta, legacy);
                    }
                }
                if (!applied) {
                    for (Enchantment fallback : Enchantment.values()) {
                        if (addEnchantment(meta, fallback)) {
                            applied = true;
                            break;
                        }
                    }
                }
                if (applied) {
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
            } else {
                meta.removeEnchant(Enchantment.UNBREAKING);
                final Enchantment legacy = Enchantment.getByName("DURABILITY");
                if (legacy != null) {
                    meta.removeEnchant(legacy);
                }
            }
            base.setItemMeta(meta);
        }
        return base;
    }

    private boolean addEnchantment(final ItemMeta meta, final Enchantment enchantment) {
        if (meta == null || enchantment == null) {
            return false;
        }
        try {
            return meta.addEnchant(enchantment, 1, true);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean shouldApplyEnchantment(final FriendsMenuItem definition, final Map<String, String> placeholders) {
        if (!definition.isEnchanted()) {
            return false;
        }
        final String requests = placeholders.get("nouvelles_demandes");
        if (requests == null) {
            return true;
        }
        try {
            return Integer.parseInt(requests) > 0;
        } catch (NumberFormatException exception) {
            return true;
        }
    }

    private ItemStack resolveItem(final String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            return new ItemStack(Material.BARRIER);
        }
        final String normalized = materialKey.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("hdb:")) {
            return assetManager.getHead(normalized);
        }
        final Material material = Material.matchMaterial(normalized.toUpperCase(Locale.ROOT));
        if (material == null) {
            return new ItemStack(Material.BARRIER);
        }
        return new ItemStack(material);
    }

    private String apply(final String input, final Map<String, String> placeholders) {
        if (input == null) {
            return "";
        }
        String result = input;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private void playSound(final Player player, final Sound sound) {
        playSound(player, sound, 1.0f);
    }

    private void playSound(final Player player, final Sound sound, final float pitch) {
        if (player == null || sound == null) {
            return;
        }
        player.playSound(player.getLocation(), sound, 1.0f, pitch);
    }

    private void openProfileMenu(final Player player) {
        if (player == null) {
            return;
        }
        final MenuManager menuManager = plugin.getMenuManager();
        if (menuManager == null) {
            return;
        }
        menuManager.openMenu(player, "profil_menu");
    }

    private boolean handleInternalAction(final Player player, final String action) {
        if (action == null || action.isBlank()) {
            return false;
        }
        return switch (action.toLowerCase(Locale.ROOT)) {
            case "open_settings" -> {
                openSettings(player);
                yield true;
            }
            case "open_statistics" -> {
                openStatistics(player);
                yield true;
            }
            case "open_blocked" -> {
                openBlocked(player);
                yield true;
            }
            case "open_favorites" -> {
                openFavorites(player);
                yield true;
            }
            default -> false;
        };
    }

    private void openSettings(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new FriendSettingsMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des paramètres");
            exception.printStackTrace();
        }
    }

    private void openStatistics(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new FriendStatisticsMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des statistiques");
            exception.printStackTrace();
        }
    }

    private void openBlocked(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new BlockedPlayersMenu(plugin, friendsManager, player).open(), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture de la liste des bloqués");
            exception.printStackTrace();
        }
    }

    private void openFavorites(final Player player) {
        try {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    new FavoriteFriendsMenu(plugin, friendsManager, player), 3L);
        } catch (Exception exception) {
            player.sendMessage("§cErreur lors de l'ouverture des favoris");
            exception.printStackTrace();
        }
    }

    private String colorize(final String input) {
        if (input == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}

