package com.bsmi.attendancesystem.dao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BackupDaoTest {
    // V1 to V3 in src/main/resources/db/migration
    private static final int NEWEST_MIGRATION = 3;

    @TempDir Path tempDir;

    @Test
    void backupIsAZipHoldingTheDatabaseFile() throws IOException {
        BackupDao dao = new BackupDao(TestDatabases.migratedFile(tempDir.resolve("data")));
        Path zip = tempDir.resolve("copy.zip");

        dao.backupTo(zip);

        assertThat(entryNames(zip)).containsExactly("attendance_system.mv.db");
    }

    @Test
    void quoteInTheFileNameCannotBreakOutOfTheCommand() throws IOException {
        BackupDao dao = new BackupDao(TestDatabases.migratedFile(tempDir.resolve("data")));
        Path zip = tempDir.resolve("it's'; DROP TABLE student; --.zip");

        dao.backupTo(zip);

        assertThat(entryNames(zip)).containsExactly("attendance_system.mv.db");
    }

    @Test
    void schemaVersionIsTheNewestMigration() {
        Database database = TestDatabases.migratedInMemory();

        assertThat(new BackupDao(database).schemaVersion()).isEqualTo(NEWEST_MIGRATION);
    }

    @Test
    void backupWhereNoFolderCanBeMadeFails() throws IOException {
        BackupDao dao = new BackupDao(TestDatabases.migratedFile(tempDir.resolve("data")));
        Path notAFolder = Files.writeString(tempDir.resolve("not-a-folder"), "");

        assertThatThrownBy(() -> dao.backupTo(notAFolder.resolve("copy.zip")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void backupOfAnUnreachableDatabaseFails() {
        BackupDao dao = new BackupDao(TestDatabases.unreachable());

        assertThatThrownBy(() -> dao.backupTo(tempDir.resolve("copy.zip")))
                .isInstanceOf(DataAccessException.class);
    }

    @ParameterizedTest
    @MethodSource("com.bsmi.attendancesystem.dao.TestDatabases#broken")
    void schemaVersionOfABrokenDatabaseFails(Database database) {
        assertThatThrownBy(() -> new BackupDao(database).schemaVersion())
                .isInstanceOf(DataAccessException.class);
    }

    private static List<String> entryNames(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                names.add(entry.getName());
            }
        }
        return names;
    }
}
