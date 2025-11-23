package fr.heneria.lobby.listeners;

import fr.heneria.lobby.HeneriaLobby;
import fr.heneria.lobby.manager.ItemManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.arcaniax.hdb.api.DatabaseLoadEvent;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class PlayerJoinListener implements Listener {

    private final HeneriaLobby plugin;

    public PlayerJoinListener(HeneriaLobby plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        giveLobbyItems(event.getPlayer());
    }

    @EventHandler
    public void onDatabaseLoad(DatabaseLoadEvent event) {
        // Reload items for all online players when HDB is ready, in case they joined before it was ready
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            giveLobbyItems(player);
        }
    }

    private void giveLobbyItems(Player player) {
        ItemManager itemManager = plugin.getItemManager();
        if (itemManager == null) return;

        // Slot 0: Sélecteur de Serveur (ID: 45678 - Globe/Boussole)
        ItemStack serverSelector = itemManager.getItemFromHDB("45678");
        if (serverSelector == null) serverSelector = new ItemStack(Material.COMPASS);
        setDisplayName(serverSelector, Component.text("Sélecteur de Serveur", NamedTextColor.GOLD));
        player.getInventory().setItem(0, serverSelector);

        // Slot 1: Mon Profil (Player Head)
        ItemStack profile = itemManager.getPlayerHead(player);
        setDisplayName(profile, Component.text("Mon Profil", NamedTextColor.YELLOW));
        player.getInventory().setItem(1, profile);

        // Slot 4: Menu Principal (ID: 9385 - Etoile/Menu)
        ItemStack mainMenu = itemManager.getItemFromHDB("9385");
        if (mainMenu == null) mainMenu = new ItemStack(Material.NETHER_STAR);
        setDisplayName(mainMenu, Component.text("Menu Principal", NamedTextColor.AQUA));
        player.getInventory().setItem(4, mainMenu);

        // Slot 7: Cosmétiques (ID: 2545 - Coffre)
        ItemStack cosmetics = itemManager.getItemFromHDB("2545");
        if (cosmetics == null) cosmetics = new ItemStack(Material.CHEST);
        setDisplayName(cosmetics, Component.text("Cosmétiques", NamedTextColor.LIGHT_PURPLE));
        player.getInventory().setItem(7, cosmetics);

        // Slot 8: Visibilité Joueurs (Switch) (ON default? Assuming ON)
        // ON: 7890 (Oeil Vert)
        ItemStack visibility = itemManager.getItemFromHDB("7890");
        if (visibility == null) visibility = new ItemStack(Material.LIME_DYE);
        setDisplayName(visibility, Component.text("Visibilité: ON", NamedTextColor.GREEN));
        player.getInventory().setItem(8, visibility);
    }

    private void setDisplayName(ItemStack item, Component name) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
    }
}
