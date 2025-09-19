package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.npcs.ActionProcessor;
import com.lobby.utils.LogUtils;
import com.lobby.utils.MessageUtils;
import com.lobby.utils.PlaceholderUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ConfiguredMenu implements Menu {

    private final LobbyPlugin plugin;
    private final String menuId;
    private final ConfigurationSection menuSection;
    private Inventory inventory;
    private final Map<Integer, List<String>> actionsBySlot = new HashMap<>();

    public ConfiguredMenu(final LobbyPlugin plugin, final String menuId, final ConfigurationSection menuSection) {
        this.plugin = plugin;
        this.menuId = menuId;
        this.menuSection = menuSection;
    }

    @Override
    public void open(final Player player) {
        final String rawTitle = menuSection.getString("title", "Menu");
        final String title = MessageUtils.colorize(PlaceholderUtils.applyPlaceholders(plugin, rawTitle, player));
        final int size = normalizeSize(menuSection.getInt("size", 27));
        inventory = Bukkit.createInventory(null, size, title);
        actionsBySlot.clear();

        final ConfigurationSection itemsSection = menuSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                final ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }
                final Optional<Integer> slot = createItem(player, itemSection);
                slot.ifPresent(index -> storeActions(index, itemSection.getStringList("actions")));
            }
        }

        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (inventory == null) {
            return;
        }
        final int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= inventory.getSize()) {
            return;
        }
        final List<String> actions = actionsBySlot.get(rawSlot);
        if (actions == null || actions.isEmpty()) {
            return;
        }
        final ActionProcessor actionProcessor = resolveActionProcessor();
        if (actionProcessor == null) {
            LogUtils.warning(plugin, "Attempted to execute menu actions but no ActionProcessor is available.");
            return;
        }
        actionProcessor.processActions(actions, player, null);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private Optional<Integer> createItem(final Player player, final ConfigurationSection itemSection) {
        final String materialName = itemSection.getString("material", "STONE");
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LogUtils.warning(plugin, "Invalid material '" + materialName + "' in menu '" + menuId + "'.");
            return Optional.empty();
        }

        final int amount = Math.max(1, itemSection.getInt("amount", 1));
        final ItemStack itemStack = new ItemStack(material, amount);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            if (itemSection.isString("name")) {
                final String name = MessageUtils.colorize(PlaceholderUtils.applyPlaceholders(plugin,
                        itemSection.getString("name"), player));
                meta.setDisplayName(name);
            }

            if (itemSection.isList("lore")) {
                final List<String> lore = PlaceholderUtils.applyPlaceholders(plugin, itemSection.getStringList("lore"), player)
                        .stream()
                        .map(MessageUtils::colorize)
                        .toList();
                meta.setLore(lore);
            }

            if (itemSection.contains("custom_model_data")) {
                meta.setCustomModelData(itemSection.getInt("custom_model_data"));
            }

            if (itemSection.getBoolean("glow", false)) {
                meta.addItemFlags(ItemFlag.values());
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
            }

            if (meta instanceof SkullMeta skullMeta) {
                applyHead(itemSection, player, skullMeta);
            }

            itemStack.setItemMeta(meta);
        }

        final int slot = itemSection.getInt("slot", -1);
        final int targetSlot = resolveSlot(slot, itemStack);
        if (targetSlot < 0) {
            return Optional.empty();
        }
        return Optional.of(targetSlot);
    }

    private void applyHead(final ConfigurationSection itemSection, final Player player, final SkullMeta skullMeta) {
        final String head = itemSection.getString("head");
        if (head == null || head.isEmpty()) {
            return;
        }
        if (player != null && head.equalsIgnoreCase("%player_name%")) {
            skullMeta.setOwningPlayer(player);
            return;
        }
        final String processed = PlaceholderUtils.applyPlaceholders(plugin, head, player);
        if (player != null && processed.equalsIgnoreCase(player.getName())) {
            skullMeta.setOwningPlayer(player);
            return;
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(processed);
        skullMeta.setOwningPlayer(offlinePlayer);
    }

    private void storeActions(final int slot, final List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        final List<String> sanitized = new ArrayList<>();
        for (String action : actions) {
            if (action == null || action.isBlank()) {
                continue;
            }
            sanitized.add(action.trim());
        }
        if (!sanitized.isEmpty()) {
            actionsBySlot.put(slot, List.copyOf(sanitized));
        }
    }

    private int resolveSlot(final int slot, final ItemStack itemStack) {
        if (inventory == null) {
            return -1;
        }
        if (slot >= 0 && slot < inventory.getSize()) {
            inventory.setItem(slot, itemStack);
            return slot;
        }
        final int firstEmpty = inventory.firstEmpty();
        if (firstEmpty >= 0) {
            inventory.setItem(firstEmpty, itemStack);
            return firstEmpty;
        }
        return -1;
    }

    private int normalizeSize(final int requested) {
        final int clamped = Math.max(9, Math.min(54, requested));
        final int remainder = clamped % 9;
        return remainder == 0 ? clamped : clamped + (9 - remainder);
    }

    private ActionProcessor resolveActionProcessor() {
        return Optional.ofNullable(plugin.getNpcManager())
                .map(npcManager -> npcManager.getActionProcessor())
                .orElse(null);
    }
}
