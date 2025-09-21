package com.lobby.social.groups;

public class GroupSettings {

    private final boolean autoAcceptInvites;
    private final GroupVisibility visibility;

    public GroupSettings(final boolean autoAcceptInvites, final GroupVisibility visibility) {
        this.autoAcceptInvites = autoAcceptInvites;
        this.visibility = visibility == null ? GroupVisibility.PUBLIC : visibility;
    }

    public boolean isAutoAcceptInvites() {
        return autoAcceptInvites;
    }

    public GroupVisibility getVisibility() {
        return visibility;
    }
}
