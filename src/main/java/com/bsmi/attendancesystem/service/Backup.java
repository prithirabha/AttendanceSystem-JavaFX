package com.bsmi.attendancesystem.service;

import static com.bsmi.attendancesystem.service.Fields.isStrongPassword;
import static com.bsmi.attendancesystem.service.Fields.requireFilled;
import static com.bsmi.attendancesystem.service.PendingRestore.COMPLETE_MARKER;
import static com.bsmi.attendancesystem.service.PendingRestore.DATABASE_FILE;
import static com.bsmi.attendancesystem.service.PendingRestore.IMAGES_FOLDER;
import static com.bsmi.attendancesystem.service.PendingRestore.PENDING_FOLDER;

import com.bsmi.attendancesystem.DatabaseSettings;
import com.bsmi.attendancesystem.dao.BackupDao;
import com.bsmi.attendancesystem.dao.DataAccessException;
import com.bsmi.attendancesystem.dao.Database;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Backs up the built-in database and the student photos into one password-locked file, and
 * restores such a file. Inside the lock (see {@link BackupCipher}) is a zip holding {@code
 * attendance_system.mv.db} and {@code images/<photo>}. A restore only unpacks and checks the file;
 * {@link PendingRestore} swaps it in on the next start, while nothing has the database open.
 */
public final class Backup {
    public static final String FILE_EXTENSION = ".attbackup";

    static final String PASSWORD = "Backup password";
    static final String REPEATED_PASSWORD = "Repeated password";
    // Longer than a login password, because a stolen backup file can be guessed at offline
    static final int MIN_PASSWORD_LENGTH = 12;
    // Far above any school's data, but stops a small file that unpacks to fill the disk
    static final long DEFAULT_MAX_RESTORED_BYTES = 5L * 1024 * 1024 * 1024;

    static final String SERVER_DATABASE =
            "Back up your database server with its own tools; this button covers only the"
                    + " database built into the app.";
    static final String UNEXPECTED_CONTENT =
            "The backup holds files that are not the app's database or photos, so it was not"
                    + " restored.";
    static final String TOO_BIG =
            "The backup unpacks to far more data than it should, so it was not restored.";
    static final String NO_DATABASE = "The backup has no database in it, so it was not restored.";
    static final String NEWER_DATABASE =
            "This backup is from a newer version of the app. Update the app first, then restore"
                    + " it.";

