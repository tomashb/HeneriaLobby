package com.lobby.holograms;

import com.lobby.core.DatabaseManager;
import org.bukkit.Location;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Provides database access helpers for hologram persistence.
 */
public class HologramDAO {

    private static final String UPDATE_LOCATION_SQL =
            "UPDATE holograms SET world = ?, x = ?, y = ?, z = ? WHERE name = ?";

    private final DatabaseManager databaseManager;

    public HologramDAO(final DatabaseManager databaseManager) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
    }

    public boolean updateLocation(final String name, final Location location) throws SQLException {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(location, "location");
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(UPDATE_LOCATION_SQL)) {
            statement.setString(1, location.getWorld() != null ? location.getWorld().getName() : null);
            statement.setDouble(2, location.getX());
            statement.setDouble(3, location.getY());
            statement.setDouble(4, location.getZ());
            statement.setString(5, name);
            return statement.executeUpdate() > 0;
        }
    }
}
