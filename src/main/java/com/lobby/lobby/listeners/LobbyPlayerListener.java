package com.lobby.lobby.listeners;

import com.lobby.lobby.LobbyManager;
import com.lobby.utils.MessageUtils;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class LobbyPlayerListener implements Listener {

    private final LobbyManager lobbyManager;

    public LobbyPlayerListener(final LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(final PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final boolean hasSpawn = lobbyManager.hasLobbySpawn();
        final boolean teleported = lobbyManager.teleportToLobby(player);
        lobbyManager.preparePlayer(player);
        if (!hasSpawn && player.hasPermission("lobby.admin")) {
            MessageUtils.sendConfigMessage(player, "lobby.spawn_not_set");
        } else if (!teleported) {
            MessageUtils.sendConfigMessage(player, "lobby.teleport_failed");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(final PlayerMoveEvent event) {
        final Location to = event.getTo();
        if (to == null) {
            return;
        }
        final Player player = event.getPlayer();
        if (!lobbyManager.isLobbyWorld(player.getWorld())) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        if (to.getY() < lobbyManager.getVoidTeleportY()) {
            lobbyManager.teleportToLobby(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(final PlayerRespawnEvent event) {
        final Location spawn = lobbyManager.getLobbySpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        final Player player = event.getPlayer();
        if (!lobbyManager.isBypassing(player)) {
            lobbyManager.preparePlayer(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent event) {
        lobbyManager.removeBypass(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerKick(final PlayerKickEvent event) {
        lobbyManager.removeBypass(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onItemDrop(final PlayerDropItemEvent event) {
        final Player player = event.getPlayer();
        if (!lobbyManager.isLobbyWorld(player.getWorld())) {
            return;
        }
        if (!lobbyManager.isBypassing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onItemPickup(final EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (!lobbyManager.isLobbyWorld(player.getWorld())) {
                return;
            }
            if (!lobbyManager.isBypassing(player)) {
                event.setCancelled(true);
            }
        }
    }
}
