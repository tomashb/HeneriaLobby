package com.lobby.social.friends;

public class FriendSettings {

    private final AcceptMode acceptRequests;
    private final boolean showOnlineStatus;
    private final boolean allowNotifications;
    private final boolean autoAcceptFavorites;
    private final boolean allowPrivateMessages;
    private final int maxFriends;

    public FriendSettings(final AcceptMode acceptRequests,
                          final boolean showOnlineStatus,
                          final boolean allowNotifications,
                          final boolean autoAcceptFavorites,
                          final int maxFriends) {
        this(acceptRequests, showOnlineStatus, allowNotifications, autoAcceptFavorites, true, maxFriends);
    }

    public FriendSettings(final AcceptMode acceptRequests,
                          final boolean showOnlineStatus,
                          final boolean allowNotifications,
                          final boolean autoAcceptFavorites,
                          final boolean allowPrivateMessages,
                          final int maxFriends) {
        this.acceptRequests = acceptRequests;
        this.showOnlineStatus = showOnlineStatus;
        this.allowNotifications = allowNotifications;
        this.autoAcceptFavorites = autoAcceptFavorites;
        this.allowPrivateMessages = allowPrivateMessages;
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

    public boolean isAllowPrivateMessages() {
        return allowPrivateMessages;
    }

    public int getMaxFriends() {
        return maxFriends;
    }
}
