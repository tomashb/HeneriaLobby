package com.lobby.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DatabaseManagerTest {

    @Test
    @DisplayName("from() should resolve MYSQL value")
    void fromShouldResolveMysql() {
        final DatabaseManager.DatabaseType type = DatabaseManager.DatabaseType.from("mysql");
        assertEquals(DatabaseManager.DatabaseType.MYSQL, type);
    }

    @Test
    @DisplayName("from() should default to SQLITE on unknown values")
    void fromShouldFallbackToSqlite() {
        final DatabaseManager.DatabaseType type = DatabaseManager.DatabaseType.from("unknown");
        assertEquals(DatabaseManager.DatabaseType.SQLITE, type);
    }

    @Test
    @DisplayName("from() should handle null values")
    void fromShouldHandleNull() {
        final DatabaseManager.DatabaseType type = DatabaseManager.DatabaseType.from(null);
        assertEquals(DatabaseManager.DatabaseType.SQLITE, type);
    }
}
