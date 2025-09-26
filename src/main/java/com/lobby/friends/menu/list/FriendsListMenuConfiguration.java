package com.lobby.friends.menu.list;

import com.lobby.friends.menu.FriendsMenuDecoration;
import org.bukkit.Sound;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable view over the configuration driving the friends list menu. The
 * structure mirrors the YAML schema described in {@code friends_list.yml} and
 * exposes strongly typed accessors for every configurable aspect of the menu
 * including pagination, navigation controls, friend templates and advanced
 * integrations.
 */
public final class FriendsListMenuConfiguration {

    private final Settings settings;
    private final List<FriendsMenuDecoration> decorations;
    private final List<Integer> friendSlots;
    private final Navigation navigation;
    private final Sorting sorting;
    private final Filters filters;
    private final FriendTemplate onlineTemplate;
    private final FriendTemplate offlineTemplate;
    private final FavoriteDecorations favoriteDecorations;
    private final Map<String, StatusDecoration> statusesByKey;
    private final Sounds sounds;
    private final Map<String, String> messages;
    private final List<String> placeholders;
    private final AdvancedSettings advancedSettings;
    private final Integrations integrations;

    public FriendsListMenuConfiguration(final Settings settings,
                                        final List<FriendsMenuDecoration> decorations,
                                        final List<Integer> friendSlots,
                                        final Navigation navigation,
                                        final Sorting sorting,
                                        final Filters filters,
                                        final FriendTemplate onlineTemplate,
                                        final FriendTemplate offlineTemplate,
                                        final FavoriteDecorations favoriteDecorations,
                                        final Map<String, StatusDecoration> statusesByKey,
                                        final Sounds sounds,
                                        final Map<String, String> messages,
                                        final List<String> placeholders,
                                        final AdvancedSettings advancedSettings,
                                        final Integrations integrations) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.decorations = decorations == null ? List.of() : List.copyOf(decorations);
        this.friendSlots = friendSlots == null ? List.of() : List.copyOf(friendSlots);
        this.navigation = Objects.requireNonNullElse(navigation, Navigation.empty());
        this.sorting = Objects.requireNonNullElse(sorting, Sorting.empty());
        this.filters = Objects.requireNonNullElse(filters, Filters.empty());
        this.onlineTemplate = Objects.requireNonNullElse(onlineTemplate, FriendTemplate.empty());
        this.offlineTemplate = Objects.requireNonNullElse(offlineTemplate, FriendTemplate.empty());
        this.favoriteDecorations = Objects.requireNonNullElse(favoriteDecorations, FavoriteDecorations.empty());
        this.statusesByKey = statusesByKey == null ? Map.of() : Map.copyOf(statusesByKey);
        this.sounds = Objects.requireNonNullElse(sounds, Sounds.empty());
        this.messages = messages == null ? Map.of() : Map.copyOf(messages);
        this.placeholders = placeholders == null ? List.of() : List.copyOf(placeholders);
        this.advancedSettings = Objects.requireNonNullElse(advancedSettings, AdvancedSettings.empty());
        this.integrations = Objects.requireNonNullElse(integrations, Integrations.empty());
    }

    public Settings getSettings() {
        return settings;
    }

    public List<FriendsMenuDecoration> getDecorations() {
        return Collections.unmodifiableList(decorations);
    }

    public List<Integer> getFriendSlots() {
        return Collections.unmodifiableList(friendSlots);
    }

    public Navigation getNavigation() {
        return navigation;
    }

    public Sorting getSorting() {
        return sorting;
    }

    public Filters getFilters() {
        return filters;
    }

    public FriendTemplate getOnlineTemplate() {
        return onlineTemplate;
    }

    public FriendTemplate getOfflineTemplate() {
        return offlineTemplate;
    }

    public FavoriteDecorations getFavoriteDecorations() {
        return favoriteDecorations;
    }

    public Map<String, StatusDecoration> getStatusesByKey() {
        return Collections.unmodifiableMap(statusesByKey);
    }

    public Sounds getSounds() {
        return sounds;
    }

    public Map<String, String> getMessages() {
        return Collections.unmodifiableMap(messages);
    }

    public List<String> getPlaceholders() {
        return Collections.unmodifiableList(placeholders);
    }

    public AdvancedSettings getAdvancedSettings() {
        return advancedSettings;
    }

    public Integrations getIntegrations() {
        return integrations;
    }

    /**
     * General settings for the menu layout.
     */
    public record Settings(String title,
                           int size,
                           int itemsPerPage,
                           boolean autoRefresh,
                           int refreshIntervalSeconds) {

        public Settings {
            title = (title == null || title.isBlank()) ? "§8» §aListe des Amis" : title;
            size = Math.max(9, Math.min(54, size));
            itemsPerPage = Math.max(1, Math.min(size, itemsPerPage));
            refreshIntervalSeconds = Math.max(1, refreshIntervalSeconds);
        }
    }

    /**
     * Representation of navigation controls. Each button is optional and may be
     * absent depending on the configuration file.
     */
    public record Navigation(Button previousPage,
                             Button backToMain,
                             Button nextPage) {

        public static Navigation empty() {
            return new Navigation(null, null, null);
        }
    }

    /**
     * Sorting configuration containing the current strategy and available
     * options.
     */
    public record Sorting(String currentSort,
                          Button sortButton,
                          Map<String, SortingOption> options) {

        public Sorting {
            currentSort = currentSort == null ? "online_first" : currentSort;
            options = options == null ? Map.of() : Map.copyOf(options);
        }

        public static Sorting empty() {
            return new Sorting("online_first", null, Map.of());
        }
    }

    /**
     * Single sorting option exposed to the player.
     */
    public record SortingOption(String key, String name, String description) {

        public SortingOption {
            key = key == null ? "" : key;
            name = name == null ? "" : name;
            description = description == null ? "" : description;
        }
    }

    /**
     * Filter configuration, containing default toggle values and the button used
     * to open the filter sub-menu.
     */
    public record Filters(boolean showOnlineOnly,
                          boolean showFavoritesOnly,
                          boolean hideAway,
                          boolean hideBusy,
                          Button filterButton) {

        public static Filters empty() {
            return new Filters(false, false, false, false, null);
        }
    }

    /**
     * Template describing how a friend entry should be rendered.
     */
    public record FriendTemplate(String itemKey,
                                 String displayName,
                                 List<String> lore,
                                 Map<String, String> actions) {

        public FriendTemplate {
            itemKey = itemKey == null ? "PLAYER_HEAD" : itemKey;
            displayName = displayName == null ? "" : displayName;
            lore = lore == null ? List.of() : List.copyOf(lore);
            actions = actions == null ? Map.of() : Map.copyOf(actions);
        }

        public static FriendTemplate empty() {
            return new FriendTemplate("PLAYER_HEAD", "", List.of(), Map.of());
        }
    }

    /**
     * Optional decorations applied to favourite friends.
     */
    public record FavoriteDecorations(boolean enchanted,
                                      String particles,
                                      String namePrefix,
                                      List<String> loreAddition) {

        public FavoriteDecorations {
            particles = particles == null ? "" : particles;
            namePrefix = namePrefix == null ? "" : namePrefix;
            loreAddition = loreAddition == null ? List.of() : List.copyOf(loreAddition);
        }

        public static FavoriteDecorations empty() {
            return new FavoriteDecorations(false, "", "", List.of());
        }
    }

    /**
     * Additional text decorations applied depending on friend status (away,
     * busy, etc.).
     */
    public record StatusDecoration(String nameSuffix, String statusColor) {

        public StatusDecoration {
            nameSuffix = nameSuffix == null ? "" : nameSuffix;
            statusColor = statusColor == null ? "" : statusColor;
        }
    }

    /**
     * Sounds used by the friends list menu.
     */
    public record Sounds(Sound openList,
                         Sound pageTurn,
                         Sound friendClick,
                         Sound teleportSuccess,
                         Sound teleportFailed,
                         Sound messageSent) {

        public static Sounds empty() {
            return new Sounds(null, null, null, null, null, null);
        }
    }

    /**
     * Various technical settings that influence the behaviour of the menu.
     */
    public record AdvancedSettings(int maxFriendsPerPage,
                                   int headCacheDurationSeconds,
                                   int statusUpdateIntervalSeconds,
                                   boolean offlineHeadGrayscale,
                                   boolean favoriteGlowEffect,
                                   boolean animateStatusChanges) {

        public AdvancedSettings {
            maxFriendsPerPage = Math.max(1, maxFriendsPerPage);
            headCacheDurationSeconds = Math.max(0, headCacheDurationSeconds);
            statusUpdateIntervalSeconds = Math.max(1, statusUpdateIntervalSeconds);
        }

        public static AdvancedSettings empty() {
            return new AdvancedSettings(28, 300, 5, true, true, true);
        }
    }

    /**
     * External integrations toggles.
     */
    public record Integrations(boolean placeholderApi,
                               boolean headDatabase,
                               boolean bungeeMessaging) {

        public static Integrations empty() {
            return new Integrations(true, true, true);
        }
    }

    /**
     * Generic clickable button representation.
     */
    public record Button(int slot,
                         String itemKey,
                         String displayName,
                         List<String> lore,
                         Map<String, String> actions,
                         String visibleWhen) {

        public Button {
            slot = Math.max(0, slot);
            itemKey = itemKey == null ? "BARRIER" : itemKey;
            displayName = displayName == null ? "" : displayName;
            lore = lore == null ? List.of() : List.copyOf(lore);
            actions = actions == null ? Map.of() : Map.copyOf(actions);
            visibleWhen = visibleWhen == null ? "" : visibleWhen;
        }
    }
}

