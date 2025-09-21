package com.lobby.settings;

public enum GroupRequestSetting {
    EVERYONE("Tous", "&aOuvert à tous"),
    FRIENDS_ONLY("Amis seulement", "&eAmis seulement"),
    DISABLED("Désactivé", "&cDésactivé");

    private final String displayName;
    private final String coloredDisplay;

    GroupRequestSetting(final String displayName, final String coloredDisplay) {
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

