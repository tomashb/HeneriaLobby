package com.lobby.social.friends;

public class FriendSettings {

    private final AcceptMode acceptRequests;
    private final boolean showOnlineStatus;
    private final boolean allowNotifications;
    private final boolean autoAcceptFavorites;
    private final int maxFriends;

    public FriendSettings(final AcceptMode acceptRequests,
                          final boolean showOnlineStatus,
                          final boolean allowNotifications,
                          final boolean autoAcceptFavorites,
                          final int maxFriends) {
        this.acceptRequests = acceptRequests;
        this.showOnlineStatus = showOnlineStatus;
        this.allowNotifications = allowNotifications;
        this.autoAcceptFavorites = autoAcceptFavorites;
        this.maxFriends = maxFriends;
    }

    public AcceptMode getAcceptRequests() {
        return acceptRequests;
    }

    public boolean isShowOnlineStatus() {
        return showOnlineStatus;
    }

    public boolean isAllowNotifications() {
        return allowNotifications;
    }

    public boolean isAutoAcceptFavorites() {
        return autoAcceptFavorites;
    }

    public int getMaxFriends() {
        return maxFriends;
    }
}
