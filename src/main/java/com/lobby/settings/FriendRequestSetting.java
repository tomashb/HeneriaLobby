package com.lobby.settings;

public enum FriendRequestSetting {
    EVERYONE("Tous", "&aOuvert à tous"),
    FRIENDS_OF_FRIENDS("Amis d'amis", "&eAmis d'amis uniquement"),
    DISABLED("Désactivé", "&cDésactivé");

    private final String displayName;
    private final String coloredDisplay;

    FriendRequestSetting(final String displayName, final String coloredDisplay) {
        this.displayName = displayName;
        this.coloredDisplay = coloredDisplay;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColoredDisplay() {
        return coloredDisplay;
    }
}

