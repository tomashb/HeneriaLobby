package com.lobby.lobby.items;

import com.lobby.LobbyPlugin;
import com.lobby.heads.HeadDatabaseManager;
import com.lobby.lobby.LobbyManager;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import com.lobby.utils.PlaceholderUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyItemManager {

    private final LobbyPlugin plugin;
    private final NamespacedKey lobbyItemKey;
    private final HeadDatabaseManager headDatabaseManager;
    private final Map<String, LobbyItem> items = new LinkedHashMap<>();
    private boolean enabled = true;
    private boolean clearInventoryOnJoin = true;
    private boolean restoreOnDeath = true;
    private boolean preventDrop = true;
    private boolean preventMove = true;
    private boolean preventDamage = true;
    private boolean preventConsume = true;
    private int heldSlot = -1;
    private final Set<UUID> protectedPlayers = ConcurrentHashMap.newKeySet();

    public LobbyItemManager(final LobbyPlugin plugin) {
        this.plugin = plugin;
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby_item");
        this.headDatabaseManager = plugin.getHeadDatabaseManager();
        reload();
    }

    public void reload() {
        items.clear();
        final FileConfiguration configuration = plugin.getConfigManager().getLobbyItemsConfig();
        final ConfigurationSection root = configuration.getConfigurationSection("lobby_items");
        if (root == null) {
            enabled = false;
            return;
        }

        enabled = root.getBoolean("enabled", true);
        clearInventoryOnJoin = root.getBoolean("clear_inventory_on_join", true);
        restoreOnDeath = root.getBoolean("restore_on_death", true);
        heldSlot = root.getInt("held_slot", -1);

        final boolean debugHeads = root.getBoolean("debug_heads", false);
        if (headDatabaseManager != null) {
            headDatabaseManager.setDebugLogging(debugHeads);
            headDatabaseManager.clearCache();
        }

        final ConfigurationSection protectionSection = root.getConfigurationSection("protection");
        if (protectionSection != null) {
            preventDrop = protectionSection.getBoolean("prevent_drop", true);
            preventMove = protectionSection.getBoolean("prevent_move", true);
            preventDamage = protectionSection.getBoolean("prevent_damage", true);
            preventConsume = protectionSection.getBoolean("prevent_consume", true);
        } else {
            preventDrop = true;
            preventMove = true;
            preventDamage = true;
            preventConsume = true;
        }

        final ConfigurationSection itemsSection = root.getConfigurationSection("items");
        if (itemsSection == null) {
            return;
        }

        for (String key : itemsSection.getKeys(false)) {
            final ConfigurationSection section = itemsSection.getConfigurationSection(key);
            if (section == null) {
                continue;
            }
            final Optional<LobbyItem> item = parseItem(key, section);
            item.ifPresent(lobbyItem -> items.put(lobbyItem.id().toLowerCase(Locale.ROOT), lobbyItem));
        }
    }

    public void apply(final Player player, final LobbyManager.PreparationCause cause) {
        if (!enabled || player == null) {
            return;
        }

        if (cause == LobbyManager.PreparationCause.RESPAWN && !restoreOnDeath) {
            return;
        }

        final PlayerInventory inventory = player.getInventory();

        if (cause == LobbyManager.PreparationCause.JOIN) {
            if (clearInventoryOnJoin) {
                clearInventory(inventory);
            } else {
                removeLobbyItems(inventory);
            }
        } else {
            removeLobbyItems(inventory);
        }

        if (items.isEmpty()) {
            player.updateInventory();
            removeProtection(player);
            return;
        }

        for (LobbyItem item : items.values()) {
            placeItem(player, inventory, item);
        }

        if (heldSlot >= 0 && heldSlot < 9) {
            Bukkit.getScheduler().runTask(plugin, (Runnable) () -> player.getInventory().setHeldItemSlot(heldSlot));
        }

        player.updateInventory();
        markProtected(player);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean shouldRestoreOnDeath() {
        return restoreOnDeath;
    }

    public boolean shouldPreventDrop() {
        return preventDrop;
    }

    public boolean shouldPreventMove() {
        return preventMove;
    }

    public boolean shouldPreventDamage() {
        return preventDamage;
    }

    public boolean shouldPreventConsume() {
        return preventConsume;
    }

    public NamespacedKey getLobbyItemKey() {
        return lobbyItemKey;
    }

    public Optional<LobbyItem> getLobbyItem(final ItemStack itemStack) {
        if (!isLobbyItem(itemStack)) {
            return Optional.empty();
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        final String identifier = container.get(lobbyItemKey, PersistentDataType.STRING);
        if (identifier == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(identifier.toLowerCase(Locale.ROOT)));
    }

    public Optional<LobbyItem> getLobbyItem(final String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(items.get(identifier.toLowerCase(Locale.ROOT)));
    }

    public void markProtected(final Player player) {
        if (player == null) {
            return;
        }
        protectedPlayers.add(player.getUniqueId());
    }

    public boolean isProtected(final Player player) {
        return player != null && isProtected(player.getUniqueId());
    }

    public boolean isProtected(final UUID uniqueId) {
        return uniqueId != null && protectedPlayers.contains(uniqueId);
    }

    public void removeProtection(final Player player) {
        if (player == null) {
            return;
        }
        removeProtection(player.getUniqueId());
    }

    public void removeProtection(final UUID uniqueId) {
        if (uniqueId == null) {
            return;
        }
        protectedPlayers.remove(uniqueId);
    }

    public boolean isLobbyItem(final ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }
        final PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.has(lobbyItemKey, PersistentDataType.STRING);
    }

    public void handleInteraction(final Player player, final ItemStack itemStack) {
        if (player == null || itemStack == null) {
            return;
        }
        final Optional<LobbyItem> optionalItem = getLobbyItem(itemStack);
        if (optionalItem.isEmpty()) {
            return;
        }
        final LobbyItem lobbyItem = optionalItem.get();
        final List<String> actions = lobbyItem.actions();
        if (actions.isEmpty()) {
            return;
        }
        final var npcManager = plugin.getNpcManager();
        if (npcManager == null || npcManager.getActionProcessor() == null) {
            LogUtils.warning(plugin, "Unable to execute lobby item actions: ActionProcessor unavailable.");
            return;
        }
        npcManager.getActionProcessor().processActions(actions, player, null);
    }

    public void removeLobbyItems(final PlayerInventory inventory) {
        if (inventory == null) {
            return;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            final ItemStack itemStack = inventory.getItem(slot);
            if (isLobbyItem(itemStack)) {
                inventory.setItem(slot, null);
            }
        }
        if (isLobbyItem(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    public void shutdown() {
        items.clear();
        protectedPlayers.clear();
    }

    private void placeItem(final Player player, final PlayerInventory inventory, final LobbyItem lobbyItem) {
        final ItemStack itemStack = createItemStack(player, lobbyItem);
        if (lobbyItem.slot() >= 0 && lobbyItem.slot() < inventory.getSize()) {
            inventory.setItem(lobbyItem.slot(), itemStack);
        } else {
            inventory.addItem(itemStack);
        }
    }

    private ItemStack createItemStack(final Player player, final LobbyItem lobbyItem) {
        final ItemStack baseItem = createBaseItem(player, lobbyItem);
        final Material fallbackMaterial = lobbyItem.fallbackMaterial();
        final boolean usingFallbackMaterial = fallbackMaterial != null && baseItem.getType() == fallbackMaterial;
        if (!usingFallbackMaterial && baseItem.getType() != lobbyItem.material()) {
            baseItem.setType(lobbyItem.material());
        }
        baseItem.setAmount(Math.max(1, lobbyItem.amount()));

        final ItemMeta meta = baseItem.getItemMeta();
        if (meta != null) {
            if (lobbyItem.displayName() != null && !lobbyItem.displayName().isBlank()) {
                final String processedName = MessageUtils.colorize(PlaceholderUtils.applyPlaceholders(plugin,
                        lobbyItem.displayName(), player));
                meta.setDisplayName(processedName);
            }

            if (!lobbyItem.lore().isEmpty()) {
                final List<String> lore = PlaceholderUtils.applyPlaceholders(plugin, lobbyItem.lore(), player)
                        .stream()
                        .map(MessageUtils::colorize)
                        .toList();
                meta.setLore(lore);
            }

            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ATTRIBUTES);
            final PersistentDataContainer container = meta.getPersistentDataContainer();
            container.set(lobbyItemKey, PersistentDataType.STRING, lobbyItem.id());

            if (lobbyItem.customModelData() != null) {
                meta.setCustomModelData(lobbyItem.customModelData());
            }

            if (lobbyItem.glow()) {
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            }

            baseItem.setItemMeta(meta);
        }

        return baseItem;
    }

    private ItemStack createBaseItem(final Player player, final LobbyItem lobbyItem) {
        if (lobbyItem.material() != Material.PLAYER_HEAD) {
            return new ItemStack(lobbyItem.material());
        }

        final String headId = lobbyItem.headId();
        final Material fallbackMaterial = lobbyItem.fallbackMaterial();
        if (headId != null && !headId.isBlank()) {
            final String trimmed = headId.trim();
            if ("%player_name%".equalsIgnoreCase(trimmed)) {
                return createPlayerHead(player);
            }

            if (trimmed.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
                if (headDatabaseManager != null) {
                    return headDatabaseManager.getHead(trimmed, fallbackMaterial);
                }
                return createFallbackItem(fallbackMaterial);
            }

            final String processed = PlaceholderUtils.applyPlaceholders(plugin, trimmed, player);
            if (processed != null && !processed.isBlank()) {
                final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(processed);
                return createPlayerHead(offlinePlayer);
            }
        }

        return createFallbackItem(fallbackMaterial);
    }

    private ItemStack createPlayerHead(final Player player) {
        if (headDatabaseManager != null) {
            return headDatabaseManager.getPlayerHead(player);
        }
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (player == null) {
            return head;
        }
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private ItemStack createPlayerHead(final OfflinePlayer player) {
        if (headDatabaseManager != null) {
            return headDatabaseManager.getPlayerHead(player);
        }
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = head.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            head.setItemMeta(skullMeta);
        }
        return head;
    }

    private ItemStack createFallbackItem(final Material fallbackMaterial) {
        return fallbackMaterial != null ? new ItemStack(fallbackMaterial) : new ItemStack(Material.PLAYER_HEAD);
    }

    private Optional<LobbyItem> parseItem(final String id, final ConfigurationSection section) {
        final String materialName = section.getString("material", "PLAYER_HEAD");
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LogUtils.warning(plugin, "Invalid material '" + materialName + "' for lobby item '" + id + "'.");
            return Optional.empty();
        }

        final int slot = section.getInt("slot", -1);
        final int amount = Math.max(1, section.getInt("amount", 1));
        final String name = section.getString("name");
        final List<String> lore = sanitizeList(section.getStringList("lore"));
        final List<String> actions = extractActions(section);
        final String headId = section.getString("head_id");
        Material fallbackMaterial = null;
        if (section.isString("fallback_material")) {
            final String fallbackName = section.getString("fallback_material");
            if (fallbackName != null && !fallbackName.isBlank()) {
                fallbackMaterial = Material.matchMaterial(fallbackName);
                if (fallbackMaterial == null) {
                    LogUtils.warning(plugin, "Invalid fallback material '" + fallbackName + "' for lobby item '" + id + "'.");
                }
            }
        }
        final boolean glow = section.getBoolean("glow", false);
        final Integer customModelData = section.contains("custom_model_data") ? section.getInt("custom_model_data") : null;

        final LobbyItem lobbyItem = new LobbyItem(id, material, slot, amount, name, lore, actions, headId,
                fallbackMaterial, glow, customModelData);
        return Optional.of(lobbyItem);
    }

    private List<String> extractActions(final ConfigurationSection section) {
        final List<String> actions = new ArrayList<>();
        if (section.isString("action")) {
            final String action = section.getString("action");
            if (action != null && !action.isBlank()) {
                actions.add(action.trim());
            }
        }
        if (section.isList("actions")) {
            for (String action : section.getStringList("actions")) {
                if (action == null || action.isBlank()) {
                    continue;
                }
                actions.add(action.trim());
            }
        }
        return actions.isEmpty() ? Collections.emptyList() : List.copyOf(actions);
    }

    private List<String> sanitizeList(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        final List<String> sanitized = new ArrayList<>();
        for (String line : lines) {
            if (line != null) {
                sanitized.add(line);
            }
        }
        return sanitized.isEmpty() ? List.of() : List.copyOf(sanitized);
    }

    private void clearInventory(final PlayerInventory inventory) {
        inventory.clear();
        inventory.setArmorContents(null);
        inventory.setExtraContents(new ItemStack[0]);
        inventory.setItemInOffHand(null);
    }
}
