package com.lobby.holograms;

import com.lobby.data.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class AnimationManager {

    private final JavaPlugin plugin;
    private boolean animationsEnabled = true;

    public AnimationManager(final JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setAnimationsEnabled(final boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;
    }

    public BukkitTask startAnimation(final Hologram hologram) {
        if (!animationsEnabled) {
            return null;
        }
        final HologramData data = hologram.getData();
        if (data.animation() == HologramData.AnimationType.NONE) {
            return null;
        }
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            switch (data.animation()) {
                case FLOAT -> animateFloat(hologram);
                case ROTATE -> animateRotate(hologram);
                default -> {
                }
            }
        }, 0L, 2L);
    }

    public void stopAnimation(final BukkitTask task) {
        if (task == null) {
            return;
        }
        task.cancel();
    }

    private void animateFloat(final Hologram hologram) {
        final long time = System.currentTimeMillis();
        final double offset = Math.sin(time * 0.003D) * 0.1D;
        for (int index = 0; index < hologram.getArmorStands().size(); index++) {
            final ArmorStand stand = hologram.getArmorStands().get(index);
            final Location location = new Location(stand.getWorld(), hologram.getData().x(),
                    hologram.getData().y() + (index * HologramRenderer.LINE_HEIGHT) + offset,
                    hologram.getData().z());
            stand.teleport(location);
        }
    }

    private void animateRotate(final Hologram hologram) {
        final long time = System.currentTimeMillis();
        final float yaw = (float) ((time * 0.1D) % 360);
        for (ArmorStand stand : hologram.getArmorStands()) {
            final Location location = stand.getLocation();
            location.setYaw(yaw);
            stand.teleport(location);
        }
    }
}
