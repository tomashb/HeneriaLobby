package com.lobby.menus;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;

public interface Menu {

    void open(Player player);

    void handleClick(InventoryClickEvent event);

    Inventory getInventory();
}
