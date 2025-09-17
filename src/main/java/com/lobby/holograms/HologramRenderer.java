package com.lobby.holograms;

import com.lobby.data.HologramData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.List;

public class HologramRenderer {

    public static final double LINE_HEIGHT = 0.25D;

    public List<ArmorStand> spawnArmorStands(final HologramData data) {
        final World world = Bukkit.getWorld(data.world());
        if (world == null) {
            return List.of();
        }
        final Location baseLocation = new Location(world, data.x(), data.y(), data.z());
        final List<String> lines = data.lines();
        final List<ArmorStand> spawned = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            final double offset = (lines.size() - 1 - index) * LINE_HEIGHT;
            final Location location = baseLocation.clone().add(0.0D, offset, 0.0D);
            final ArmorStand stand = world.spawn(location, ArmorStand.class, this::setupArmorStand);
            spawned.add(stand);
        }
        return spawned;
    }

    public void setupArmorStand(final ArmorStand armorStand) {
        if (armorStand == null) {
            return;
        }
        armorStand.setVisible(false);
        armorStand.setGravity(false);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomNameVisible(false);
        armorStand.setMarker(true);
        armorStand.setInvulnerable(true);
        armorStand.setSilent(true);
    }

    public void remove(final List<ArmorStand> armorStands) {
        if (armorStands == null || armorStands.isEmpty()) {
            return;
        }
        armorStands.forEach(ArmorStand::remove);
    }
}
