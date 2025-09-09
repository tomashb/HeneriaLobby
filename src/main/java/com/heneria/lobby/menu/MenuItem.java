package com.heneria.lobby.menu;

import org.bukkit.inventory.ItemStack;

/**
 * Represents a clickable item inside a menu.
 */
public class MenuItem {

    private final ItemStack itemStack;
    private final String action;

    public MenuItem(ItemStack itemStack, String action) {
        this.itemStack = itemStack;
        this.action = action;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public String getAction() {
        return action;
    }
}

