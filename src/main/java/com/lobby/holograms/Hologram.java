package com.lobby.holograms;

import com.lobby.data.HologramData;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Hologram {

    private HologramData data;
    private final List<ArmorStand> armorStands = new ArrayList<>();
    private final HologramManager manager;
    private boolean spawned;
    private BukkitTask animationTask;

    public Hologram(final HologramData data, final HologramManager manager) {
        this.data = data;
        this.manager = manager;
    }

    public void spawn() {
        if (spawned || !data.visible()) {
            return;
        }
        remove();
        final List<ArmorStand> spawnedArmorStands = manager.getRenderer().spawnArmorStands(data);
        armorStands.addAll(spawnedArmorStands);
        spawned = !armorStands.isEmpty();
        if (!spawned) {
            return;
        }
        updateLines();
        animationTask = manager.getAnimationManager().startAnimation(this);
    }

    public void updateLines() {
        if (!spawned) {
            return;
        }
        if (armorStands.size() != data.lines().size()) {
            respawn();
            return;
        }
        for (int index = 0; index < armorStands.size(); index++) {
            final String line = data.lines().get(data.lines().size() - 1 - index);
            final String processed = manager.getPlaceholderProcessor().process(line, null);
            armorStands.get(index).setCustomName(ChatColor.translateAlternateColorCodes('&', processed));
        }
    }

    public void remove() {
        if (animationTask != null) {
            manager.getAnimationManager().stopAnimation(animationTask);
            animationTask = null;
        }
        manager.getRenderer().remove(armorStands);
        armorStands.clear();
        spawned = false;
    }

    public void teleport(final Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        remove();
        data = data.withLocation(location.getWorld().getName(), location.getX(), location.getY(), location.getZ());
        spawn();
    }

    public void respawn() {
        remove();
        spawn();
    }

    public HologramData getData() {
        return data;
    }

    public List<ArmorStand> getArmorStands() {
        return Collections.unmodifiableList(armorStands);
    }

    public boolean isSpawned() {
        return spawned;
    }
}
