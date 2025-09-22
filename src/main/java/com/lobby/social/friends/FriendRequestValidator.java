package com.lobby.social.friends;

import com.lobby.settings.PlayerSettings;
import com.lobby.settings.PlayerSettingsManager;
import com.lobby.settings.FriendRequestSetting;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Validates whether a friend request can be sent between two players based on their settings
 * and existing social relations.
 */
public class FriendRequestValidator {

    private final PlayerSettingsManager settingsManager;
    private final FriendManager friendManager;

    public FriendRequestValidator(final PlayerSettingsManager settingsManager,
                                  final FriendManager friendManager) {
        this.settingsManager = settingsManager;
        this.friendManager = friendManager;
    }

    /**
     * Determines if a friend request is allowed between the sender and the target player.
     *
     * @param sender the UUID of the player sending the request
     * @param target the UUID of the player receiving the request
     * @return the validation result describing whether the request is allowed
     */
    public ValidationResult canSendFriendRequest(final UUID sender, final UUID target) {
        if (sender == null || target == null) {
            return ValidationResult.denied(FriendRequestResult.DATABASE_ERROR);
        }
        if (settingsManager == null) {
            return ValidationResult.allowed();
        }

        final PlayerSettings targetSettings = settingsManager.getPlayerSettings(target);
        final FriendRequestSetting setting = targetSettings.getFriendRequestSetting();

        switch (setting) {
            case DISABLED:
                return ValidationResult.denied(FriendRequestResult.SETTINGS_DISABLED);
            case FRIENDS_OF_FRIENDS:
                if (!haveMutualFriends(sender, target)) {
                    return ValidationResult.denied(FriendRequestResult.MUTUAL_FRIENDS_REQUIRED);
                }
                break;
            case EVERYONE:
                break;
        }

        if (friendManager.areFriends(sender, target)) {
            return ValidationResult.denied(FriendRequestResult.ALREADY_FRIENDS);
        }
        if (friendManager.hasPendingRequest(sender, target)) {
            return ValidationResult.denied(FriendRequestResult.REQUEST_ALREADY_SENT);
        }
        if (friendManager.isBlocked(target, sender)) {
            return ValidationResult.denied(FriendRequestResult.BLOCKED);
        }

        return ValidationResult.allowed();
    }

    private boolean haveMutualFriends(final UUID player1, final UUID player2) {
        final Set<UUID> friends1 = safeFriends(player1);
        final Set<UUID> friends2 = safeFriends(player2);
        for (final UUID friend : friends1) {
            if (friends2.contains(friend)) {
                return true;
            }
        }
        return false;
    }

    private Set<UUID> safeFriends(final UUID uuid) {
        if (uuid == null) {
            return Collections.emptySet();
        }
        return friendManager.getFriends(uuid);
    }

    public static class ValidationResult {
        private final boolean allowed;
        private final FriendRequestResult failureResult;

        private ValidationResult(final boolean allowed, final FriendRequestResult failureResult) {
            this.allowed = allowed;
            this.failureResult = failureResult;
        }

        public static ValidationResult allowed() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult denied(final FriendRequestResult result) {
            return new ValidationResult(false, result);
        }

        public boolean isAllowed() {
            return allowed;
        }

        public FriendRequestResult getFailureResult() {
            return failureResult;
        }
    }
}
