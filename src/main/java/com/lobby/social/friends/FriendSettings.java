package com.lobby.social.friends;

public class FriendSettings {

    private final AcceptMode acceptRequests;
    private final boolean showOnlineStatus;
    private final boolean receiveNotifications;

    public FriendSettings(final AcceptMode acceptRequests, final boolean showOnlineStatus, final boolean receiveNotifications) {
        this.acceptRequests = acceptRequests;
        this.showOnlineStatus = showOnlineStatus;
        this.receiveNotifications = receiveNotifications;
    }

    public AcceptMode getAcceptRequests() {
        return acceptRequests;
    }

    public boolean isShowOnlineStatus() {
        return showOnlineStatus;
    }

    public boolean isReceiveNotifications() {
        return receiveNotifications;
    }
}
