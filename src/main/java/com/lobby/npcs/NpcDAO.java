package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.data.NPCData;
import com.lobby.utils.LogUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Data access object responsible for persisting NPC definitions.
 */
public class NpcDAO {

    private final DatabaseManager databaseManager;
    private final LobbyPlugin plugin;

    public NpcDAO(final DatabaseManager databaseManager, final LobbyPlugin plugin) {
        this.databaseManager = Objects.requireNonNull(databaseManager, "databaseManager");
        this.plugin = plugin;
    }

    public List<NPCData> loadAllNpcs() throws SQLException {
        final List<NPCData> npcs = new ArrayList<>();
        final String sql = "SELECT name, display_name, world, x, y, z, yaw, pitch, head_texture, armor_color, actions, visible "
                + "FROM npcs WHERE visible = TRUE";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                try {
                    npcs.add(mapNpcData(resultSet));
                } catch (final Exception exception) {
                    if (plugin != null) {
                        LogUtils.warning(plugin, "Failed to load NPC from database: " + exception.getMessage());
                    }
                }
            }
        }

        return npcs;
    }

    public boolean createNpc(final NPCData data) throws SQLException {
        final String sql = """
                INSERT INTO npcs (name, display_name, world, x, y, z, yaw, pitch, head_texture, armor_color, actions, visible)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, data.name());
            setNullableString(statement, 2, data.displayName());
            statement.setString(3, data.world());
            statement.setDouble(4, data.x());
            statement.setDouble(5, data.y());
            statement.setDouble(6, data.z());
            statement.setFloat(7, data.yaw());
            statement.setFloat(8, data.pitch());
            setNullableString(statement, 9, data.headTexture());
            setNullableString(statement, 10, data.armorColor());
            statement.setString(11, actionsToJson(data.actions()));
            statement.setBoolean(12, data.visible());

            return statement.executeUpdate() > 0;
        }
    }

    public boolean deleteNpc(final String name) throws SQLException {
        final String sql = "DELETE FROM npcs WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateNpcActions(final String name, final List<String> actions) throws SQLException {
        final String sql = "UPDATE npcs SET actions = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, actionsToJson(actions));
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateNpcArmorColor(final String name, final String armorColor) throws SQLException {
        final String sql = "UPDATE npcs SET armor_color = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, armorColor);
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    private NPCData mapNpcData(final ResultSet resultSet) throws SQLException {
        final String actionsJson = resultSet.getString("actions");
        final List<String> actions = parseActions(actionsJson);
        return new NPCData(
                resultSet.getString("name"),
                resultSet.getString("display_name"),
                resultSet.getString("world"),
                resultSet.getDouble("x"),
                resultSet.getDouble("y"),
                resultSet.getDouble("z"),
                resultSet.getFloat("yaw"),
                resultSet.getFloat("pitch"),
                resultSet.getString("head_texture"),
                resultSet.getString("armor_color"),
                actions,
                resultSet.getBoolean("visible")
        );
    }

    private List<String> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            if (actionsJson.startsWith("[") && actionsJson.endsWith("]")) {
                actionsJson = actionsJson.substring(1, actionsJson.length() - 1);
                if (actionsJson.trim().isEmpty()) {
                    return new ArrayList<>();
                }

                return Arrays.stream(actionsJson.split("\\",\\""))
                        .map(s -> s.replace("\\"", ""))
                        .collect(Collectors.toList());
            }
        } catch (final Exception exception) {
            if (plugin != null) {
                LogUtils.warning(plugin, "Failed to parse NPC actions: " + actionsJson);
            }
        }

        return List.of("[MESSAGE] &cError loading NPC actions");
    }

    private String actionsToJson(final List<String> actions) {
        if (actions == null || actions.isEmpty()) {
            return "[]";
        }
        return "[\"" + String.join("\",\"", actions) + "\"]";
    }

    private void setNullableString(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }
}
