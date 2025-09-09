package com.heneria.lobby.menu;

import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Represents a menu configuration with a title, size and items.
 */
public class Menu {

    private final String name;
    private final String title;
    private final int size;
    private final Map<Integer, MenuItem> items;

    public Menu(String name, String title, int size, Map<Integer, MenuItem> items) {
        this.name = name;
        this.title = title;
        this.size = size;
        this.items = items;
    }

    public String getName() {
        return name;
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public Map<Integer, MenuItem> getItems() {
        return items;
    }

    public ItemStack getItem(int slot) {
        return items.containsKey(slot) ? items.get(slot).getItemStack() : null;
    }
}

