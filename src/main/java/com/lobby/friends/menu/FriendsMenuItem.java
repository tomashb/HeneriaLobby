package com.lobby.friends.menu;

import java.util.Collections;
import java.util.List;

/**
 * Configuration of a clickable friends menu item.
 */
public final class FriendsMenuItem {

    private final int slot;
    private final String materialKey;
    private final String displayName;
    private final List<String> lore;
    private final String action;
    private final boolean enchanted;

    public FriendsMenuItem(final int slot,
                           final String materialKey,
                           final String displayName,
                           final List<String> lore,
                           final String action,
                           final boolean enchanted) {
        this.slot = slot;
        this.materialKey = materialKey;
        this.displayName = displayName;
        this.lore = lore == null ? List.of() : List.copyOf(lore);
        this.action = action;
        this.enchanted = enchanted;
    }

    public int getSlot() {
        return slot;
    }

    public String getMaterialKey() {
        return materialKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<String> getLore() {
        return Collections.unmodifiableList(lore);
    }

    public String getAction() {
        return action;
    }

    public boolean isEnchanted() {
        return enchanted;
    }
}

