package com.heneria.lobby.cosmetics;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

/**
 * Handles equipping and unequipping of player cosmetics.
 */
public class CosmeticsManager implements Listener {

    /**
     * List storing cosmetic data for players currently equipped.
     * The list is synchronized to allow safe access from multiple threads.
     */
    private final List<PlayerCosmetic> equippedCosmetics = Collections.synchronizedList(new ArrayList<>());

    /**
     * Equip a cosmetic for a player.
     *
     * @param playerId   UUID of the player
     * @param cosmeticId identifier of the cosmetic
     */
    public void equipCosmetic(UUID playerId, String cosmeticId) {
        synchronized (equippedCosmetics) {
            equippedCosmetics.removeIf(data -> data.getPlayerId().equals(playerId));
            equippedCosmetics.add(new PlayerCosmetic(playerId, cosmeticId));
        }
    }

    /**
     * Unequip any cosmetic for the specified player.
     *
     * @param playerId UUID of the player
     */
    public void unequipCosmetic(UUID playerId) {
        synchronized (equippedCosmetics) {
            equippedCosmetics.removeIf(data -> data.getPlayerId().equals(playerId));
        }
    }

    /**
     * Handle player joining the server by equipping stored cosmetics.
     *
     * @param event join event
     */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // In a full implementation, cosmeticId would come from persistent storage.
        // For now, we simply mark the player as having no specific cosmetic equipped.
        equipCosmetic(player.getUniqueId(), null);
    }

    /**
     * Handle player leaving the server by unequipping cosmetics.
     *
     * @param event quit event
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        unequipCosmetic(player.getUniqueId());
    }

    /**
     * Simple data holder for player cosmetics.
     */
    private static class PlayerCosmetic {
        private final UUID playerId;
        private final String cosmeticId;

        PlayerCosmetic(UUID playerId, String cosmeticId) {
            this.playerId = playerId;
            this.cosmeticId = cosmeticId;
        }

        UUID getPlayerId() {
            return playerId;
        }

        String getCosmeticId() {
            return cosmeticId;
        }
    }
}

