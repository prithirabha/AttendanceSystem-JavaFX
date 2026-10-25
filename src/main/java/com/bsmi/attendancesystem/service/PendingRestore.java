package com.bsmi.attendancesystem.service;

import com.bsmi.attendancesystem.DatabaseSettings;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Swaps a restored backup in when the app starts, before anything opens the database.
 *
 * <p>{@link Backup#restore} unpacks into {@code restore-pending/} and writes the {@code complete}
 * marker last. Here the current database and photos move into {@code before-restore-<time>/},
 * a safety copy that is never deleted automatically, and the restored ones move into their place.
 * Each step can run again after a crash halfway, so a restore always finishes on the next start.
 */
public final class PendingRestore {
    static final String PENDING_FOLDER = "restore-pending";
    static final String COMPLETE_MARKER = "complete";
    static final String DATABASE_FILE = DatabaseSettings.DATABASE_FILE_NAME + ".mv.db";
    static final String IMAGES_FOLDER = "images";
    static final String SAFETY_COPY_PREFIX = "before-restore-";

    private static final DateTimeFormatter SAFETY_COPY_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private PendingRestore() {}

    /**
     * Finishes a restore if one is waiting; deletes an unfinished one without touching the data.
     *
     * @throws UncheckedIOException if the files can't be moved
     */
    public static void applyIfReady(Path dataFolder) {
        applyIfReady(dataFolder, LocalDateTime.now());
    }

    static void applyIfReady(Path dataFolder, LocalDateTime now) {
        Path pending = dataFolder.resolve(PENDING_FOLDER);
        if (!Files.isDirectory(pending)) {
            return;
        }
        try {
            Path marker = pending.resolve(COMPLETE_MARKER);
            if (Files.exists(marker)) {
                Path safetyCopy =
                        dataFolder.resolve(SAFETY_COPY_PREFIX + SAFETY_COPY_TIME.format(now));
                for (String item : List.of(DATABASE_FILE, IMAGES_FOLDER)) {
                    swapIn(pending.resolve(item), dataFolder.resolve(item), safetyCopy);
                }
                Files.delete(marker);
            }
            deleteTree(pending);
        } catch (IOException e) {
            throw new UncheckedIOException(
                    "Could not finish restoring the backup in " + pending, e);
        }
    }

    /** Leaves out an item an earlier, interrupted start already moved. */
    private static void swapIn(Path restored, Path current, Path safetyCopy) throws IOException {
        if (!Files.exists(restored)) {
            return;
        }
        if (Files.exists(current)) {
            Files.createDirectories(safetyCopy);
            Files.move(current, safetyCopy.resolve(current.getFileName()));
        }
        Files.move(restored, current);
    }

    static void deleteTree(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            // Deepest first, so each folder is empty when its turn comes
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }
}
