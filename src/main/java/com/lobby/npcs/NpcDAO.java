package com.lobby.npcs;

import com.lobby.LobbyPlugin;
import com.lobby.core.DatabaseManager;
import com.lobby.data.NPCData;
import com.lobby.utils.ItemSerializationUtils;
import com.lobby.utils.LogUtils;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
        final String sql = "SELECT name, display_name, world, x, y, z, yaw, pitch, head_texture, armor_color, actions, animation,"
                + " visible, main_hand_item, off_hand_item FROM npcs WHERE visible = TRUE";

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
                INSERT INTO npcs (name, display_name, world, x, y, z, yaw, pitch, head_texture, armor_color, actions, animation,
                visible, main_hand_item, off_hand_item)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            setNullableString(statement, 12, data.animation());
            statement.setBoolean(13, data.visible());
            setNullableString(statement, 14, serializeItem(data.mainHandItem()));
            setNullableString(statement, 15, serializeItem(data.offHandItem()));

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

    public boolean updateNpcAnimation(final String name, final String animation) throws SQLException {
        final String sql = "UPDATE npcs SET animation = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, animation);
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
                resultSet.getString("animation"),
                resultSet.getBoolean("visible"),
                deserializeItem(resultSet.getString("main_hand_item")),
                deserializeItem(resultSet.getString("off_hand_item"))
        );
    }

    public boolean updateNpcMainHandItem(final String name, final ItemStack item) throws SQLException {
        final String sql = "UPDATE npcs SET main_hand_item = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, serializeItem(item));
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    public boolean updateNpcOffHandItem(final String name, final ItemStack item) throws SQLException {
        final String sql = "UPDATE npcs SET off_hand_item = ? WHERE name = ?";
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            setNullableString(statement, 1, serializeItem(item));
            statement.setString(2, name);
            return statement.executeUpdate() > 0;
        }
    }

    private List<String> parseActions(String actionsJson) {
        if (actionsJson == null || actionsJson.trim().isEmpty()) {
            return new ArrayList<>();
        }

        try {
            final String trimmed = actionsJson.trim();
            if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                return new ArrayList<>();
            }

            final List<String> parsedActions = new ArrayList<>();
            final StringBuilder current = new StringBuilder();
            boolean insideString = false;
            boolean escaping = false;

            for (int index = 1; index < trimmed.length() - 1; index++) {
                final char character = trimmed.charAt(index);

                if (escaping) {
                    current.append(unescapeJsonCharacter(character));
                    escaping = false;
                    continue;
                }

                if (character == '\\') {
                    escaping = true;
                    continue;
                }

                if (character == '"') {
                    insideString = !insideString;
                    if (!insideString) {
                        parsedActions.add(current.toString());
                        current.setLength(0);
                    }
                    continue;
                }

                if (insideString) {
                    current.append(character);
                }
            }

            if (!parsedActions.isEmpty()) {
                return parsedActions;
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

        final StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < actions.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }

            builder.append('"')
                    .append(escapeJsonContent(actions.get(index)))
                    .append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private char unescapeJsonCharacter(final char character) {
        return switch (character) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> character;
        };
    }

    private String escapeJsonContent(final String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        final StringBuilder escaped = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            escaped.append(switch (character) {
                case '"' -> "\\\"";
                case '\\' -> "\\\\";
                case '\n' -> "\\n";
                case '\r' -> "\\r";
                case '\t' -> "\\t";
                default -> String.valueOf(character);
            });
        }
        return escaped.toString();
    }

    private void setNullableString(final PreparedStatement statement, final int index, final String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
            return;
        }
        statement.setString(index, value);
    }

    private String serializeItem(final ItemStack item) {
        try {
            return ItemSerializationUtils.serializeItem(item);
        } catch (final IllegalStateException exception) {
            if (plugin != null) {
                LogUtils.warning(plugin, "Failed to serialize NPC item: " + exception.getMessage());
            }
            return null;
        }
    }

    private ItemStack deserializeItem(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return ItemSerializationUtils.deserializeItem(value);
        } catch (final IllegalStateException exception) {
            if (plugin != null) {
                LogUtils.warning(plugin, "Failed to deserialize NPC item: " + exception.getMessage());
            }
            return null;
        }
    }
}
