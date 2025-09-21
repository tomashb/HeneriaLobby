package com.lobby.menus;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Objects;

public final class MenuDesign {

    private final ItemStack decorativeItem;
    private final ItemStack borderItem;
    private final ItemStack confirmItem;
    private final ItemStack cancelItem;
    private final List<Integer> decorationSlots;
    private final List<Integer> borderSlots;

    public MenuDesign(final Material decorativeMaterial,
                      final Material borderMaterial,
                      final Material confirmMaterial,
                      final Material cancelMaterial,
                      final List<Integer> decorationSlots,
                      final List<Integer> borderSlots) {
        this.decorativeItem = createItem(decorativeMaterial);
        this.borderItem = createItem(borderMaterial);
        this.confirmItem = createItem(confirmMaterial);
        this.cancelItem = createItem(cancelMaterial);
        this.decorationSlots = List.copyOf(Objects.requireNonNullElse(decorationSlots, List.of()));
        this.borderSlots = List.copyOf(Objects.requireNonNullElse(borderSlots, List.of()));
    }

    private ItemStack createItem(final Material material) {
        final Material resolved = material != null ? material : Material.BLACK_STAINED_GLASS_PANE;
        final ItemStack itemStack = new ItemStack(resolved);
        final ItemMeta meta = itemStack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.values());
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }

    public ItemStack createDecorativeItem() {
        return decorativeItem.clone();
    }

    public ItemStack createBorderItem() {
        return borderItem.clone();
    }

    public ItemStack createConfirmItem() {
        return confirmItem.clone();
    }

    public ItemStack createCancelItem() {
        return cancelItem.clone();
    }

    public List<Integer> decorationSlots() {
        return decorationSlots;
    }

    public List<Integer> borderSlots() {
        return borderSlots;
    }
}

