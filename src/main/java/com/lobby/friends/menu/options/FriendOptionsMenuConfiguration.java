package com.lobby.friends.menu.options;

import com.lobby.friends.menu.FriendsMenuDecoration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable representation of the friend options menu (opened via right click
 * on a friend entry). The configuration contains layout decorations, individual
 * actionable items and confirmation prompts.
 */
public final class FriendOptionsMenuConfiguration {

    private final String title;
    private final int size;
    private final List<FriendsMenuDecoration> decorations;
    private final List<MenuItem> items;
    private final Map<String, ConfirmationPrompt> confirmations;

    public FriendOptionsMenuConfiguration(final String title,
                                          final int size,
                                          final List<FriendsMenuDecoration> decorations,
                                          final List<MenuItem> items,
                                          final Map<String, ConfirmationPrompt> confirmations) {
        this.title = title == null || title.isBlank() ? "§8» §7Options d'Ami" : title;
        this.size = Math.max(9, Math.min(54, size));
        this.decorations = decorations == null ? List.of() : List.copyOf(decorations);
        this.items = items == null ? List.of() : List.copyOf(items);
        this.confirmations = confirmations == null ? Map.of() : Map.copyOf(confirmations);
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public List<FriendsMenuDecoration> getDecorations() {
        return Collections.unmodifiableList(decorations);
    }

    public List<MenuItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Map<String, ConfirmationPrompt> getConfirmations() {
        return Collections.unmodifiableMap(confirmations);
    }

    /**
     * Describes an item displayed in the friend options menu.
     */
    public record MenuItem(int slot,
                           String itemKey,
                           String displayName,
                           List<String> lore,
                           Map<String, String> actions) {

        public MenuItem {
            slot = Math.max(0, slot);
            itemKey = itemKey == null ? "BARRIER" : itemKey;
            displayName = displayName == null ? "" : displayName;
            lore = lore == null ? List.of() : List.copyOf(lore);
            actions = actions == null ? Map.of() : Map.copyOf(actions);
        }
    }

    /**
     * Confirmation prompt triggered before executing sensitive actions such as
     * removing or blocking a friend.
     */
    public record ConfirmationPrompt(String title,
                                     String message,
                                     String confirmButton,
                                     String cancelButton) {

        public ConfirmationPrompt {
            title = Objects.requireNonNullElse(title, "");
            message = Objects.requireNonNullElse(message, "");
            confirmButton = Objects.requireNonNullElse(confirmButton, "");
            cancelButton = Objects.requireNonNullElse(cancelButton, "");
        }
    }
}

