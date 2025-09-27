package com.lobby.friends.menu.statistics;

import com.lobby.friends.menu.FriendsMenuDecoration;
import org.bukkit.Sound;

import java.util.List;
import java.util.Map;

/**
 * Represents the strongly typed configuration for the detailed friend
 * statistics menu. The configuration is loaded from the
 * {@code friends/statistics.yml} file at runtime and cached inside the menu
 * instance.
 */
public final class FriendStatisticsMenuConfiguration {

    private final String title;
    private final int size;
    private final boolean autoRefresh;
    private final int refreshIntervalSeconds;
    private final List<FriendsMenuDecoration> decorations;
    private final List<MenuItem> items;
    private final Map<String, String> messages;
    private final Map<String, Sound> sounds;

    public FriendStatisticsMenuConfiguration(final String title,
                                             final int size,
                                             final boolean autoRefresh,
                                             final int refreshIntervalSeconds,
                                             final List<FriendsMenuDecoration> decorations,
                                             final List<MenuItem> items,
                                             final Map<String, String> messages,
                                             final Map<String, Sound> sounds) {
        this.title = title;
        this.size = size;
        this.autoRefresh = autoRefresh;
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.decorations = List.copyOf(decorations);
        this.items = List.copyOf(items);
        this.messages = Map.copyOf(messages);
        this.sounds = Map.copyOf(sounds);
    }

    public String getTitle() {
        return title;
    }

    public int getSize() {
        return size;
    }

    public boolean isAutoRefresh() {
        return autoRefresh;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public List<FriendsMenuDecoration> getDecorations() {
        return decorations;
    }

    public List<MenuItem> getItems() {
        return items;
    }

    public Map<String, String> getMessages() {
        return messages;
    }

    public Map<String, Sound> getSounds() {
        return sounds;
    }

    /**
     * Represents a single clickable item inside the statistics menu.
     */
    public record MenuItem(int slot,
                           String itemKey,
                           String name,
                           List<String> lore,
                           Map<String, String> actions) {

        public MenuItem {
            lore = List.copyOf(lore);
            actions = Map.copyOf(actions);
        }
    }
}
