package com.heneria.lobby.activities.parkour;

import com.heneria.lobby.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Handles timed parkour runs including checkpoints and database persistence.
 */
public class ParkourManager {

    private final JavaPlugin plugin;
    private final DatabaseManager databaseManager;
    private final FileConfiguration config;
    private final Location start;
    private final List<Location> checkpoints;
    private final Location finish;
    private final Location leaderboardLocation;
    private final int leaderboardSize;

    private final Map<UUID, Long> startTimes = new HashMap<>();
    private final Map<UUID, Location> lastCheckpoint = new HashMap<>();
    private final List<ArmorStand> hologramLines = new ArrayList<>();

    public ParkourManager(JavaPlugin plugin, DatabaseManager databaseManager, FileConfiguration config) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.config = config;

        this.start = parseLocation(config.getString("parkour.start"));
        this.finish = parseLocation(config.getString("parkour.finish"));

        this.checkpoints = new ArrayList<>();
        for (String s : config.getStringList("parkour.checkpoints")) {
            Location loc = parseLocation(s);
            if (loc != null) checkpoints.add(loc);
        }

        this.leaderboardLocation = parseLocation(config.getString("parkour.leaderboard.location"));
        this.leaderboardSize = config.getInt("parkour.leaderboard.size", 5);

        Bukkit.getScheduler().runTask(plugin, this::updateLeaderboard);
    }

    private Location parseLocation(String raw) {
        if (raw == null) return null;
        String[] parts = raw.split(",");
        if (parts.length < 4) return null;
        return new Location(Bukkit.getWorld(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
                Double.parseDouble(parts[3]));
    }

    public Location getStart() {
        return start;
    }

    public Location getFinish() {
        return finish;
    }

    public boolean isCheckpoint(Location loc) {
        for (Location cp : checkpoints) {
            if (sameBlock(cp, loc)) return true;
        }
        return false;
    }

    private boolean sameBlock(Location a, Location b) {
        return a.getWorld().equals(b.getWorld()) &&
                a.getBlockX() == b.getBlockX() &&
                a.getBlockY() == b.getBlockY() &&
                a.getBlockZ() == b.getBlockZ();
    }

    public void startRun(Player player) {
        startTimes.put(player.getUniqueId(), System.currentTimeMillis());
        lastCheckpoint.put(player.getUniqueId(), start.clone().add(0.5, 1, 0.5));
    }

    public void checkpoint(Player player, Location cp) {
        lastCheckpoint.put(player.getUniqueId(), cp.clone().add(0.5, 1, 0.5));
    }

    public void finishRun(Player player) {
        Long start = startTimes.remove(player.getUniqueId());
        if (start == null) return;
        long time = System.currentTimeMillis() - start;
        long best = getBestTime(player.getUniqueId());
        boolean newRecord = best == 0 || time < best;
        if (newRecord) {
            saveBestTime(player.getUniqueId(), time);
            player.sendMessage(config.getString("parkour.messages.best", "New record!").replace("%time%", formatTime(time)));
        }
        player.sendMessage(config.getString("parkour.messages.finish", "Finished in %time%s").replace("%time%", formatTime(time)));
        lastCheckpoint.remove(player.getUniqueId());
        if (newRecord) {
            updateLeaderboard();
        }
    }

    public Location getLastCheckpoint(Player player) {
        return lastCheckpoint.get(player.getUniqueId());
    }

    public void teleportToStart(Player player) {
        player.teleport(start.clone().add(0.5, 1, 0.5));
    }

    public void teleportToCheckpoint(Player player) {
        Location loc = lastCheckpoint.getOrDefault(player.getUniqueId(), start.clone().add(0.5, 1, 0.5));
        player.teleport(loc);
    }

    private String formatTime(long millis) {
        return String.format(Locale.US, "%.2f", millis / 1000.0);
    }

    private long getBestTime(UUID uuid) {
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT best_time FROM player_parkour_times WHERE player_uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("best_time");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load parkour time: " + e.getMessage());
        }
        return 0;
    }

    private void saveBestTime(UUID uuid, long time) {
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("REPLACE INTO player_parkour_times (player_uuid,best_time) VALUES (?,?)")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, time);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save parkour time: " + e.getMessage());
        }
    }

    public List<String> getTopTimes(int limit) {
        List<String> list = new ArrayList<>();
        try (Connection c = databaseManager.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT username,best_time FROM players p JOIN player_parkour_times t ON p.uuid=t.player_uuid ORDER BY best_time ASC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(rs.getString("username") + " - " + formatTime(rs.getLong("best_time")) + "s");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to load top times: " + e.getMessage());
        }
        return list;
    }

    /**
     * Updates the simple hologram leaderboard using armor stands.
     */
    public void updateLeaderboard() {
        if (leaderboardLocation == null) return;
        // remove existing
        hologramLines.forEach(ArmorStand::remove);
        hologramLines.clear();

        List<String> top = getTopTimes(leaderboardSize);
        Location base = leaderboardLocation.clone();
        for (int i = 0; i < top.size(); i++) {
            final int index = i;
            final String entry = top.get(i);
            Location lineLoc = base.clone().add(0, -0.25 * index, 0);
            ArmorStand stand = base.getWorld().spawn(lineLoc, ArmorStand.class, st -> {
                st.setMarker(true);
                st.setInvisible(true);
                st.setCustomNameVisible(true);
                st.setGravity(false);
                st.setCustomName("§e" + (index + 1) + ". §f" + entry);
            });
            hologramLines.add(stand);
        }
    }
}

