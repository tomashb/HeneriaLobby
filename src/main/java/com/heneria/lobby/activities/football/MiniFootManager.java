package com.heneria.lobby.activities.football;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Slime;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * Very small slime football mini-game. The slime is pushed around by players
 * and when it enters a goal area a message is broadcast and it respawns.
 */
public class MiniFootManager {

    private final JavaPlugin plugin;
    private final Location spawn;
    private final Cuboid goalA;
    private final Cuboid goalB;
    private final String message;

    private Slime slime;

    public MiniFootManager(JavaPlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.spawn = parseLocation(config.getString("mini-foot.slime-spawn"));
        this.goalA = new Cuboid(config.getString("mini-foot.goal-a.min"), config.getString("mini-foot.goal-a.max"));
        this.goalB = new Cuboid(config.getString("mini-foot.goal-b.min"), config.getString("mini-foot.goal-b.max"));
        this.message = config.getString("mini-foot.message", "Goal!");

        Bukkit.getScheduler().runTask(plugin, this::spawnSlime);

        new BukkitRunnable() {
            @Override
            public void run() {
                checkGoal();
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnSlime() {
        if (spawn == null) return;
        if (slime != null && !slime.isDead()) slime.remove();
        slime = spawn.getWorld().spawn(spawn, Slime.class);
        slime.setAI(false);
        slime.setInvulnerable(true);
        slime.setSize(1);
    }

    private void checkGoal() {
        if (slime == null || slime.isDead()) return;
        Location loc = slime.getLocation();
        if (goalA.contains(loc)) {
            Bukkit.broadcastMessage(message.replace("%team%", "A"));
            slime.teleport(spawn);
        } else if (goalB.contains(loc)) {
            Bukkit.broadcastMessage(message.replace("%team%", "B"));
            slime.teleport(spawn);
        }
    }

    private Location parseLocation(String raw) {
        if (raw == null) return null;
        String[] p = raw.split(",");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
    }

    private static class Cuboid {
        private final World world;
        private final int x1, y1, z1, x2, y2, z2;

        Cuboid(String min, String max) {
            String[] a = min.split(",");
            String[] b = max.split(",");
            this.world = Bukkit.getWorld(a[0]);
            int ax = Integer.parseInt(a[1]);
            int ay = Integer.parseInt(a[2]);
            int az = Integer.parseInt(a[3]);
            int bx = Integer.parseInt(b[1]);
            int by = Integer.parseInt(b[2]);
            int bz = Integer.parseInt(b[3]);
            this.x1 = Math.min(ax, bx);
            this.y1 = Math.min(ay, by);
            this.z1 = Math.min(az, bz);
            this.x2 = Math.max(ax, bx);
            this.y2 = Math.max(ay, by);
            this.z2 = Math.max(az, bz);
        }

        boolean contains(Location loc) {
            if (loc.getWorld() != world) return false;
            int x = loc.getBlockX(), y = loc.getBlockY(), z = loc.getBlockZ();
            return x >= x1 && x <= x2 && y >= y1 && y <= y2 && z >= z1 && z <= z2;
        }
    }
}

