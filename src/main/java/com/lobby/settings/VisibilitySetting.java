package com.lobby.settings;

public enum VisibilitySetting {
    EVERYONE("Tous", "&aTous les joueurs"),
    FRIENDS_ONLY("Amis seulement", "&eAmis seulement"),
    NOBODY("Personne", "&cPersonne");

    private final String displayName;
    private final String coloredDisplay;

    VisibilitySetting(final String displayName, final String coloredDisplay) {
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

