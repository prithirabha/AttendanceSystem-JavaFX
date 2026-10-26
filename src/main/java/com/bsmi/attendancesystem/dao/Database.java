package com.bsmi.attendancesystem.dao;

import com.bsmi.attendancesystem.DatabaseSettings;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens connections to the database described by {@link DatabaseSettings}. */
public final class Database {
    private final DatabaseSettings settings;

    public Database(DatabaseSettings settings) {
        this.settings = settings;
    }

    public static Database fromCurrentSettings() {
        return new Database(DatabaseSettings.current());
    }

    /** @return where the database is, e.g. to tell the built-in file from a server */
    public String url() {
        return settings.url();
    }

    /**
     * @return a new connection; the caller closes it (use try-with-resources)
     * @throws DataAccessException if the database can't be reached
     */
    public Connection open() {
        try {
            return DriverManager.getConnection(
                    settings.url(), settings.user(), settings.password());
        } catch (SQLException e) {
            throw new DataAccessException("open the database at " + settings.url(), e);
        }
    }

    /**
     * Opens a read-only connection to the built-in database file in {@code folder}, logging in
     * the same way as this database. Lets a restored database be inspected before it replaces
     * the live one, with no chance of writing to it.
     *
     * @throws DataAccessException if the database can't be opened
     */
    public Connection openReadOnlyFile(Path folder) {
        // ACCESS_MODE_DATA=r never writes the file; IFEXISTS=TRUE won't create a missing one
        String fileUrl =
                DatabaseSettings.embeddedUrl(folder) + ";ACCESS_MODE_DATA=r;IFEXISTS=TRUE";
        try {
            return DriverManager.getConnection(fileUrl, settings.user(), settings.password());
        } catch (SQLException e) {
            throw new DataAccessException("open the database at " + fileUrl, e);
        }
    }
}
