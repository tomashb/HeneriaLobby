package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.servers.ServerManager;
import com.lobby.social.ChatInputManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generic menu implementation that builds its inventory from a YAML
 * configuration file. The configuration supports defining items with either a
 * single {@code slot} or a list of {@code slots}, an optional display name,
 * lore and click action. Materials can reference Bukkit {@link Material}
 * enums or custom heads using the {@code hdb:<id>} syntax handled by the
 * {@link AssetManager}.
 */
public final class ConfiguredMenu implements Menu, InventoryHolder {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final int MAX_INVENTORY_SIZE = 54;
    private static final int MIN_INVENTORY_SIZE = 9;
    private static final String DEFAULT_TITLE = "&8Menu";
    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final String menuId;
    private final Component title;
    private final int size;
    private final List<MenuItemDefinition> items;
    private final MenuRenderContext renderContext;
    private final Map<String, String> additionalPlaceholders;

    private final Map<Integer, MenuAction> actions = new HashMap<>();
    private Inventory inventory;

    private ConfiguredMenu(final LobbyPlugin plugin,
                           final MenuManager menuManager,
                           final AssetManager assetManager,
                           final String menuId,
                           final Component title,
                           final int size,
                           final List<MenuItemDefinition> items,
                           final MenuRenderContext renderContext,
                           final Map<String, String> additionalPlaceholders) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.menuId = menuId;
        this.title = title;
        this.size = size;
        this.items = items;
        this.renderContext = renderContext == null ? MenuRenderContext.EMPTY : renderContext;
        if (additionalPlaceholders == null || additionalPlaceholders.isEmpty()) {
            this.additionalPlaceholders = Map.of();
        } else {
            this.additionalPlaceholders = Collections.unmodifiableMap(new HashMap<>(additionalPlaceholders));
        }
    }

    public static ConfiguredMenu fromConfiguration(final LobbyPlugin plugin,
                                                   final MenuManager menuManager,
                                                   final AssetManager assetManager,
                                                   final String menuId) {
        return fromConfiguration(plugin, menuManager, assetManager, menuId, Map.of(), MenuRenderContext.EMPTY);
    }

    public static ConfiguredMenu fromConfiguration(final LobbyPlugin plugin,
                                                   final MenuManager menuManager,
                                                   final AssetManager assetManager,
                                                   final String menuId,
                                                   final Map<String, String> extraPlaceholders,
                                                   final MenuRenderContext renderContext) {
        if (plugin == null || menuId == null || menuId.isBlank()) {
            return null;
        }

        final File menuFile = resolveMenuFile(plugin, menuId.toLowerCase(Locale.ROOT));
        if (menuFile == null) {
            plugin.getLogger().warning("Unable to load menu configuration for " + menuId + ": file is missing.");
            return null;
        }

        final YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(menuFile);
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Failed to load menu configuration '" + menuFile.getName() + "': "
                    + exception.getMessage());
            return null;
        }

        final String rawTitle = configuration.getString("title", DEFAULT_TITLE);
        final Component title = LEGACY_SERIALIZER.deserialize(colorize(rawTitle));
        final int requestedSize = configuration.getInt("size", MAX_INVENTORY_SIZE);
        final int size = normalizeInventorySize(requestedSize);

        final List<MenuItemDefinition> definitions = new ArrayList<>();
        appendDesignItems(plugin, menuId, configuration.getConfigurationSection("design"), definitions);

        final ConfigurationSection itemsSection = configuration.getConfigurationSection("items");
        if (itemsSection == null) {
            plugin.getLogger().warning("Menu '" + menuId + "' does not define an items section.");
            return null;
        }

        for (String key : itemsSection.getKeys(false)) {
            final ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
            if (itemSection == null) {
                continue;
            }
            final MenuItemDefinition definition = parseItemDefinition(plugin, menuId, key, itemSection);
            if (definition != null) {
                definitions.add(definition);
            }
        }

        if (definitions.isEmpty()) {
            plugin.getLogger().warning("Menu '" + menuId + "' does not define any items.");
        }
        return new ConfiguredMenu(plugin, menuManager, assetManager, menuId, title, size,
                Collections.unmodifiableList(definitions), renderContext, extraPlaceholders);
    }

    @Override
    public void open(final Player player) {
        if (player == null) {
            return;
        }
        inventory = Bukkit.createInventory(this, size, title);
        actions.clear();

        final ItemStack[] contents = new ItemStack[size];
        final Map<String, String> placeholders = buildPlaceholderMap(player);
        if (!additionalPlaceholders.isEmpty()) {
            placeholders.putAll(additionalPlaceholders);
        }

        for (MenuItemDefinition definition : items) {
            if (!definition.condition().isSatisfied(renderContext)) {
                continue;
            }
            final ItemStack baseItem = definition.createItem(assetManager, player, placeholders);
            if (baseItem == null) {
                continue;
            }
            for (int slot : definition.slots()) {
                if (slot < 0 || slot >= size) {
                    plugin.getLogger().warning("Menu '" + menuId + "' tried to place item outside of inventory bounds: "
                            + slot);
                    continue;
                }
                final ItemStack item = baseItem.clone();
                item.setAmount(definition.amount());
                contents[slot] = item;
                if (definition.action().type != ActionType.NONE) {
                    actions.put(slot, definition.action());
                }
            }
        }

        inventory.setContents(contents);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        final MenuAction action = actions.get(event.getSlot());
        if (action == null || action.type == ActionType.NONE) {
            return;
        }
        switch (action.type) {
            case SERVER_SEND -> sendToServer(player, action.argument);
            case MENU -> openMenu(player, action.argument);
            case CLOSE_MENU -> player.closeInventory();
            case MESSAGE -> {
                if (!action.argument.isBlank()) {
                    player.sendMessage(colorize(action.argument));
                }
            }
            case COMMAND -> executeCommand(player, action.argument);
            case CHAT_PROMPT -> startChatPrompt(player, action);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void sendToServer(final Player player, final String serverId) {
        if (serverId == null || serverId.isBlank()) {
            return;
        }
        final ServerManager serverManager = plugin.getServerManager();
        if (serverManager == null) {
            player.sendMessage("§cAucun serveur n'est disponible actuellement.");
            return;
        }
        player.closeInventory();
        serverManager.sendPlayerToServer(player, serverId);
    }

    private void openMenu(final Player player, final String targetMenu) {
        if (targetMenu == null || targetMenu.isBlank()) {
            return;
        }
        if (!menuManager.openMenu(player, targetMenu)) {
            player.sendMessage("§cCe menu est actuellement indisponible.");
        }
    }

    private void executeCommand(final Player player, final String rawCommand) {
        if (player == null || rawCommand == null || rawCommand.isBlank()) {
            return;
        }
        final String command = rawCommand.startsWith("/") ? rawCommand.substring(1) : rawCommand;
        if (command.isBlank()) {
            return;
        }
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(command));
    }

    private void startChatPrompt(final Player player, final MenuAction action) {
        if (player == null || action == null) {
            return;
        }
        try {
            final String flow = action.flow();
            if (flow != null && !flow.isBlank()) {
                if ("clan_create".equalsIgnoreCase(flow)) {
                    ChatInputManager.startClanCreationFlow(player, action.argument(), action.reopenMenu());
                    return;
                }
            }
            if (action.command() != null && !action.command().isBlank()) {
                ChatInputManager.startCommandPrompt(player, action.argument(), action.command(), action.reopenMenu());
                return;
            }
        } catch (final IllegalStateException exception) {
            player.sendMessage("§cLes actions de chat sont indisponibles pour le moment.");
            return;
        }
        if (action.argument() != null && !action.argument().isBlank()) {
            player.closeInventory();
            player.sendMessage(colorize(action.argument()));
        }
    }

    private Map<String, String> buildPlaceholderMap(final Player player) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player_name%", player.getName());
        placeholders.put("%player_displayname%", player.getDisplayName());
        assetManager.snapshotPlaceholders().forEach(placeholders::put);
        return placeholders;
    }

    private static void appendDesignItems(final LobbyPlugin plugin,
                                          final String menuId,
                                          final ConfigurationSection designSection,
                                          final List<MenuItemDefinition> definitions) {
        if (designSection == null) {
            return;
        }
        final ConfigurationSection primary = designSection.getConfigurationSection("primary_border");
        if (primary != null) {
            final MenuItemDefinition definition = parseItemDefinition(plugin, menuId,
                    "design-primary-border", primary);
            if (definition != null) {
                definitions.add(definition);
            }
        }
        final ConfigurationSection secondary = designSection.getConfigurationSection("secondary_border");
        if (secondary != null) {
            final MenuItemDefinition definition = parseItemDefinition(plugin, menuId,
                    "design-secondary-border", secondary);
            if (definition != null) {
                definitions.add(definition);
            }
        }
    }

    private static MenuItemDefinition parseItemDefinition(final LobbyPlugin plugin,
                                                          final String menuId,
                                                          final String itemId,
                                                          final ConfigurationSection section) {
        final List<Integer> slots = resolveSlots(section);
        if (slots.isEmpty()) {
            plugin.getLogger().warning("Menu '" + menuId + "' item '" + itemId + "' does not specify any slot.");
            return null;
        }
        final String material = section.getString("material", "BARRIER");
        final String name = section.getString("name", "&r");
        final List<String> lore = section.getStringList("lore");
        final String actionValue = section.getString("action");
        final int amount = Math.max(1, Math.min(64, section.getInt("amount", 1)));
        final String skullOwner = section.getString("skull-owner");
        final boolean usePlayerHead = section.getBoolean("player-head", false)
                || "PLAYER_HEAD".equalsIgnoreCase(material);
        final MenuAction action = MenuAction.parse(actionValue, section);
        final MenuItemCondition condition = MenuItemCondition.fromSection(section.getConfigurationSection("conditions"));
        return new MenuItemDefinition(itemId, slots, material, name, lore, action, amount, usePlayerHead,
                skullOwner, condition);
    }

    private static File resolveMenuFile(final LobbyPlugin plugin, final String menuId) {
        final File menusDirectory = new File(plugin.getDataFolder(), "config/menus");
        if (!menusDirectory.exists() && !menusDirectory.mkdirs()) {
            plugin.getLogger().warning("Unable to create config/menus directory for menu configurations.");
        }
        final File menuFile = new File(menusDirectory, menuId + ".yml");
        if (menuFile.exists()) {
            return menuFile;
        }
        try {
            plugin.saveResource("config/menus/" + menuId + ".yml", false);
        } catch (final IllegalArgumentException ignored) {
            // No default resource available, continue with potential custom file.
        }
        return menuFile.exists() ? menuFile : null;
    }

    private static int normalizeInventorySize(final int requestedSize) {
        final int clamped = Math.max(MIN_INVENTORY_SIZE, Math.min(MAX_INVENTORY_SIZE, requestedSize));
        return (clamped % 9 == 0) ? clamped : ((clamped / 9) + 1) * 9;
    }

    private static List<Integer> resolveSlots(final ConfigurationSection section) {
        if (section == null) {
            return List.of();
        }
        final Set<Integer> slots = new LinkedHashSet<>();
        final List<Integer> slotList = section.getIntegerList("slots");
        slots.addAll(slotList);
        if (section.contains("slot")) {
            slots.add(section.getInt("slot"));
        }
        return new ArrayList<>(slots);
    }

    private static String colorize(final String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private enum ActionType {
        NONE,
        SERVER_SEND,
        MENU,
        CLOSE_MENU,
        MESSAGE,
        COMMAND,
        CHAT_PROMPT
    }

    private record MenuAction(ActionType type,
                              String argument,
                              String command,
                              String reopenMenu,
                              String flow) {

        private static final MenuAction NONE = new MenuAction(ActionType.NONE, "", null, null, null);

        private static MenuAction parse(final String raw, final ConfigurationSection section) {
            if (raw == null || raw.isBlank()) {
                return NONE;
            }
            final String trimmed = raw.trim();
            if (!trimmed.startsWith("[") || !trimmed.contains("]")) {
                return NONE;
            }
            final int closingIndex = trimmed.indexOf(']');
            final String typeName = trimmed.substring(1, closingIndex).trim().toUpperCase(Locale.ROOT);
            final String argument = trimmed.substring(closingIndex + 1).trim();
            return switch (typeName) {
                case "SERVER_SEND" -> new MenuAction(ActionType.SERVER_SEND, argument, null, null, null);
                case "MENU" -> new MenuAction(ActionType.MENU, argument, null, null, null);
                case "CLOSE_MENU" -> new MenuAction(ActionType.CLOSE_MENU, argument, null, null, null);
                case "MESSAGE" -> new MenuAction(ActionType.MESSAGE, argument, null, null, null);
                case "COMMAND" -> new MenuAction(ActionType.COMMAND, argument, null, null, null);
                case "CHAT_PROMPT" -> {
                    final String command = readChatPromptValue(section, "command");
                    final String reopenMenu = readChatPromptValue(section, "reopen_menu");
                    final String flow = readChatPromptValue(section, "flow");
                    yield new MenuAction(ActionType.CHAT_PROMPT, argument, command, reopenMenu, flow);
                }
                default -> NONE;
            };
        }

        private static String readChatPromptValue(final ConfigurationSection section, final String key) {
            if (section == null || key == null || key.isBlank()) {
                return null;
            }
            final ConfigurationSection promptSection = section.getConfigurationSection("chat_prompt");
            if (promptSection == null) {
                return null;
            }
            final String value = promptSection.getString(key);
            return (value == null || value.isBlank()) ? null : value;
        }
    }

    private record MenuItemDefinition(String id,
                                      List<Integer> slots,
                                      String materialKey,
                                      String displayName,
                                      List<String> lore,
                                      MenuAction action,
                                      int amount,
                                      boolean playerHead,
                                      String skullOwner,
                                      MenuItemCondition condition) {

        private ItemStack createItem(final AssetManager assetManager,
                                     final Player player,
                                     final Map<String, String> placeholders) {
            final ItemStack base = resolveBaseItem(assetManager, materialKey);
            if (base == null) {
                return null;
            }
            final ItemMeta meta = base.getItemMeta();
            if (meta != null) {
                final String name = apply(placeholders, displayName);
                final String displayName = (name == null || name.isBlank()) ? "&r" : name;
                meta.setDisplayName(colorize(displayName));
                final List<String> renderedLore = renderLore(placeholders, lore);
                if (!renderedLore.isEmpty()) {
                    meta.setLore(renderedLore);
                }
                if (meta instanceof SkullMeta skullMeta) {
                    if (skullOwner != null && !skullOwner.isBlank()) {
                        final String owner = apply(placeholders, skullOwner);
                        if (owner != null && !owner.isBlank()) {
                            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                        }
                    } else if (playerHead) {
                        skullMeta.setOwningPlayer(player);
                    }
                }
                base.setItemMeta(meta);
            }
            return base;
        }

        private static ItemStack resolveBaseItem(final AssetManager assetManager, final String materialKey) {
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

        private static List<String> renderLore(final Map<String, String> placeholders, final Collection<String> rawLore) {
            if (rawLore == null || rawLore.isEmpty()) {
                return List.of();
            }
            final List<String> rendered = new ArrayList<>(rawLore.size());
            for (String line : rawLore) {
                if (line == null) {
                    continue;
                }
                rendered.add(colorize(apply(placeholders, line)));
            }
            return rendered;
        }

        private static String apply(final Map<String, String> placeholders, final String input) {
            if (input == null) {
                return "";
            }
            String result = input;
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }
    }

    private enum Requirement {
        ANY,
        MEMBER,
        NONE,
        LEADER;

        private static Requirement fromString(final String raw) {
            if (raw == null || raw.isBlank()) {
                return ANY;
            }
            final String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "member", "present", "oui", "yes", "true" -> MEMBER;
                case "leader", "chef" -> LEADER;
                case "none", "absent", "no", "false" -> NONE;
                default -> ANY;
            };
        }

        private boolean isSatisfied(final boolean has, final boolean leader) {
            return switch (this) {
                case ANY -> true;
                case MEMBER -> has;
                case NONE -> !has;
                case LEADER -> leader;
            };
        }
    }

    private record MenuItemCondition(Requirement groupRequirement, Requirement clanRequirement) {

        private static final MenuItemCondition NONE = new MenuItemCondition(Requirement.ANY, Requirement.ANY);

        private static MenuItemCondition fromSection(final ConfigurationSection section) {
            if (section == null) {
                return NONE;
            }
            final Requirement group = Requirement.fromString(section.getString("group"));
            final Requirement clan = Requirement.fromString(section.getString("clan"));
            if (group == Requirement.ANY && clan == Requirement.ANY) {
                return NONE;
            }
            return new MenuItemCondition(group, clan);
        }

        private boolean isSatisfied(final MenuRenderContext context) {
            if (context == null) {
                return groupRequirement == Requirement.ANY && clanRequirement == Requirement.ANY;
            }
            return groupRequirement.isSatisfied(context.hasGroup(), context.groupLeader())
                    && clanRequirement.isSatisfied(context.hasClan(), context.clanLeader());
        }
    }
}
