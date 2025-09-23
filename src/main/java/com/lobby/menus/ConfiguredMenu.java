package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.menus.templates.DesignTemplate;
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
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.AbstractMap;

public class ConfiguredMenu implements Menu {

    private final LobbyPlugin plugin;
    private final String menuId;
    private final ConfigurationSection menuSection;
    private final MenuDesignProvider menuDesignProvider;
    private Inventory inventory;
    private final Map<Integer, List<String>> actionsBySlot = new HashMap<>();
    private Map<Menu.HeadRequest, ItemStack> asyncPreloadedHeads = Map.of();
    private Map<String, String> asyncPreloadedPlaceholders = Map.of();
    private final Map<Integer, ItemStack> deferredHeadUpdates = new LinkedHashMap<>();
    private BukkitTask deferredHeadTask;

    private static final int MAX_HEAD_UPDATES_PER_TICK = 4;
    private static final long HEAD_UPDATE_INITIAL_DELAY = 2L;
    private static final long HEAD_UPDATE_INTERVAL = 2L;

    public ConfiguredMenu(final LobbyPlugin plugin,
                          final String menuId,
                          final ConfigurationSection menuSection,
                          final MenuDesignProvider menuDesignProvider) {
        this.plugin = plugin;
        this.menuId = menuId;
        this.menuSection = menuSection;
        this.menuDesignProvider = menuDesignProvider;
    }

    @Override
    public AsyncPreparation prepareAsync(final Player player) {
        final Set<Menu.HeadRequest> headRequests = new HashSet<>();
        collectHeadRequests(menuSection.getConfigurationSection("items"), player, headRequests);

        final DesignTemplate designTemplate = resolveDesignTemplate();
        if (designTemplate != null) {
            collectHeadRequests(designTemplate.getItemsSection(), player, headRequests);
        }

        if (headRequests.isEmpty()) {
            return Menu.AsyncPreparation.EMPTY;
        }
        return new Menu.AsyncPreparation(headRequests);
    }

