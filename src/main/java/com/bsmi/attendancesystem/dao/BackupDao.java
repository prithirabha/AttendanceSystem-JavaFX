package com.bsmi.attendancesystem.dao;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** The database side of a backup: a safe copy of the open database, and its schema version. */
public final class BackupDao {
    private final Database database;

    public BackupDao(Database database) {
        this.database = database;
    }

    /**
     * Writes a zip holding a consistent copy of the database file, using H2's {@code BACKUP TO},
     * which is safe while the app keeps using the database.
     *
     * @throws DataAccessException if the database is not an H2 file or the zip can't be written
     */
    public void backupTo(Path zip) {
        // BACKUP TO takes only a text literal, not a ? parameter, so quotes are doubled by hand
        String fileName = zip.toAbsolutePath().toString().replace("'", "''");
        try (Connection connection = database.open();
                Statement statement = connection.createStatement()) {
            statement.execute("BACKUP TO '" + fileName + "'");
        } catch (SQLException e) {
            throw new DataAccessException("back up the database to " + zip, e);
        }
    }

    /**
     * @return the newest migration applied to the database, e.g. 3 after {@code V3__*.sql}
     * @throws DataAccessException if the database can't be read or has no migration history
     */
    public int schemaVersion() {
        String sql =
                "SELECT MAX(CAST(\"version\" AS INT)) FROM \"flyway_schema_history\""
                        + " WHERE \"success\"";
        try (Connection connection = database.open();
                PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            result.next();
            return result.getInt(1);
        } catch (SQLException e) {
            throw new DataAccessException("read the database version", e);
        }
    }
}
