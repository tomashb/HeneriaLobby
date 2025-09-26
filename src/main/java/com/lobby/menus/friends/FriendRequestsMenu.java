package com.lobby.menus.friends;

import com.lobby.LobbyPlugin;
import com.lobby.friends.FriendRequestEntry;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.utils.MessageUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class FriendRequestsMenu implements Menu, InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.systemDefault());

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final FriendRequestsMenuConfig config;
    private final List<FriendRequestEntry> requests;
    private Inventory inventory;
    private final Map<Integer, FriendRequestEntry> requestSlots = new HashMap<>();

    public FriendRequestsMenu(final LobbyPlugin plugin,
                              final MenuManager menuManager,
                              final AssetManager assetManager,
                              final List<FriendRequestEntry> requests) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.config = FriendRequestsMenuConfig.load(plugin);
        this.requests = requests == null ? List.of() : List.copyOf(requests);
    }

    @Override
    public void open(final Player player) {
        if (player == null) {
            return;
        }
        final Component title = LEGACY_SERIALIZER.deserialize(MessageUtils.colorize(config.title()));
        inventory = Bukkit.createInventory(this, config.size(), title);
        requestSlots.clear();

        placeItems(config.designItems(), player);
        placeItems(List.of(config.returnButton()), player);

        fillRequests();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final int slot = event.getSlot();
        if (config.returnButton().slots().contains(slot)) {
            handleAction(player, config.returnButton().action());
            return;
        }
        final FriendRequestEntry entry = requestSlots.get(slot);
        if (entry == null) {
            return;
        }
        if (event.getClick() == ClickType.RIGHT) {
            menuManager.handleFriendRequestDecision(player, entry.senderUuid(), false);
        } else {
            menuManager.handleFriendRequestDecision(player, entry.senderUuid(), true);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillRequests() {
        final Set<Integer> reservedSlots = new HashSet<>();
        config.designItems().forEach(item -> reservedSlots.addAll(item.slots()));
        reservedSlots.addAll(config.returnButton().slots());

        final List<Integer> availableSlots = new ArrayList<>();
        for (int i = 0; i < config.size(); i++) {
            if (!reservedSlots.contains(i)) {
                availableSlots.add(i);
            }
        }
        final int displayCount = Math.min(requests.size(), availableSlots.size());
        for (int index = 0; index < displayCount; index++) {
            final FriendRequestEntry entry = requests.get(index);
            final int slot = availableSlots.get(index);
            inventory.setItem(slot, buildRequestItem(entry));
            requestSlots.put(slot, entry);
        }
    }

    private ItemStack buildRequestItem(final FriendRequestEntry entry) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = item.getItemMeta();
        if (baseMeta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.senderUuid()));
            final String displayName = entry.senderName() == null ? entry.senderUuid().toString() : entry.senderName();
            skullMeta.setDisplayName(colorize("&e" + displayName));
            final List<String> lore = List.of(
                    colorize("&7Demande reçue le &f" + DATE_FORMAT.format(entry.createdAt())),
                    "",
                    colorize("&a▶ Clic gauche : Accepter"),
                    colorize("&c▶ Clic droit : Refuser")
            );
            skullMeta.setLore(lore);
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private void placeItems(final List<FriendsMenuConfig.MenuItemDefinition> definitions, final Player viewer) {
        for (FriendsMenuConfig.MenuItemDefinition definition : definitions) {
            final ItemStack item = createItem(definition, viewer);
            for (int slot : definition.slots()) {
                inventory.setItem(slot, item);
            }
        }
    }

    private ItemStack createItem(final FriendsMenuConfig.MenuItemDefinition definition, final Player viewer) {
        final ItemStack base;
        if (definition.material() != null && definition.material().toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            base = assetManager.getHead(definition.material());
        } else {
            final Material material = Material.matchMaterial(definition.material().toUpperCase(Locale.ROOT));
            base = new ItemStack(material == null ? Material.BARRIER : material);
        }
        base.setAmount(Math.max(1, definition.amount()));
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            if (!definition.name().isBlank()) {
                meta.setDisplayName(colorize(definition.name()));
            }
            final List<String> lore = definition.lore().stream().map(this::colorize).toList();
            if (!lore.isEmpty()) {
                meta.setLore(lore);
            }
            if (meta instanceof SkullMeta skullMeta) {
                if (definition.playerHead() && viewer != null) {
                    skullMeta.setOwningPlayer(viewer);
                } else if (definition.skullOwner() != null && !definition.skullOwner().isBlank()) {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(definition.skullOwner()));
                }
                base.setItemMeta(skullMeta);
            } else {
                base.setItemMeta(meta);
            }
        }
        return base;
    }

    private String colorize(final String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private void handleAction(final Player player, final String action) {
        if (action == null || action.isBlank()) {
            return;
        }
        final String trimmed = action.trim();
        if (!trimmed.startsWith("[")) {
            menuManager.openMenu(player, trimmed);
            return;
        }
        final int closingIndex = trimmed.indexOf(']');
        if (closingIndex <= 0) {
            return;
        }
        final String type = trimmed.substring(1, closingIndex).trim().toUpperCase(Locale.ROOT);
        final String argument = trimmed.substring(closingIndex + 1).trim();
        if (type.equals("MENU")) {
            if (!menuManager.openMenu(player, argument)) {
                player.sendMessage(MessageUtils.colorize("&cCe menu est indisponible."));
            }
        }
    }
}
