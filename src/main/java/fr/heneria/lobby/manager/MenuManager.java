package fr.heneria.lobby.manager;

import fr.heneria.lobby.HeneriaLobby;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class MenuManager extends Manager {

    public MenuManager(HeneriaLobby plugin) {
        super(plugin);
    }

    @Override
    public void onEnable() {
        // Nothing specifically to enable
    }

    @Override
    public void onDisable() {
        // Nothing specifically to disable
    }

    public void openMenu(Player player, String menuId) {
        ConfigurationSection menuSection = plugin.getConfigManager().getMenusConfig().getConfigurationSection("menus." + menuId);
        if (menuSection == null) {
            plugin.getLogger().warning("Menu not found: " + menuId);
            return;
        }

        String titleRaw = menuSection.getString("title", "Menu");
        Component title = plugin.getConfigManager().parseComponent(titleRaw, player);
        int rows = menuSection.getInt("rows", 3);
        if (rows < 1 || rows > 6) rows = 3;

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        // Filler
        if (menuSection.getBoolean("filler.enabled")) {
            String matName = menuSection.getString("filler.material", "BLACK_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matName);
            if (mat != null) {
                ItemStack filler = new ItemStack(mat);
                ItemMeta meta = filler.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.empty());
                    filler.setItemMeta(meta);
                }
                for (int i = 0; i < inventory.getSize(); i++) {
                    inventory.setItem(i, filler);
                }
            }
        }

        // Items
        ConfigurationSection itemsSection = menuSection.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                ItemStack item = plugin.getConfigManager().getMenuItem(menuId, key, player);
                int slot = itemsSection.getInt(key + ".slot");
                if (item != null && slot >= 0 && slot < inventory.getSize()) {
                    inventory.setItem(slot, item);
                }
            }
        }

        player.openInventory(inventory);
    }
}