    private static final System.Logger LOG = System.getLogger(Backup.class.getName());
    private static final String PHOTO_ENTRY_PREFIX = IMAGES_FOLDER + "/";
    // The names ImageStore gives photos; nothing else is backed up or restored
    private static final Pattern PHOTO_NAME =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
                    + "\\.(png|jpg|jpeg)");

    private final Database database;
    private final BackupDao backupDao;
    private final Path dataFolder;
    private final BackupCipher cipher;
    private final long maxRestoredBytes;

    public Backup(Database database, Path dataFolder) {
        this(database, dataFolder, new BackupCipher(), DEFAULT_MAX_RESTORED_BYTES);
    }

    Backup(Database database, Path dataFolder, BackupCipher cipher, long maxRestoredBytes) {
        this.database = database;
        this.backupDao = new BackupDao(database);
        this.dataFolder = dataFolder.toAbsolutePath();
        this.cipher = cipher;
        this.maxRestoredBytes = maxRestoredBytes;
    }

    /** @throws NotAllowedException if the app uses a database server instead of its own file */
    public void requireBuiltInDatabase() {
        if (!database.url().equals(DatabaseSettings.embeddedUrl(dataFolder))) {
            throw new NotAllowedException(SERVER_DATABASE);
        }
    }

    /**
     * Lets the UI check the password before asking where to save.
     *
     * @throws MissingFieldException if the password is empty
     * @throws InvalidValueException if the password is weak or the repeated one differs
     */
    public static void requireStrong(String password, String repeated) {
        requireFilled(password, PASSWORD);
        if (!isStrongPassword(password, MIN_PASSWORD_LENGTH)) {
            throw new InvalidValueException(
                    PASSWORD,
                    "at least "
                            + MIN_PASSWORD_LENGTH
                            + " characters long and contain both uppercase and lowercase"
                            + " letters");
        }
        if (!password.equals(repeated)) {
            throw new InvalidValueException(REPEATED_PASSWORD, "the same as " + PASSWORD);
        }
    }

    /**
     * Writes the backup to {@code target}, replacing it. A failed backup leaves no file behind.
     *
     * @throws ValidationException if the database is a server or the password is weak
     * @throws DataAccessException if the database can't be copied
     * @throws UncheckedIOException if the file can't be written
     */
    public void write(String password, String repeated, Path target) {
        requireBuiltInDatabase();
        requireStrong(password, repeated);
        Path databaseCopy = null;
        Path partial = null;
        try {
            // H2's copy isn't locked yet, so it stays in the data folder beside the database
            databaseCopy = Files.createTempFile(dataFolder, "backup-", ".zip");
            Files.delete(databaseCopy);
            backupDao.backupTo(databaseCopy);
            partial =
                    Files.createTempFile(
                            target.toAbsolutePath().getParent(), ".attendance-backup-", ".part");
            int schemaVersion = backupDao.schemaVersion();
            try (OutputStream file = new BufferedOutputStream(Files.newOutputStream(partial));
                    ZipOutputStream zip =
                            new ZipOutputStream(
                                    cipher.encryptingTo(file, password, schemaVersion))) {
                copyDatabaseFile(databaseCopy, zip);
                for (Path photo : photosToBackUp()) {
                    zip.putNextEntry(new ZipEntry(PHOTO_ENTRY_PREFIX + photo.getFileName()));
                    Files.copy(photo, zip);
                    zip.closeEntry();
                }
            }
            Files.move(
                    partial,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the backup to " + target, e);
        } finally {
            deleteQuietly(databaseCopy);
            deleteQuietly(partial);
        }
    }

    /**
     * Unpacks and checks the backup into the pending folder; the next start swaps it in.
     *
     * @throws ValidationException if the database is a server, the password is empty or wrong,
     *     or the file is damaged, holds unexpected files, is too big or is from a newer app
     * @throws DataAccessException if the current database version can't be read
     * @throws UncheckedIOException if the files can't be read or written
     */
    public void restore(Path backupFile, String password) {
        requireBuiltInDatabase();
        requireFilled(password, PASSWORD);
        Path pending = dataFolder.resolve(PENDING_FOLDER);
        try {
            PendingRestore.deleteTree(pending);
            // Always there, so a backup without photos also replaces the current photos
            Files.createDirectories(pending.resolve(IMAGES_FOLDER));
            try (InputStream file = new BufferedInputStream(Files.newInputStream(backupFile));
                    BackupCipher.DecryptingStream plain = cipher.decrypting(file, password)) {
                if (plain.schemaVersion() > backupDao.schemaVersion()) {
                    throw new UnreadableBackupException(NEWER_DATABASE);
                }
                unpack(new ZipInputStream(plain), pending);
                // Reading to the end runs the check on the last chunk, catching a cut-off file
                plain.transferTo(OutputStream.nullOutputStream());
            }
            Files.createFile(pending.resolve(COMPLETE_MARKER));
        } catch (ZipException | EOFException unlockedButNotAZip) {
            discard(pending);
            throw new UnreadableBackupException(
                    UnreadableBackupException.WRONG_PASSWORD_OR_DAMAGED);
        } catch (IOException e) {
            discard(pending);
            throw new UncheckedIOException("Could not restore the backup " + backupFile, e);
        } catch (RuntimeException e) {
            discard(pending);
            throw e;
        }
    }

    private static void copyDatabaseFile(Path databaseCopy, ZipOutputStream zip)
            throws IOException {
        try (ZipInputStream h2Zip = new ZipInputStream(Files.newInputStream(databaseCopy))) {
            for (ZipEntry entry = h2Zip.getNextEntry();
                    entry != null;
                    entry = h2Zip.getNextEntry()) {
                if (entry.getName().equals(DATABASE_FILE)) {
                    zip.putNextEntry(new ZipEntry(DATABASE_FILE));
                    h2Zip.transferTo(zip);
                    zip.closeEntry();
                    return;
                }
            }
        }
        throw new IOException("H2's backup has no " + DATABASE_FILE + " in it");
    }

    private List<Path> photosToBackUp() throws IOException {
        Path images = dataFolder.resolve(IMAGES_FOLDER);
        if (!Files.isDirectory(images)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(images)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> PHOTO_NAME.matcher(file.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        }
    }

    private void unpack(ZipInputStream zip, Path pending) throws IOException {
        Set<String> unpacked = new HashSet<>();
        long bytesLeft = maxRestoredBytes;
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String name = entry.getName();
            if (name.equals(PHOTO_ENTRY_PREFIX)) {
                continue;
            }
            // Also blocks "zip slip": names like "../x" or "/etc/x" that would land elsewhere
            if (!isDatabaseOrPhoto(name) || !unpacked.add(name)) {
                throw new UnreadableBackupException(UNEXPECTED_CONTENT);
            }
            bytesLeft -= copyAtMost(zip, pending.resolve(name), bytesLeft);
        }
        if (!unpacked.contains(DATABASE_FILE)) {
            throw new UnreadableBackupException(NO_DATABASE);
        }
    }

    private static boolean isDatabaseOrPhoto(String entryName) {
        return entryName.equals(DATABASE_FILE)
                || entryName.startsWith(PHOTO_ENTRY_PREFIX)
                        && PHOTO_NAME
                                .matcher(entryName.substring(PHOTO_ENTRY_PREFIX.length()))
                                .matches();
    }

    /** @return how many bytes were copied */
    private static long copyAtMost(InputStream in, Path target, long limit) throws IOException {
        long copied = 0;
        try (OutputStream out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
                copied += read;
                if (copied > limit) {
                    throw new UnreadableBackupException(TOO_BIG);
                }
                out.write(buffer, 0, read);
            }
        }
        return copied;
    }

    private static void discard(Path pending) {
        try {
            PendingRestore.deleteTree(pending);
        } catch (IOException e) {
            // The next start deletes it anyway, because it has no complete marker
            LOG.log(System.Logger.Level.WARNING, "Could not delete " + pending, e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not delete " + file, e);
        }
    }
}
