package com.heneria.lobby.activities.archery;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Simple target practice: hitting a target block awards points and sends a message.
 */
public class ArcheryListener implements Listener {

    private final String message;

    public ArcheryListener(FileConfiguration config) {
        this.message = config.getString("archery.message", "You scored %points% points!");
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getHitBlock() == null || event.getHitBlock().getType() != Material.TARGET) return;
        ProjectileSource source = event.getEntity().getShooter();
        if (source instanceof Player player) {
            int points = 10; // simplified scoring
            player.sendMessage(message.replace("%points%", String.valueOf(points)));
        }
    }
}

