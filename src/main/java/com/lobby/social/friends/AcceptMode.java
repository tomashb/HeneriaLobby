package com.lobby.social.friends;

public enum AcceptMode {
    ALL,
    FRIENDS_OF_FRIENDS,
    NONE;

    public static AcceptMode fromDatabase(final String value) {
        if (value == null) {
            return ALL;
        }
        try {
            return AcceptMode.valueOf(value.toUpperCase());
        } catch (final IllegalArgumentException exception) {
            return ALL;
        }
    }

    public String toDatabase() {
        return name();
    }
}
