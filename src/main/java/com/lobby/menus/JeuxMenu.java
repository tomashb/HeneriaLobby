package com.lobby.menus;

import com.lobby.LobbyPlugin;
import com.lobby.servers.ServerManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.Set;

public class JeuxMenu implements Menu, InventoryHolder {

    private static final int INVENTORY_SIZE = 54;
    private static final String TITLE = "§8» §3Sélecteur de Jeux";

    private static final int SLOT_BEDWARS = 20;
    private static final int SLOT_NEXUS = 22;
    private static final int SLOT_ZOMBIE = 24;
    private static final int SLOT_CUSTOM = 31;
    private static final int SLOT_PROFILE = 48;
    private static final int SLOT_SHOP = 49;
    private static final int SLOT_CLOSE = 50;
    private static final Set<Integer> DECORATION_SLOTS = Set.of(
            0, 1, 2, 6, 7, 8,
            9, 17, 35,
            44, 45, 46,
            52, 53
    );

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final Inventory inventory;

    public JeuxMenu(final LobbyPlugin plugin, final MenuManager menuManager, final AssetManager assetManager) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.inventory = Bukkit.createInventory(this, INVENTORY_SIZE, TITLE);
    }

    @Override
    public void open(final Player player) {
        buildItems(player);
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        switch (event.getSlot()) {
            case SLOT_BEDWARS -> sendToServer(player, "bedwars");
            case SLOT_NEXUS -> sendToServer(player, "nexus");
            case SLOT_ZOMBIE -> sendToServer(player, "zombie");
            case SLOT_CUSTOM -> sendToServer(player, "custom");
            case SLOT_PROFILE -> openMenu(player, "profil_menu", "§cLe profil est actuellement indisponible.");
            case SLOT_SHOP -> openMenu(player, "shop_menu", "§cLa boutique est actuellement indisponible.");
            case SLOT_CLOSE -> player.closeInventory();
            default -> {
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void buildItems(final Player player) {
        final ItemStack[] contents = new ItemStack[INVENTORY_SIZE];

        final ItemStack decoration = createDecorationPane();
        for (int slot : DECORATION_SLOTS) {
            contents[slot] = decoration.clone();
        }

        contents[SLOT_BEDWARS] = createBedwarsItem();
        contents[SLOT_NEXUS] = createNexusItem();
        contents[SLOT_ZOMBIE] = createZombieItem();
        contents[SLOT_CUSTOM] = createCustomItem();
        contents[SLOT_PROFILE] = createProfileItem(player);
        contents[SLOT_SHOP] = createShopItem();
        contents[SLOT_CLOSE] = createCloseItem();

        inventory.setContents(contents);
    }

    private ItemStack createDecorationPane() {
        final ItemStack pane = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        final ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("     ");
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private ItemStack createBedwarsItem() {
        final ItemStack item = assetManager.getHead("hdb:14138");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String players = assetManager.getGlobalPlaceholder("%lobby_online_bedwars%");
            meta.setDisplayName("§c§lBedWars");
            meta.setLore(List.of(
                    "§d§lDescription",
                    "§7Protégez votre lit et soyez la",
                    "§7dernière équipe en vie.",
                    "§r",
                    "§c§lInformations",
                    "§8▪ Version du jeu : §d1.21",
                    "§8▪ Connecté(s) : §a" + players,
                    "§r",
                    "§a▶ Cliquez pour rejoindre !"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createNexusItem() {
        final ItemStack item = assetManager.getHead("hdb:12822");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String players = assetManager.getGlobalPlaceholder("%lobby_online_nexus%");
            meta.setDisplayName("§b§lNexus");
            meta.setLore(List.of(
                    "§d§lDescription",
                    "§7Détruisez le Nexus ennemi avant",
                    "§7qu'ils n'atteignent le vôtre.",
                    "§r",
                    "§c§lInformations",
                    "§8▪ Version du jeu : §d1.21",
                    "§8▪ Connecté(s) : §a" + players,
                    "§r",
                    "§a▶ Cliquez pour dominer !"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createZombieItem() {
        final ItemStack item = assetManager.getHead("hdb:32038");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String players = assetManager.getGlobalPlaceholder("%lobby_online_zombie%");
            meta.setDisplayName("§2§lSurvie Zombie");
            meta.setLore(List.of(
                    "§d§lDescription",
                    "§7Repoussez des vagues de zombies",
                    "§7et survivez le plus longtemps possible.",
                    "§r",
                    "§c§lInformations",
                    "§8▪ Version du jeu : §d1.21",
                    "§8▪ Connecté(s) : §a" + players,
                    "§r",
                    "§a▶ Cliquez pour survivre !"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCustomItem() {
        final ItemStack item = assetManager.getHead("hdb:23959");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final String players = assetManager.getGlobalPlaceholder("%lobby_online_custom%");
            meta.setDisplayName("§6§lJeux Inédits");
            meta.setLore(List.of(
                    "§d§lDescription",
                    "§7Découvrez nos créations originales",
                    "§7en rotation constante.",
                    "§r",
                    "§c§lInformations",
                    "§8▪ Version du jeu : §d1.21",
                    "§8▪ Connecté(s) : §a" + players,
                    "§r",
                    "§a▶ Cliquez pour explorer !"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createProfileItem(final Player player) {
        final ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
            skullMeta.setDisplayName("§6§lMon Profil");
            skullMeta.setLore(List.of(
                    "§7Accédez à vos statistiques, gérez",
                    "§7vos amis et personnalisez vos",
                    "§7paramètres de jeu.",
                    "§r",
                    "§a▶ Cliquez pour consulter"
            ));
            item.setItemMeta(skullMeta);
        }
        return item;
    }

    private ItemStack createShopItem() {
        final ItemStack item = assetManager.getHead("hdb:35472");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§e§lBoutique");
            meta.setLore(List.of(
                    "§7Dépensez vos gains pour obtenir",
                    "§7des grades, cosmétiques et",
                    "§7avantages exclusifs.",
                    "§r",
                    "§a▶ Cliquez pour acheter"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createCloseItem() {
        final ItemStack item = assetManager.getHead("hdb:9334");
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§c§lFermer le Menu");
            meta.setLore(List.of(
                    "§7Retourner au cœur du lobby.",
                    "§r",
                    "§a▶ Cliquez pour fermer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void sendToServer(final Player player, final String serverId) {
        final ServerManager serverManager = plugin.getServerManager();
        if (serverManager == null) {
            player.sendMessage("§cAucun serveur n'est disponible actuellement.");
            return;
        }
        player.closeInventory();
        serverManager.sendPlayerToServer(player, serverId);
    }

    private void openMenu(final Player player, final String menuId, final String unavailableMessage) {
        if (!menuManager.openMenu(player, menuId)) {
            player.sendMessage(unavailableMessage);
        }
    }
}
