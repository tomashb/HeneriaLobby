package com.heneria.lobby.friends;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks last messaged players to enable the /r command.
 */
public class PrivateMessageManager {

    private final Map<UUID, UUID> lastMessaged = new ConcurrentHashMap<>();

    public void setLastMessaged(UUID from, UUID to) {
        lastMessaged.put(from, to);
    }

    public UUID getLastMessaged(UUID player) {
        return lastMessaged.get(player);
    }

    public void clear(UUID player) {
        lastMessaged.remove(player);
    }
}
