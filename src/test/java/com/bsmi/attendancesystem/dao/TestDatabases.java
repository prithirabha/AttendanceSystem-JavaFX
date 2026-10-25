package com.bsmi.attendancesystem.dao;

import com.bsmi.attendancesystem.DatabaseMigrations;
import com.bsmi.attendancesystem.DatabaseSettings;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;

/** Throwaway databases for tests. Each call gets its own, so tests can't affect each other. */
public final class TestDatabases {
    private TestDatabases() {}

    /** @return a new in-memory database with the schema and demo data */
    public static Database migratedInMemory() {
        // DB_CLOSE_DELAY keeps the database alive between the DAO's short-lived connections
        DatabaseSettings settings =
                new DatabaseSettings(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "admin", "admin");
        DatabaseMigrations.applyPending(settings);
        return new Database(settings);
    }

    /** @return a new database file in {@code dataFolder}, as the app keeps it, migrated */
    public static Database migratedFile(Path dataFolder) {
        DatabaseSettings settings =
                new DatabaseSettings(DatabaseSettings.embeddedUrl(dataFolder), "admin", "admin");
        DatabaseMigrations.applyPending(settings);
        return new Database(settings);
    }

    /** @return a database whose every connection attempt fails */
    public static Database unreachable() {
        // IFEXISTS=TRUE refuses to create the database, so opening a missing one fails
        return new Database(
                new DatabaseSettings(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";IFEXISTS=TRUE", "admin", "admin"));
    }

    /** @return a database that opens but has no tables, so every query fails */
    public static Database withoutTables() {
        return new Database(
                new DatabaseSettings(
                        "jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "admin", "admin"));
    }

    /** The two ways a DAO call can fail: no connection at all, or a query that fails. */
    public static Stream<Named<Database>> broken() {
        return Stream.of(
                Named.of("unreachable", unreachable()), Named.of("without tables", withoutTables()));
    }

    public static void execute(Database database, String sql) throws SQLException {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    /** @return the number in the first column of the first row, e.g. of a COUNT(*) */
    public static long queryNumber(Database database, String sql) throws SQLException {
        try (Connection connection = database.open();
                Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}
