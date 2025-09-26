package com.lobby.friends.menu;

import org.bukkit.Material;

import java.util.Collections;
import java.util.List;

/**
 * Represents decorative items (typically glass panes) used to build the border
 * of the friends menu.
 */
public record FriendsMenuDecoration(Material material, String displayName, List<Integer> slots) {

    public FriendsMenuDecoration {
        if (material == null) {
            material = Material.BLACK_STAINED_GLASS_PANE;
        }
        if (displayName == null) {
            displayName = " ";
        }
        if (slots == null) {
            slots = List.of();
        }
    }

    public List<Integer> slots() {
        return Collections.unmodifiableList(slots);
    }
}

