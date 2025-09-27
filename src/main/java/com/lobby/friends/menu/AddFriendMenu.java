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
 * Temporary menu used while the real add-friend workflow is under
 * construction. Provides informative placeholders and a back button that
 * returns to the friends main menu.
 */
public class AddFriendMenu implements Listener {

    private static final String TITLE = "§8» §aAjouter un Ami";
    private static final int SIZE = 36;
    private static final int BACK_BUTTON_SLOT = 31;
    private static final int SEARCH_BUTTON_SLOT = 10;

    private final LobbyPlugin plugin;
    private final Map<Integer, ItemStack> layout = new HashMap<>();

    public AddFriendMenu(final LobbyPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        setupLayout();
    }

    private void setupLayout() {
        final ItemStack greenGlass = createItem(Material.GREEN_STAINED_GLASS_PANE, " ");
        final int[] greenSlots = {0, 1, 2, 6, 7, 8, 9, 17, 18, 26, 27, 28, 29, 33, 34, 35};
        for (int slot : greenSlots) {
            layout.put(slot, greenGlass.clone());
        }

        final ItemStack searchButton = createItem(Material.COMPASS, "§a§l🔍 Rechercher un Joueur", List.of(
                "§7Recherchez un joueur par son nom",
                "§7pour lui envoyer une demande d'ami",
                "",
                "§e⚠ Fonctionnalité en développement",
                "§7Utilisez §b/msg <joueur>§7 en attendant",
                "",
                "§8» §aCliquez pour plus d'infos"
        ));
        layout.put(SEARCH_BUTTON_SLOT, searchButton);

        final ItemStack suggestionsButton = createItem(Material.BOOK, "§e§l📝 Suggestions (Bientôt)");
        layout.put(11, suggestionsButton);

        final ItemStack nearbyButton = createItem(Material.COMPASS, "§b§l📍 Joueurs Proches (Bientôt)");
        layout.put(12, nearbyButton);

        final ItemStack backButton = createItem(Material.BARRIER, "§e🏠 Retour Menu Principal", List.of(
                "§7Revenir au menu principal",
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
            return;
        }
        if (slot == SEARCH_BUTTON_SLOT) {
            player.closeInventory();
            player.sendMessage("§e🔍 Fonction de recherche en développement !");
            player.sendMessage("§7Utilisez §b/msg <nom_joueur> §7pour contacter quelqu'un en attendant");
        }
    }
}
