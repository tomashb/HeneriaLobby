package com.lobby.friends;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory provider used while the friends backend is still under
 * construction. Values can be overridden per player and fallback to sensible
 * defaults otherwise.
 */
public class DefaultFriendsDataProvider implements FriendsDataProvider {

    private final Map<UUID, FriendsPlaceholderData> overrides = new ConcurrentHashMap<>();

    @Override
    public FriendsPlaceholderData resolve(final Player player) {
        if (player == null) {
            return FriendsPlaceholderData.empty();
        }
        return overrides.getOrDefault(player.getUniqueId(), FriendsPlaceholderData.empty());
    }

    public void updateData(final UUID playerId, final FriendsPlaceholderData data) {
        if (playerId == null || data == null) {
            return;
        }
        overrides.put(playerId, data);
    }

    public void clearData(final UUID playerId) {
        if (playerId == null) {
            return;
        }
        overrides.remove(playerId);
    }
}

