package com.lobby.friends.menu;

import com.lobby.LobbyPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Temporary friends list menu displayed while the full implementation is
 * under development. Provides visual feedback and a way to return to the main
 * friends menu.
 */
public class FriendsListMenu implements Listener {

    private static final String TITLE = "§8» §aListe des Amis (1/1)";
    private static final int SIZE = 54;
    private static final int BACK_BUTTON_SLOT = 49;

    private final LobbyPlugin plugin;
    private final Map<Integer, ItemStack> layout = new HashMap<>();

    public FriendsListMenu(final LobbyPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupLayout();
    }

    private void setupLayout() {
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 45, 46, 52, 53};
        for (int slot : greenSlots) {
            layout.put(slot, greenGlass.clone());
        }

        final ItemStack noFriends = createItem(Material.PAPER, "§7§lAucun ami pour le moment", List.of(
                "§7Vous n'avez pas encore d'amis",
                "§7ajoutés à votre liste",
                "",
                "§e💡 Utilisez le menu d'ajout",
                "§epour trouver des amis !"
        ));
        layout.put(22, noFriends);

        final ItemStack backButton = createItem(Material.PLAYER_HEAD, "§e🏠 Retour Menu Principal", List.of(
                "§7Revenir au menu principal",
                "§7des amis",
                "",
                "§8» §eCliquez pour retourner"
        ));
        layout.put(BACK_BUTTON_SLOT, backButton);
    }

    private ItemStack createItem(final Material material, final String name) {
        return createItem(material, name, List.of());
    }

    private ItemStack createItem(final Material material, final String name, final List<String> lore) {
        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null && !lore.isEmpty()) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Opens the menu for the specified player.
     *
     * @param player the viewer
     */
    public void open(final Player player) {
        if (player == null) {
            return;
        }
        final Inventory menu = Bukkit.createInventory(null, SIZE, TITLE);
        for (Map.Entry<Integer, ItemStack> entry : layout.entrySet()) {
            menu.setItem(entry.getKey(), entry.getValue().clone());
        }
        player.openInventory(menu);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);
    }

    @EventHandler
    public void onInventoryClick(final InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!TITLE.equals(event.getView().getTitle())) {
            return;
        }
        event.setCancelled(true);
        final int slot = event.getSlot();
        if (slot == BACK_BUTTON_SLOT) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                final FriendsMenuController controller = plugin.getFriendsMenuController();
                if (controller != null) {
                    controller.openMainMenu(player);
                }
            }, 1L);
        }
    }
}
