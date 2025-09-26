package com.lobby.friends.menu;

import org.bukkit.Sound;

import java.util.Collections;
import java.util.List;

public final class FriendsMenuConfiguration {

    private final String title;
    private final int size;
    private final int updateIntervalSeconds;
    private final Sound openSound;
    private final Sound clickSound;
    private final Sound errorSound;
    private final List<FriendsMenuDecoration> decorations;
    private final List<FriendsMenuItem> items;

    public FriendsMenuConfiguration(final String title,
                                    final int size,
                                    final int updateIntervalSeconds,
                                    final Sound openSound,
                                    final Sound clickSound,
                                    final Sound errorSound,
                                    final List<FriendsMenuDecoration> decorations,
                                    final List<FriendsMenuItem> items) {
        this.title = title == null || title.isBlank() ? "§8» §aMenu des Amis" : title;
        this.size = Math.max(9, Math.min(54, size));
        this.updateIntervalSeconds = Math.max(1, updateIntervalSeconds);
        this.openSound = openSound;
        this.clickSound = clickSound;
        this.errorSound = errorSound;
        this.decorations = decorations == null ? List.of() : List.copyOf(decorations);
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public int getUpdateIntervalSeconds() {
        return updateIntervalSeconds;
    }

    public int getUpdateIntervalTicks() {
        return updateIntervalSeconds * 20;
    }

    public Sound getOpenSound() {
        return openSound;
    }

    public Sound getClickSound() {
        return clickSound;
    }

    public Sound getErrorSound() {
        return errorSound;
    }

    public List<FriendsMenuDecoration> getDecorations() {
        return Collections.unmodifiableList(decorations);
    }

    public List<FriendsMenuItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}