    @Override
    public void applyAsyncPreparation(final AsyncPreparationResult result) {
        cancelDeferredHeadTask();
        deferredHeadUpdates.clear();
        if (result == null) {
            asyncPreloadedHeads = Map.of();
            asyncPreloadedPlaceholders = Map.of();
            return;
        }

        final Map<Menu.HeadRequest, ItemStack> storedHeads = new HashMap<>();
        result.preloadedHeads().forEach((request, itemStack) -> {
            if (request == null || itemStack == null) {
                return;
            }
            storedHeads.put(request, itemStack.clone());
        });
        asyncPreloadedHeads = storedHeads.isEmpty() ? Map.of() : Map.copyOf(storedHeads);

        final Map<String, String> placeholders = result.placeholderValues();
        if (placeholders == null || placeholders.isEmpty()) {
            asyncPreloadedPlaceholders = Map.of();
        } else {
            final Map<String, String> storedPlaceholders = new HashMap<>();
            placeholders.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    return;
                }
                storedPlaceholders.put(key, value != null ? value : "");
            });
            asyncPreloadedPlaceholders = storedPlaceholders.isEmpty() ? Map.of() : Map.copyOf(storedPlaceholders);
        }
    }

    @Override
    public void open(final Player player) {
        cancelDeferredHeadTask();
        deferredHeadUpdates.clear();
        final String rawTitle = menuSection.getString("title", "Menu");
        final String resolvedTitle = applyAsyncPlaceholders(PlaceholderUtils.applyPlaceholders(plugin, rawTitle, player));
        final String title = MessageUtils.colorize(resolvedTitle != null ? resolvedTitle : "Menu");
        final int size = normalizeSize(menuSection.getInt("size", 27));
        inventory = Bukkit.createInventory(null, size, title);
        actionsBySlot.clear();

        final boolean debugAsyncMenu = "stats_menu".equalsIgnoreCase(menuId) || "profil_menu".equalsIgnoreCase(menuId);

        final ItemStack[] contents = new ItemStack[size];
        final DesignTemplate designTemplate = resolveDesignTemplate();

        final ItemStack filler = createFillerItem(resolveFillMaterial(designTemplate));
        if (filler != null) {
            final List<Integer> fillSlots = resolveFillSlots(designTemplate);
            if (fillSlots == null || fillSlots.isEmpty()) {
                for (int slot = 0; slot < contents.length; slot++) {
                    contents[slot] = filler.clone();
                }
            } else {
                for (Integer slot : fillSlots) {
                    if (slot == null) {
                        continue;
                    }
                    final int index = slot;
                    if (index >= 0 && index < contents.length) {
                        contents[index] = filler.clone();
                    }
                }
            }
        }

        applyDesignTemplate(player, contents);

        final ConfigurationSection itemsSection = menuSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                final ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) {
                    continue;
                }
                final Optional<Integer> slot = createItem(player, itemSection, contents);
                slot.ifPresent(index -> storeActions(index, itemSection));
            }
        }

        if (debugAsyncMenu) {
            plugin.getLogger().info("[DEBUG E] Menu '" + menuId + "' construit. Appel de setContents().");
        }
        inventory.setContents(contents);
        if (debugAsyncMenu) {
            plugin.getLogger().info("[DEBUG F] 'setContents' terminé. Appel de player.openInventory().");
        }
        player.openInventory(inventory);
        scheduleDeferredHeadUpdates(player);
        if (debugAsyncMenu) {
            plugin.getLogger().info("[DEBUG G] 'openInventory' appelé. Tâche terminée.");
        }
        asyncPreloadedHeads = Map.of();
        asyncPreloadedPlaceholders = Map.of();
    }

    private String applyAsyncPlaceholders(final String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        final Map<String, String> replacements = asyncPreloadedPlaceholders;
        if (replacements == null || replacements.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            final String placeholder = entry.getKey();
            if (placeholder == null || placeholder.isEmpty()) {
                continue;
            }
            final String value = entry.getValue();
            result = result.replace(placeholder, value != null ? value : "");
        }
        return result;
    }

    private List<String> applyAsyncPlaceholders(final List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        final List<String> processed = new ArrayList<>(lines.size());
        for (String line : lines) {
            processed.add(applyAsyncPlaceholders(line));
        }
        return processed;
    }

    private void applyDesignTemplate(final Player player, final ItemStack[] contents) {
        if (contents == null || menuDesignProvider == null) {
            return;
        }
        final String templateName = menuSection.getString("design_template");
        if (templateName == null || templateName.isBlank()) {
            return;
        }
        final Optional<MenuDesign> designOptional = menuDesignProvider.getDesign(templateName);
        if (designOptional.isEmpty()) {
            return;
        }
        final MenuDesign design = designOptional.get();
        final ItemStack decorative = design.createDecorativeItem();
        for (Integer slot : design.decorationSlots()) {
            if (slot == null) {
                continue;
            }
            final int index = slot;
            if (index >= 0 && index < contents.length) {
                contents[index] = decorative.clone();
            }
        }
        final ItemStack border = design.createBorderItem();
        for (Integer slot : design.borderSlots()) {
            if (slot == null) {
                continue;
            }
            final int index = slot;
            if (index >= 0 && index < contents.length) {
                contents[index] = border.clone();
            }
        }
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

    @Override
    public List<String> getActionsForSlot(final int slot) {
        if (slot < 0) {
            return List.of();
        }
        final List<String> actions = actionsBySlot.get(slot);
        return actions != null ? actions : List.of();
    }

    private Optional<Integer> createItem(final Player player,
                                         final ConfigurationSection itemSection,
                                         final ItemStack[] contents) {
        String materialName = itemSection.getString("material");
        Material material = materialName != null ? Material.matchMaterial(materialName) : null;
        final boolean headDefined = itemSection.contains("head") || itemSection.contains("head_id");
        if (material == null) {
            if (materialName != null && !materialName.isBlank()) {
                LogUtils.warning(plugin, "Invalid material '" + materialName + "' in menu '" + menuId + "'.");
            }
            material = headDefined ? Material.PLAYER_HEAD : Material.STONE;
        }

        final int amount = Math.max(1, itemSection.getInt("amount", 1));
        String rawHead = itemSection.getString("head");
        if (rawHead == null) {
            rawHead = itemSection.getString("head_id");
        }
        final String resolvedHead = resolveHeadValue(rawHead, player);

        final boolean requestHeadFromDatabase = material == Material.PLAYER_HEAD && resolvedHead != null
                && resolvedHead.toLowerCase(Locale.ROOT).startsWith("hdb:");

        ItemStack itemStack;
        ItemStack deferredHeadItem = null;

        if (requestHeadFromDatabase) {
            final Material fallbackMaterial = resolveHeadFallbackMaterial(itemSection.getString("head_fallback_material"));
            final ItemStack preloaded = getPreloadedHead(resolvedHead, fallbackMaterial, amount);
            if (preloaded != null) {
                deferredHeadItem = preloaded;
                itemStack = createFallbackHead(amount, fallbackMaterial);
            } else {
                itemStack = createFallbackHead(amount, fallbackMaterial);
                LogUtils.warning(plugin, "Async head preload missing for '" + resolvedHead + "' in menu '" + menuId
                        + "'. Using fallback material " + itemStack.getType() + '.');
            }
        } else {
            itemStack = new ItemStack(material, amount);
        }

        configureItemMeta(player, itemSection, rawHead, resolvedHead, itemStack, !requestHeadFromDatabase);

        if (deferredHeadItem != null) {
            configureItemMeta(player, itemSection, rawHead, resolvedHead, deferredHeadItem, false);
        }

        final int slot = itemSection.getInt("slot", -1);
        final int targetSlot = resolveSlot(slot, itemStack, contents);
        if (targetSlot < 0) {
            return Optional.empty();
        }
        if (deferredHeadItem != null) {
            deferredHeadUpdates.put(targetSlot, deferredHeadItem);
        }
        return Optional.of(targetSlot);
    }

    private void scheduleDeferredHeadUpdates(final Player player) {
        if (deferredHeadUpdates.isEmpty() || inventory == null || player == null) {
            return;
        }
        final Deque<Map.Entry<Integer, ItemStack>> queue = new ArrayDeque<>();
        deferredHeadUpdates.forEach((slot, item) -> {
            if (item != null) {
                queue.add(new AbstractMap.SimpleEntry<>(slot, item));
            }
        });
        if (queue.isEmpty()) {
            deferredHeadUpdates.clear();
            return;
        }
        cancelDeferredHeadTask();
        deferredHeadTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (queue.isEmpty()) {
                cleanupDeferredHeadUpdates();
                return;
            }
            if (!player.isOnline() || !isInventoryOpenFor(player)) {
                queue.clear();
                cleanupDeferredHeadUpdates();
                return;
            }
            int processed = 0;
            while (processed < MAX_HEAD_UPDATES_PER_TICK && !queue.isEmpty()) {
                final Map.Entry<Integer, ItemStack> entry = queue.poll();
                if (entry == null) {
                    continue;
                }
                final int slot = entry.getKey();
                final ItemStack headItem = entry.getValue();
                if (inventory != null && slot >= 0 && slot < inventory.getSize() && headItem != null) {
                    inventory.setItem(slot, headItem);
                }
                deferredHeadUpdates.remove(slot);
                processed++;
            }
            if (queue.isEmpty() || deferredHeadUpdates.isEmpty()) {
                queue.clear();
                cleanupDeferredHeadUpdates();
            }
        }, HEAD_UPDATE_INITIAL_DELAY, HEAD_UPDATE_INTERVAL);
    }

    private boolean isInventoryOpenFor(final Player player) {
        if (player == null || inventory == null) {
            return false;
        }
        final InventoryView view = player.getOpenInventory();
        return view != null && inventory.equals(view.getTopInventory());
    }

    private void cleanupDeferredHeadUpdates() {
        deferredHeadUpdates.clear();
        cancelDeferredHeadTask();
    }

    private void cancelDeferredHeadTask() {
        if (deferredHeadTask != null) {
            deferredHeadTask.cancel();
            deferredHeadTask = null;
        }
    }

    private void configureItemMeta(final Player player,
                                    final ConfigurationSection itemSection,
                                    final String rawHead,
                                    final String resolvedHead,
                                    final ItemStack itemStack,
                                    final boolean applyHeadOwner) {
        if (itemSection == null || itemStack == null) {
            return;
        }
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return;
        }

        if (itemSection.isString("name")) {
            final String processedName = applyAsyncPlaceholders(PlaceholderUtils.applyPlaceholders(plugin,
                    itemSection.getString("name"), player));
            if (processedName != null) {
                meta.setDisplayName(MessageUtils.colorize(processedName));
            }
        }

        if (itemSection.isList("lore")) {
            final List<String> lore = applyAsyncPlaceholders(
                    PlaceholderUtils.applyPlaceholders(plugin, itemSection.getStringList("lore"), player))
                    .stream()
                    .map(MessageUtils::colorize)
                    .toList();
            meta.setLore(lore);
        } else if (itemSection.isString("lore")) {
            final String processedLore = applyAsyncPlaceholders(
                    PlaceholderUtils.applyPlaceholders(plugin, itemSection.getString("lore"), player));
            if (processedLore != null && !processedLore.isBlank()) {
                final List<String> lore = Arrays.stream(processedLore.split("\\n"))
                        .map(MessageUtils::colorize)
                        .toList();
                meta.setLore(lore);
            }
        }

        if (itemSection.contains("custom_model_data")) {
            meta.setCustomModelData(itemSection.getInt("custom_model_data"));
        }

        if (itemSection.getBoolean("glow", false)) {
            meta.addItemFlags(ItemFlag.values());
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        }

        if (applyHeadOwner && meta instanceof SkullMeta skullMeta) {
            applyHead(rawHead, resolvedHead, player, skullMeta);
        }

        itemStack.setItemMeta(meta);
    }

    private void applyHead(final String rawHead, final String resolvedHead, final Player player, final SkullMeta skullMeta) {
        if (rawHead == null || rawHead.isBlank()) {
            return;
        }
        if (resolvedHead != null && resolvedHead.toLowerCase(Locale.ROOT).startsWith("hdb:")) {
            return;
        }
        if (player != null && rawHead.equalsIgnoreCase("%player_name%")) {
            skullMeta.setOwningPlayer(player);
            return;
        }
        final String processed = resolvedHead != null
                ? resolvedHead
                : applyAsyncPlaceholders(PlaceholderUtils.applyPlaceholders(plugin, rawHead, player));
        if (processed == null || processed.isBlank()) {
            return;
        }
        if (player != null && processed.equalsIgnoreCase(player.getName())) {
            skullMeta.setOwningPlayer(player);
            return;
        }
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(processed);
        skullMeta.setOwningPlayer(offlinePlayer);
    }

    private void collectHeadRequests(final ConfigurationSection section,
                                     final Player player,
                                     final Set<Menu.HeadRequest> sink) {
        if (section == null || sink == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            if (key == null) {
                continue;
            }
            final ConfigurationSection itemSection = section.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            String rawHead = itemSection.getString("head");
            if (rawHead == null) {
                rawHead = itemSection.getString("head_id");
            }
            if (rawHead == null || rawHead.isBlank()) {
                continue;
            }
            final String resolvedHead = resolveHeadValue(rawHead, player);
            if (resolvedHead == null) {
                continue;
            }
            final String normalized = resolvedHead.toLowerCase(Locale.ROOT);
            if (!normalized.startsWith("hdb:")) {
                continue;
            }
            final Material fallback = resolveHeadFallbackMaterial(itemSection.getString("head_fallback_material"));
            sink.add(new Menu.HeadRequest(resolvedHead, fallback));
        }
    }

    private ItemStack getPreloadedHead(final String headId,
                                       final Material fallbackMaterial,
                                       final int amount) {
        if (headId == null) {
            return null;
        }
        final Map<Menu.HeadRequest, ItemStack> cache = asyncPreloadedHeads;
        if (cache == null || cache.isEmpty()) {
            return null;
        }
        final Menu.HeadRequest key = new Menu.HeadRequest(headId, fallbackMaterial);
        final ItemStack preloaded = cache.get(key);
        if (preloaded == null) {
            return null;
        }
        final ItemStack clone = preloaded.clone();
        clone.setAmount(Math.max(1, amount));
        return clone;
    }

    private ItemStack createFallbackHead(final int amount, final Material fallbackMaterial) {
        final Material material = fallbackMaterial != null ? fallbackMaterial : Material.PLAYER_HEAD;
        final ItemStack fallback = new ItemStack(material);
        fallback.setAmount(amount);
        return fallback;
    }

    private Material resolveHeadFallbackMaterial(final String fallbackName) {
        if (fallbackName == null || fallbackName.isBlank()) {
            return Material.PLAYER_HEAD;
        }
        final Material material = Material.matchMaterial(fallbackName.trim());
        if (material == null) {
            LogUtils.warning(plugin, "Invalid head fallback material '" + fallbackName + "' in menu '" + menuId + "'.");
            return Material.PLAYER_HEAD;
        }
        return material;
    }

    private String resolveHeadValue(final String head, final Player player) {
        if (head == null || head.isBlank()) {
            return null;
        }
        final String processed = applyAsyncPlaceholders(PlaceholderUtils.applyPlaceholders(plugin, head, player));
        if (processed == null || processed.isBlank()) {
            return null;
        }
        final var templateManager = plugin.getUiTemplateManager();
        if (templateManager != null) {
            if (processed.startsWith("@")) {
                final String identifier = processed.substring(1);
                final String resolved = templateManager.resolveHead(identifier);
                if (resolved != null && !resolved.isBlank()) {
                    return resolved;
                }
            }
            final String resolved = templateManager.resolveHead(processed);
            if (resolved != null && !resolved.isBlank()) {
                return resolved;
            }
        }
        return processed;
    }

    private void applyBorders(final Player player,
                               final DesignTemplate designTemplate,
                               final ItemStack[] contents) {
        if (contents == null) {
            return;
        }
        final List<Map<?, ?>> borders = new ArrayList<>();
        if (designTemplate != null) {
            borders.addAll(designTemplate.getBorders());
        }
        final List<Map<?, ?>> menuBorders = menuSection.getMapList("borders");
        if (menuBorders != null) {
            borders.addAll(menuBorders);
        }
        if (borders.isEmpty()) {
            return;
        }
        for (Map<?, ?> definition : borders) {
            if (definition == null || definition.isEmpty()) {
                continue;
            }
            final ItemStack borderItem = createBorderItem(player, definition);
            if (borderItem == null) {
                continue;
            }
            final List<Integer> slots = parseSlots(definition.get("slots"));
            for (Integer slot : slots) {
                if (slot == null) {
                    continue;
                }
                final int index = slot;
                if (index >= 0 && index < contents.length) {
                    contents[index] = borderItem.clone();
                }
            }
        }
    }

    private void applyTemplateItems(final Player player,
                                     final DesignTemplate designTemplate,
                                     final ItemStack[] contents) {
        if (designTemplate == null) {
            return;
        }
        final ConfigurationSection templateItems = designTemplate.getItemsSection();
        if (templateItems == null) {
            return;
        }
        for (String key : templateItems.getKeys(false)) {
            if (key == null) {
                continue;
            }
            final ConfigurationSection itemSection = templateItems.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final Optional<Integer> slot = createItem(player, itemSection, contents);
            slot.ifPresent(index -> storeActions(index, itemSection));
        }
    }

    private ItemStack createBorderItem(final Player player, final Map<?, ?> definition) {
        final Object materialObject = definition.containsKey("material")
                ? definition.get("material")
                : "BLACK_STAINED_GLASS_PANE";
        final String materialName = materialObject != null ? materialObject.toString() : "BLACK_STAINED_GLASS_PANE";
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LogUtils.warning(plugin, "Invalid border material '" + materialName + "' in menu '" + menuId + "'.");
            return null;
        }
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            final Object nameObject = definition.get("name");
            final String rawName = nameObject != null
                    ? applyAsyncPlaceholders(PlaceholderUtils.applyPlaceholders(plugin, nameObject.toString(), player))
                    : " ";
            final String coloredName = rawName != null ? MessageUtils.colorize(rawName) : " ";
            meta.setDisplayName((coloredName == null || coloredName.isBlank()) ? " " : coloredName);
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private List<Integer> parseSlots(final Object value) {
        if (value instanceof List<?> list) {
            final List<Integer> slots = new ArrayList<>();
            for (Object element : list) {
                if (element instanceof Number number) {
                    slots.add(number.intValue());
                } else if (element instanceof String string) {
                    try {
                        slots.add(Integer.parseInt(string.trim()));
                    } catch (final NumberFormatException ignored) {
                        // Ignore invalid entries
                    }
                }
            }
            return slots;
        }
        return List.of();
    }

    private void storeActions(final int slot, final ConfigurationSection itemSection) {
        final List<String> sanitized = new ArrayList<>();
        if (itemSection.isString("action")) {
            final String action = itemSection.getString("action");
            if (action != null && !action.isBlank()) {
                sanitized.add(action.trim());
            }
        }
        if (itemSection.isList("actions")) {
            for (String action : itemSection.getStringList("actions")) {
                if (action == null || action.isBlank()) {
                    continue;
                }
                sanitized.add(action.trim());
            }
        }
        if (!sanitized.isEmpty()) {
            actionsBySlot.put(slot, List.copyOf(sanitized));
        }
    }

    private DesignTemplate resolveDesignTemplate() {
        final String designName = menuSection.getString("design");
        if (designName == null || designName.isBlank()) {
            return null;
        }
        if (plugin.getUiTemplateManager() == null) {
            return null;
        }
        return plugin.getUiTemplateManager().getDesignTemplate(designName);
    }

    private String resolveFillMaterial(final DesignTemplate designTemplate) {
        if (menuSection.contains("fill_material")) {
            return menuSection.getString("fill_material");
        }
        return designTemplate != null ? designTemplate.getFillMaterial() : null;
    }

    private List<Integer> resolveFillSlots(final DesignTemplate designTemplate) {
        if (menuSection.contains("fill_slots")) {
            return menuSection.getIntegerList("fill_slots");
        }
        return designTemplate != null ? designTemplate.getFillSlots() : List.of();
    }

    private int resolveSlot(final int slot, final ItemStack itemStack, final ItemStack[] contents) {
        if (contents == null || itemStack == null) {
            return -1;
        }
        if (slot >= 0 && slot < contents.length) {
            contents[slot] = itemStack;
            return slot;
        }
        for (int index = 0; index < contents.length; index++) {
            if (isEmpty(contents[index])) {
                contents[index] = itemStack;
                return index;
            }
        }
        return -1;
    }

    private boolean isEmpty(final ItemStack itemStack) {
        return itemStack == null || itemStack.getType() == Material.AIR;
    }

    private int normalizeSize(final int requested) {
        final int clamped = Math.max(9, Math.min(54, requested));
        final int remainder = clamped % 9;
        return remainder == 0 ? clamped : clamped + (9 - remainder);
    }

    private ItemStack createFillerItem(final String materialName) {
        if (materialName == null || materialName.isBlank()) {
            return null;
        }
        final Material material = Material.matchMaterial(materialName);
        if (material == null) {
            LogUtils.warning(plugin, "Invalid filler material '" + materialName + "' in menu '" + menuId + "'.");
            return null;
        }
        final ItemStack itemStack = new ItemStack(material);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    private ActionProcessor resolveActionProcessor() {
        return Optional.ofNullable(plugin.getNpcManager())
                .map(npcManager -> npcManager.getActionProcessor())
                .orElse(null);
    }
}
