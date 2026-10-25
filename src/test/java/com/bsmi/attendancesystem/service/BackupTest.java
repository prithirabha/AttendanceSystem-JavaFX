package com.bsmi.attendancesystem.service;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.dao.BackupDao;
import com.bsmi.attendancesystem.dao.Database;
import com.bsmi.attendancesystem.dao.TestDatabases;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class BackupTest {
    private static final String PASSWORD = "Correct Horse Battery";
    private static final String PHOTO = "0f8fad5b-d9cb-469f-a165-70867728950e.png";
    private static final String OTHER_PHOTO = "7c9e6679-7425-40de-944b-e07fc1f90ae7.jpg";
    private static final byte[] PHOTO_BYTES = {1, 2, 3, 4};
    private static final byte[] DATABASE_BYTES = "not really a database".getBytes(UTF_8);
    private static final long SIZE_LIMIT = 10L * 1024 * 1024;
    private static final String INSERT_STUDENT =
            "INSERT INTO student (studentNum, semester, course, firstName, lastName, gender,"
                    + " birthDate, status, image, added_on) VALUES (900001, 'First Year', 'BSIT',"
                    + " 'Asha', 'Rabha', 'Female', DATE '2004-05-06', 'Enrolled', '',"
                    + " CURRENT_DATE)";

    @TempDir Path tempDir;

    // Few key rounds keep the tests fast; small chunks make even a small backup span many
    private final BackupCipher cipher = new BackupCipher(1000, 4096);
    private Path dataFolder;
    private Path pending;
    private Database database;
    private Backup backup;
    private int currentSchemaVersion;

    @BeforeEach
    void createDataFolder() {
        dataFolder = tempDir.resolve("data");
        pending = dataFolder.resolve(PendingRestore.PENDING_FOLDER);
        database = TestDatabases.migratedFile(dataFolder);
        backup = new Backup(database, dataFolder, cipher, SIZE_LIMIT);
        currentSchemaVersion = new BackupDao(database).schemaVersion();
    }

    @Test
    void restoredBackupBringsBackTheDataAndPhotos() throws IOException, SQLException {
        TestDatabases.execute(database, INSERT_STUDENT);
        writePhoto(dataFolder, PHOTO);
        Path file = tempDir.resolve("school" + Backup.FILE_EXTENSION);
        backup.write(PASSWORD, PASSWORD, file);

        Path otherFolder = tempDir.resolve("other-computer");
        Database otherDatabase = TestDatabases.migratedFile(otherFolder);
        writePhoto(otherFolder, OTHER_PHOTO);
        new Backup(otherDatabase, otherFolder, cipher, SIZE_LIMIT).restore(file, PASSWORD);
        PendingRestore.applyIfReady(otherFolder);

        assertThat(TestDatabases.queryNumber(otherDatabase, studentCount(900001))).isEqualTo(1);
        assertThat(otherFolder.resolve("images").resolve(PHOTO)).hasBinaryContent(PHOTO_BYTES);
        assertThat(otherFolder.resolve("images").resolve(OTHER_PHOTO)).doesNotExist();
        assertThat(otherFolder.resolve(PendingRestore.PENDING_FOLDER)).doesNotExist();
    }

    @Test
    void restoreWaitsForTheNextStartBehindACompleteMarker() throws IOException {
        Path file = writtenBackup();

        backup.restore(file, PASSWORD);

        assertThat(pending.resolve(PendingRestore.COMPLETE_MARKER)).exists();
        assertThat(pending.resolve(PendingRestore.DATABASE_FILE)).isRegularFile();
        assertThat(pending.resolve(PendingRestore.IMAGES_FOLDER)).isDirectory();
    }

    @Test
    void onlyAppPhotosAreBackedUp() throws IOException {
        writePhoto(dataFolder, PHOTO);
        Files.writeString(dataFolder.resolve("images/notes.txt"), "private notes");
        Files.createDirectories(dataFolder.resolve("images/" + OTHER_PHOTO));

        backup.restore(writtenBackup(), PASSWORD);

        try (Stream<Path> restored = Files.list(pending.resolve(PendingRestore.IMAGES_FOLDER))) {
            assertThat(restored.map(path -> path.getFileName().toString())).containsExactly(PHOTO);
        }
    }

    @Test
    void backupWithoutAnImagesFolderStillWorks() throws IOException {
        backup.restore(writtenBackup(), PASSWORD);

        assertThat(pending.resolve(PendingRestore.COMPLETE_MARKER)).exists();
    }

    @Test
    void writingReplacesAnOlderBackupFile() throws IOException {
        Path file = tempDir.resolve("school" + Backup.FILE_EXTENSION);
        Files.writeString(file, "an older backup");

        backup.write(PASSWORD, PASSWORD, file);

        backup.restore(file, PASSWORD);
        assertThat(pending.resolve(PendingRestore.COMPLETE_MARKER)).exists();
    }

    @Test
    void failedWriteLeavesNoFilesBehind() throws IOException {
        Path file = tempDir.resolve("missing-folder/school" + Backup.FILE_EXTENSION);

        assertThatThrownBy(() -> backup.write(PASSWORD, PASSWORD, file))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Could not write the backup");

        assertThat(file.getParent()).doesNotExist();
        try (Stream<Path> leftovers = Files.list(dataFolder)) {
            assertThat(leftovers.map(Path::getFileName).map(Path::toString))
                    .noneMatch(name -> name.startsWith("backup-"));
        }
    }

    @Test
    void databaseServerIsNotBackedUpOrRestored() throws IOException {
        Path file = writtenBackup();
        Backup ofServer = new Backup(TestDatabases.migratedInMemory(), dataFolder);

        assertThatThrownBy(ofServer::requireBuiltInDatabase)
                .isInstanceOf(NotAllowedException.class)
                .hasMessage(Backup.SERVER_DATABASE);
        assertThatThrownBy(() -> ofServer.write(PASSWORD, PASSWORD, tempDir.resolve("x")))
                .isInstanceOf(NotAllowedException.class);
        assertThatThrownBy(() -> ofServer.restore(file, PASSWORD))
                .isInstanceOf(NotAllowedException.class);
    }

    @ParameterizedTest
    @CsvSource({
        "Short1Aa,           Short1Aa,                 Backup password",
        "alllowercase12345,  alllowercase12345,        Backup password",
        "ALLUPPERCASE12345,  ALLUPPERCASE12345,        Backup password",
        "Correct Horse Battery, Correct Horse Batteyr, Repeated password",
        "Correct Horse Battery, '',                    Repeated password"
    })
    void weakOrMistypedPasswordIsRefusedNamingTheField(
            String password, String repeated, String field) {
        Path file = tempDir.resolve("school" + Backup.FILE_EXTENSION);

        assertThatThrownBy(() -> backup.write(password, repeated, file))
                .isInstanceOfSatisfying(
                        InvalidValueException.class,
                        e -> assertThat(e.fieldName()).isEqualTo(field));
        assertThat(file).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void emptyPasswordAsksForOne(String password) throws IOException {
        Path file = writtenBackup();

        assertThatThrownBy(() -> Backup.requireStrong(password, password))
                .isInstanceOf(MissingFieldException.class);
        assertThatThrownBy(() -> Backup.requireStrong(null, null))
                .isInstanceOf(MissingFieldException.class);
        assertThatThrownBy(() -> backup.restore(file, password))
                .isInstanceOf(MissingFieldException.class);
    }

    @Test
    void wrongPasswordLeavesNothingPending() throws IOException {
        Path file = writtenBackup();

        assertThatThrownBy(() -> backup.restore(file, "Wrong Horse Battery"))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
        assertThat(pending).doesNotExist();
    }

    @Test
    void changedByteLeavesNothingPending() throws IOException {
        Path file = writtenBackup();
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length - 20] ^= 1;
        Files.write(file, bytes);

        assertThatThrownBy(() -> backup.restore(file, PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
        assertThat(pending).doesNotExist();
    }

    @Test
    void newRestoreClearsAnUnfinishedOne() throws IOException {
        Files.createDirectories(pending);
        Files.writeString(pending.resolve("leftover"), "from a crash");

        backup.restore(writtenBackup(), PASSWORD);

        assertThat(pending.resolve("leftover")).doesNotExist();
    }

    @Test
    void missingBackupFileIsAClearError() {
        Path file = tempDir.resolve("missing" + Backup.FILE_EXTENSION);

        assertThatThrownBy(() -> backup.restore(file, PASSWORD))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Could not restore the backup");
        assertThat(pending).doesNotExist();
    }

    @Test
    void backupFromANewerDatabaseVersionIsRefused() throws IOException {
        Path file = encrypted(zipOf(Map.of(PendingRestore.DATABASE_FILE, DATABASE_BYTES)), 99);

        assertThatThrownBy(() -> backup.restore(file, PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.NEWER_DATABASE);
        assertThat(pending).doesNotExist();
    }

    @Test
    void backupFromAnOlderDatabaseVersionIsRestored() throws IOException {
        Path file = encrypted(zipOf(Map.of(PendingRestore.DATABASE_FILE, DATABASE_BYTES)), 1);

        backup.restore(file, PASSWORD);

        assertThat(pending.resolve(PendingRestore.DATABASE_FILE)).hasBinaryContent(DATABASE_BYTES);
    }

    @Test
    void imagesFolderEntryIsAllowed() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PendingRestore.DATABASE_FILE, DATABASE_BYTES);
        entries.put("images/", new byte[0]);
        entries.put("images/" + PHOTO, PHOTO_BYTES);

        backup.restore(encrypted(zipOf(entries)), PASSWORD);

        assertThat(pending.resolve("images").resolve(PHOTO)).hasBinaryContent(PHOTO_BYTES);
    }

    // Each would write outside the pending folder, or put files there the app never made
    @ParameterizedTest
    @ValueSource(
            strings = {
                "../evil.png",
                "../../evil.png",
                "/tmp/evil.png",
                "images/../../evil.png",
                "images/sub/" + PHOTO,
                "images/" + PHOTO + "/../../evil.png",
                "notes.txt",
                "images/notes.txt",
                "images/" + PHOTO + ".exe",
                "attendance_system.trace.db",
                "IMAGES/" + PHOTO
            })
    void unexpectedEntryRefusesTheWholeBackup(String entryName) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PendingRestore.DATABASE_FILE, DATABASE_BYTES);
        entries.put(entryName, PHOTO_BYTES);

        assertThatThrownBy(() -> backup.restore(encrypted(zipOf(entries)), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.UNEXPECTED_CONTENT);
        assertThat(pending).doesNotExist();
        assertThat(tempDir.resolve("evil.png")).doesNotExist();
        assertThat(dataFolder.resolve("evil.png")).doesNotExist();
    }

    @Test
    void entryThatAppearsTwiceRefusesTheWholeBackup() throws IOException {
        // ZipOutputStream refuses to write a name twice, so the entries are written by hand
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        zip.write(storedEntry(PendingRestore.DATABASE_FILE, DATABASE_BYTES, crcOf(DATABASE_BYTES)));
        zip.write(storedEntry(PendingRestore.DATABASE_FILE, PHOTO_BYTES, crcOf(PHOTO_BYTES)));

        assertThatThrownBy(() -> backup.restore(encrypted(zip.toByteArray()), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.UNEXPECTED_CONTENT);
        assertThat(pending).doesNotExist();
    }

    @Test
    void entryWithAWrongChecksumCountsAsDamaged() throws IOException {
        byte[] zip = storedEntry(PendingRestore.DATABASE_FILE, DATABASE_BYTES, 12345);

        assertThatThrownBy(() -> backup.restore(encrypted(zip), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
        assertThat(pending).doesNotExist();
    }

    @Test
    void backupThatUnpacksTooBigIsRefused() throws IOException {
        Backup limited = new Backup(database, dataFolder, cipher, 1000);
        // Zeros compress to almost nothing: a small file that unpacks big, like a "zip bomb"
        Path file = encrypted(zipOf(Map.of(PendingRestore.DATABASE_FILE, new byte[1001])));

        assertThatThrownBy(() -> limited.restore(file, PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.TOO_BIG);
        assertThat(pending).doesNotExist();
    }

    @Test
    void sizeLimitCountsAllEntriesTogether() throws IOException {
        Backup limited = new Backup(database, dataFolder, cipher, 1000);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put(PendingRestore.DATABASE_FILE, new byte[600]);
        entries.put("images/" + PHOTO, new byte[600]);

        assertThatThrownBy(() -> limited.restore(encrypted(zipOf(entries)), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.TOO_BIG);
    }

    @Test
    void backupWithoutADatabaseIsRefused() throws IOException {
        Path file = encrypted(zipOf(Map.of("images/" + PHOTO, PHOTO_BYTES)));

        assertThatThrownBy(() -> backup.restore(file, PASSWORD))
                .isInstanceOf(UnreadableBackupException.class)
                .hasMessage(Backup.NO_DATABASE);
        assertThat(pending).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 500})
    void lockedFileThatIsNotAZipIsRefused(int length) throws IOException {
        byte[] notAZip = new byte[length];
        new Random(length).nextBytes(notAZip);

        assertThatThrownBy(() -> backup.restore(encrypted(notAZip), PASSWORD))
                .isInstanceOf(UnreadableBackupException.class);
        assertThat(pending).doesNotExist();
    }

    private Path writtenBackup() {
        Path file = tempDir.resolve("school" + Backup.FILE_EXTENSION);
        backup.write(PASSWORD, PASSWORD, file);
        return file;
    }

    private Path encrypted(byte[] plain) throws IOException {
        return encrypted(plain, currentSchemaVersion);
    }

    /** Locks any bytes the way a real backup is locked, to test what is inside the lock. */
    private Path encrypted(byte[] plain, int schemaVersion) throws IOException {
        Path file = Files.createTempFile(tempDir, "crafted-", Backup.FILE_EXTENSION);
        try (OutputStream out =
                cipher.encryptingTo(Files.newOutputStream(file), PASSWORD, schemaVersion)) {
            out.write(plain);
        }
        return file;
    }

    private static byte[] zipOf(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    /** A zip "local file header" plus uncompressed content, as the zip format lays it out. */
    private static byte[] storedEntry(String name, byte[] content, long crc) {
        byte[] nameBytes = name.getBytes(UTF_8);
        return ByteBuffer.allocate(30 + nameBytes.length + content.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x04034b50) // the signature every entry starts with
                .putShort((short) 10) // version needed to unpack
                .putShort((short) 0) // flags
                .putShort((short) ZipEntry.STORED)
                .putShort((short) 0) // time
                .putShort((short) 0x21) // date: 1 January 1980
                .putInt((int) crc)
                .putInt(content.length) // compressed size
                .putInt(content.length) // size
                .putShort((short) nameBytes.length)
                .putShort((short) 0) // extra field length
                .put(nameBytes)
                .put(content)
                .array();
    }

    private static long crcOf(byte[] content) {
        CRC32 crc = new CRC32();
        crc.update(content);
        return crc.getValue();
    }

    private static void writePhoto(Path folder, String name) throws IOException {
        Files.createDirectories(folder.resolve("images"));
        Files.write(folder.resolve("images").resolve(name), PHOTO_BYTES);
    }

    private static String studentCount(int studentNumber) {
        return "SELECT COUNT(*) FROM student WHERE studentNum = " + studentNumber;
    }
}
