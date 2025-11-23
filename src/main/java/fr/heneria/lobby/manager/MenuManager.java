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

        // Frame Style: CORNERS
        String frameStyle = menuSection.getString("frame_style", "NONE");
        if ("CORNERS".equalsIgnoreCase(frameStyle)) {
            applyCorners(inventory, rows);
        }

        // Explicit Filler (Legacy or specific override)
        if (menuSection.getBoolean("filler.enabled")) {
             // If frame style is corners, maybe we don't want full filler?
             // But if explicitly enabled, we do it.
             // The prompt says: "Si activé (CORNERS)... Important : Laisse les autres slots vides (AIR)".
             // So if CORNERS is active, we assume we only do corners unless filler is ALSO explicitly requested to fill EVERYTHING?
             // Let's check standard practice. Usually filler fills empty slots.
             // I'll respect the 'filler' config if present, but CORNERS is applied on top or before.
             fillInventory(inventory, menuSection.getString("filler.material", "BLACK_STAINED_GLASS_PANE"));
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

    private void applyCorners(Inventory inventory, int rows) {
        ItemStack orangePane = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        ItemMeta meta = orangePane.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            orangePane.setItemMeta(meta);
        }

        int size = rows * 9;

        // Top corners
        inventory.setItem(0, orangePane);
        inventory.setItem(1, orangePane);
        inventory.setItem(7, orangePane);
        inventory.setItem(8, orangePane);

        // Bottom corners (if rows > 1)
        if (rows > 1) {
            inventory.setItem(size - 9, orangePane); // Bottom Left
            inventory.setItem(size - 8, orangePane); // Bottom Left + 1
            inventory.setItem(size - 2, orangePane); // Bottom Right - 1
            inventory.setItem(size - 1, orangePane); // Bottom Right
        }

        // Side borders? The prompt specifically says: "Slots 0,1,7,8 et 45,46,52,53 pour un menu 6 lignes".
        // This matches Top 4 corners and Bottom 4 corners logic.
        // It does not mention sides.
    }

    private void fillInventory(Inventory inventory, String materialName) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null) return;

        ItemStack filler = new ItemStack(mat);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null || inventory.getItem(i).getType() == Material.AIR) {
                inventory.setItem(i, filler);
            }
        }
    }
}
