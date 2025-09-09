package com.heneria.lobby.activities.parkour;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Listens for pressure plate interactions to control parkour flow.
 */
public class ParkourListener implements Listener {

    private final ParkourManager parkourManager;

    public ParkourListener(ParkourManager parkourManager) {
        this.parkourManager = parkourManager;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.PHYSICAL || event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();
        if (parkourManager.getStart() != null && parkourManager.getStart().getWorld().equals(loc.getWorld())
                && parkourManager.getStart().getBlockX() == loc.getBlockX()
                && parkourManager.getStart().getBlockY() == loc.getBlockY()
                && parkourManager.getStart().getBlockZ() == loc.getBlockZ()) {
            parkourManager.startRun(event.getPlayer());
            return;
        }
        if (parkourManager.isCheckpoint(loc)) {
            parkourManager.checkpoint(event.getPlayer(), loc);
            return;
        }
        if (parkourManager.getFinish() != null && parkourManager.getFinish().getWorld().equals(loc.getWorld())
                && parkourManager.getFinish().getBlockX() == loc.getBlockX()
                && parkourManager.getFinish().getBlockY() == loc.getBlockY()
                && parkourManager.getFinish().getBlockZ() == loc.getBlockZ()) {
            parkourManager.finishRun(event.getPlayer());
        }
    }
}

