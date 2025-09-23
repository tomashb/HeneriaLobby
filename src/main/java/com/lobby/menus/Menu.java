package com.lobby.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

import java.util.Collections;
import java.util.List;

public interface Menu {

    void open(Player player);

    void handleClick(InventoryClickEvent event);

    Inventory getInventory();

    default List<String> getActionsForSlot(final int slot) {
        return Collections.emptyList();
    }
}
