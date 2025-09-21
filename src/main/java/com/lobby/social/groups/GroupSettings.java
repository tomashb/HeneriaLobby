package com.lobby.social.groups;

public class GroupSettings {

    private static final String DEFAULT_GAMEMODE = "ANY";
    private static final int DEFAULT_MAX_INVITATIONS = 5;

    private final boolean autoAcceptInvites;
    private final String preferredGamemode;
    private final GroupVisibility visibility;
    private final int maxInvitations;
    private final boolean allowNotifications;

    public GroupSettings() {
        this(false, DEFAULT_GAMEMODE, GroupVisibility.PUBLIC, DEFAULT_MAX_INVITATIONS, true);
    }

    public GroupSettings(final boolean autoAcceptInvites, final GroupVisibility visibility) {
        this(autoAcceptInvites, DEFAULT_GAMEMODE, visibility, DEFAULT_MAX_INVITATIONS, true);
    }

    public GroupSettings(final boolean autoAcceptInvites,
                         final String preferredGamemode,
                         final GroupVisibility visibility,
                         final int maxInvitations,
                         final boolean allowNotifications) {
        this.autoAcceptInvites = autoAcceptInvites;
        this.preferredGamemode = (preferredGamemode == null || preferredGamemode.isBlank())
                ? DEFAULT_GAMEMODE
                : preferredGamemode;
        this.visibility = visibility == null ? GroupVisibility.PUBLIC : visibility;
        this.maxInvitations = Math.max(0, maxInvitations);
        this.allowNotifications = allowNotifications;
    }

    public boolean isAutoAcceptInvites() {
        return autoAcceptInvites;
    }

    public String getPreferredGamemode() {
        return preferredGamemode;
    }

    public GroupVisibility getVisibility() {
        return visibility;
    }

    public int getMaxInvitations() {
        return maxInvitations;
    }

    public boolean isAllowNotifications() {
        return allowNotifications;
    }
}
