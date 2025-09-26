package com.lobby.social.menus.friends;

import com.lobby.LobbyPlugin;
import com.lobby.economy.EconomyManager;
import com.lobby.menus.AssetManager;
import com.lobby.menus.Menu;
import com.lobby.menus.MenuManager;
import com.lobby.social.menus.SocialHeavyMenus;
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
import org.bukkit.scheduler.BukkitScheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class FriendGiftMenu implements Menu, InventoryHolder {

    private static final int INVENTORY_SIZE = 27;
    private static final String TITLE_TEMPLATE = "&8» &dCadeau pour %s";
    private static final List<Long> COIN_AMOUNTS = List.of(100L, 500L, 1000L, 2500L);

    private final LobbyPlugin plugin;
    private final MenuManager menuManager;
    private final AssetManager assetManager;
    private final EconomyManager economyManager;
    private final UUID targetUuid;
    private final String targetName;

    private Inventory inventory;
    private final Map<Integer, Long> giftAmounts = new HashMap<>();

    public FriendGiftMenu(final LobbyPlugin plugin,
                          final MenuManager menuManager,
                          final AssetManager assetManager,
                          final EconomyManager economyManager,
                          final UUID targetUuid,
                          final String targetName) {
        this.plugin = plugin;
        this.menuManager = menuManager;
        this.assetManager = assetManager;
        this.economyManager = economyManager;
        this.targetUuid = targetUuid;
        this.targetName = targetName == null ? "Inconnu" : targetName;
    }

    @Override
    public void open(final Player player) {
        final String title = ChatColor.translateAlternateColorCodes('&',
                String.format(Locale.FRENCH, TITLE_TEMPLATE, targetName));
        inventory = Bukkit.createInventory(this, INVENTORY_SIZE, title);
        fillBackground();
        placeHeader();
        placeGiftOptions();
        player.openInventory(inventory);
    }

    @Override
    public void handleClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!Objects.equals(event.getClickedInventory(), inventory)) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        if (slot == 18) {
            SocialHeavyMenus.openFriendsMenu(menuManager, player, 0, null);
            return;
        }
        final Long amount = giftAmounts.get(slot);
        if (amount == null) {
            return;
        }
        if (economyManager == null) {
            player.sendMessage("§cLe système économique est indisponible.");
            return;
        }
        transferCoins(player, amount);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void fillBackground() {
        final ItemStack filler = createGlass(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }
        final ItemStack primary = createGlass(Material.LIME_STAINED_GLASS_PANE);
        final int[] primarySlots = {0, 1, 2, 6, 7, 8, 18, 26};
        for (int slot : primarySlots) {
            if (slot < INVENTORY_SIZE) {
                inventory.setItem(slot, primary);
            }
        }
    }

    private void placeHeader() {
        final ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        final ItemMeta baseMeta = head.getItemMeta();
        if (baseMeta instanceof SkullMeta meta) {
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetUuid));
            meta.setDisplayName("§d§l" + targetName);
            meta.setLore(List.of(
                    "§7Envoyez un cadeau pour surprendre",
                    "§7votre ami(e) !"
            ));
            head.setItemMeta(meta);
        }
        inventory.setItem(4, head);
        inventory.setItem(18, decorateButton(assetManager.getHead("hdb:9334"), "§c§lRetour"));
    }

    private void placeGiftOptions() {
        int slot = 10;
        for (long amount : COIN_AMOUNTS) {
            final ItemStack item = createGiftItem(amount);
            inventory.setItem(slot, item);
            giftAmounts.put(slot, amount);
            slot++;
        }
    }

    private ItemStack createGiftItem(final long amount) {
        final ItemStack item = new ItemStack(Material.GOLD_INGOT);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6§l" + amount + " coins");
            meta.setLore(List.of(
                    "§7Envoyer §6" + amount + " coins",
                    "§7au joueur §f" + targetName,
                    "§r",
                    "§e▶ Cliquez pour confirmer"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createGlass(final Material material) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack decorateButton(final ItemStack base, final String name) {
        final ItemMeta meta = base.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            base.setItemMeta(meta);
        }
        return base;
    }

    private void runAsync(final Runnable runnable) {
        if (runnable == null) {
            return;
        }
        final BukkitScheduler scheduler = Bukkit.getScheduler();
        if (plugin == null) {
            runnable.run();
            return;
        }
        scheduler.runTaskAsynchronously(plugin, runnable);
    }

    private void transferCoins(final Player sender, final long amount) {
        final UUID senderUuid = sender.getUniqueId();
        runAsync(() -> {
            final boolean success = economyManager.transfer(senderUuid, targetUuid, amount,
                    "Ami: " + targetName);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!success) {
                    sender.sendMessage("§cImpossible d'envoyer le cadeau (fonds insuffisants ?)");
                    return;
                }
                sender.closeInventory();
                sender.sendMessage("§aVous avez envoyé §6" + amount + " coins §aà §e" + targetName + "§a !");
                final Player targetPlayer = Bukkit.getPlayer(targetUuid);
                if (targetPlayer != null) {
                    targetPlayer.sendMessage("§6" + sender.getName() + " §avous a offert §6" + amount + " coins !");
                }
            });
        });
    }
}
