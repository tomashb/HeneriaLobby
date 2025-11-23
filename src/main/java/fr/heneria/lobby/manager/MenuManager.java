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
        int rows = menuSection.getInt("rows", 6);
        if (rows < 1 || rows > 6) rows = 6;

        Inventory inventory = Bukkit.createInventory(null, rows * 9, title);

        // Frame Style
        boolean enableFrame = menuSection.getBoolean("enable_frame", false);
        if (enableFrame) {
            String frameMat = menuSection.getString("frame_material", "ORANGE_STAINED_GLASS_PANE");
            applyFrame(inventory, rows, frameMat);
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

    private void applyFrame(Inventory inventory, int rows, String materialName) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) mat = Material.ORANGE_STAINED_GLASS_PANE;

        ItemStack pane = new ItemStack(mat);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
        }

        // The prompt asks for:
        // Top: 0, 1, 7, 8, 9, 17
        // Bottom: 36, 44, 45, 46, 52, 53 (assuming 6 rows = 54 slots)

        // Let's verify rows. If rows < 6, this mapping might be weird.
        // But the prompt says "Tous les menus font 54 slots (6 lignes)".
        // So we target 6 rows specifically.

        int[] topSlots = {0, 1, 7, 8, 9, 17};
        for (int slot : topSlots) {
            if (slot < inventory.getSize()) inventory.setItem(slot, pane);
        }

        // For bottom, we assume 6 rows (size 54).
        // 36, 44 are row 5 (indices 36-44). 36 is first, 44 is last.
        // 45, 46, 52, 53 are row 6 (indices 45-53). 45,46 are first two, 52,53 are last two.

        // If rows is strictly 6:
        if (rows == 6) {
             int[] bottomSlots = {36, 44, 45, 46, 52, 53};
             for (int slot : bottomSlots) {
                 inventory.setItem(slot, pane);
             }
        } else {
            // Fallback logic if rows != 6 but frame enabled?
            // Just do corners? Or try to adapt.
            // Prompt says "Tous les menus font 54 slots".
            // I will implement adaptive logic just in case, or stick to the requested pattern for 6 rows.

            int lastRowStart = (rows - 1) * 9;
            int secondLastRowStart = (rows - 2) * 9;

            // Row N (Bottom): 0, 1, 7, 8 relative to row start
            inventory.setItem(lastRowStart + 0, pane);
            inventory.setItem(lastRowStart + 1, pane);
            inventory.setItem(lastRowStart + 7, pane);
            inventory.setItem(lastRowStart + 8, pane);

            // Row N-1: 0, 8 relative to row start
            inventory.setItem(secondLastRowStart + 0, pane);
            inventory.setItem(secondLastRowStart + 8, pane);
        }
    }
}
