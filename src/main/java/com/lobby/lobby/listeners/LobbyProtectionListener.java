package com.lobby.lobby.listeners;

import com.lobby.lobby.LobbyManager;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.weather.ThunderChangeEvent;
import org.bukkit.event.weather.WeatherChangeEvent;

public class LobbyProtectionListener implements Listener {

    private final LobbyManager lobbyManager;

    public LobbyProtectionListener(final LobbyManager lobbyManager) {
        this.lobbyManager = lobbyManager;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(final BlockBreakEvent event) {
        if (!lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            return;
        }
        if (!lobbyManager.isBypassing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockPlace(final BlockPlaceEvent event) {
        if (!lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            return;
        }
        if (!lobbyManager.isBypassing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlayerInteract(final PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (!lobbyManager.isLobbyWorld(player.getWorld())) {
            return;
        }
        if (lobbyManager.isBypassing(player)) {
            return;
        }
        if (event.getClickedBlock() != null || event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onArmorStandManipulate(final PlayerArmorStandManipulateEvent event) {
        if (!lobbyManager.isLobbyWorld(event.getRightClicked().getWorld())) {
            return;
        }
        if (!lobbyManager.isBypassing(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamage(final EntityDamageEvent event) {
        final Entity entity = event.getEntity();
        if (!lobbyManager.isLobbyWorld(entity.getWorld())) {
            return;
        }
        if (entity instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        if (entity instanceof Player player) {
            if (event.getCause() == DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }
            if (event.getCause() == DamageCause.VOID) {
                event.setCancelled(true);
                lobbyManager.teleportToLobby(player);
            }
            return;
        }
        if (lobbyManager.isLobbyWorld(entity.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityDamageByEntity(final EntityDamageByEntityEvent event) {
        final Entity target = event.getEntity();
        if (!lobbyManager.isLobbyWorld(target.getWorld())) {
            return;
        }
        if (target instanceof ArmorStand) {
            event.setCancelled(true);
            return;
        }
        if (target instanceof Player && event.getDamager() instanceof Player) {
            event.setCancelled(true);
            return;
        }
        if (event.getDamager() instanceof Player player) {
            if (!lobbyManager.isBypassing(player)) {
                event.setCancelled(true);
            }
            return;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            if (target instanceof Player || !lobbyManager.isBypassing((Player) shooter)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFoodLevelChange(final FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && lobbyManager.isLobbyWorld(player.getWorld())) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0F);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWeatherChange(final WeatherChangeEvent event) {
        if (event.toWeatherState() && lobbyManager.isLobbyWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onThunderChange(final ThunderChangeEvent event) {
        if (event.toThunderState() && lobbyManager.isLobbyWorld(event.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBurn(final BlockBurnEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockIgnite(final BlockIgniteEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(final BlockSpreadEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            if (event.getSource() != null
                    && (event.getSource().getType() == Material.FIRE || event.getBlock().getType() == Material.FIRE)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeavesDecay(final LeavesDecayEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(final BlockFadeEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(final BlockFormEvent event) {
        if (lobbyManager.isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

}
