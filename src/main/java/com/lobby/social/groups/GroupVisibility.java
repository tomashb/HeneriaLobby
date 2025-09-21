package com.lobby.social.groups;

public enum GroupVisibility {
    PUBLIC,
    FRIENDS_ONLY,
    INVITE_ONLY;

    public static GroupVisibility fromDatabase(final String value) {
        if (value == null || value.isBlank()) {
            return PUBLIC;
        }
        try {
            return GroupVisibility.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException exception) {
            return PUBLIC;
        }
    }

    public String toDatabase() {
        return name();
    }
}
