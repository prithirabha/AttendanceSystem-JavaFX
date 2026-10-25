package com.bsmi.attendancesystem.service;

import static com.bsmi.attendancesystem.service.PendingRestore.COMPLETE_MARKER;
import static com.bsmi.attendancesystem.service.PendingRestore.DATABASE_FILE;
import static com.bsmi.attendancesystem.service.PendingRestore.IMAGES_FOLDER;
import static com.bsmi.attendancesystem.service.PendingRestore.PENDING_FOLDER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PendingRestoreTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 2, 3, 4, 5);
    private static final String SAFETY_COPY = "before-restore-20260102-030405";

    @TempDir Path dataFolder;

    private Path pending;
    private Path safetyCopy;

    @BeforeEach
    void namePaths() {
        pending = dataFolder.resolve(PENDING_FOLDER);
        safetyCopy = dataFolder.resolve(SAFETY_COPY);
    }

    @Test
    void completeRestoreSwapsInAndKeepsASafetyCopy() throws IOException {
        writeCurrentData();
        writePending(true);

        PendingRestore.applyIfReady(dataFolder, NOW);

        assertThat(dataFolder.resolve(DATABASE_FILE)).hasContent("restored database");
        assertThat(dataFolder.resolve(IMAGES_FOLDER).resolve("new.png")).hasContent("new");
        assertThat(dataFolder.resolve(IMAGES_FOLDER).resolve("old.png")).doesNotExist();
        assertThat(safetyCopy.resolve(DATABASE_FILE)).hasContent("current database");
        assertThat(safetyCopy.resolve(IMAGES_FOLDER).resolve("old.png")).hasContent("old");
        assertThat(pending).doesNotExist();
    }

    @Test
    void unfinishedRestoreIsDeletedAndTheDataIsLeftAlone() throws IOException {
        writeCurrentData();
        writePending(false);

        PendingRestore.applyIfReady(dataFolder, NOW);

        assertThat(dataFolder.resolve(DATABASE_FILE)).hasContent("current database");
        assertThat(dataFolder.resolve(IMAGES_FOLDER).resolve("old.png")).hasContent("old");
        assertThat(safetyCopy).doesNotExist();
        assertThat(pending).doesNotExist();
    }

    @Test
    void nothingPendingChangesNothing() throws IOException {
        writeCurrentData();

        PendingRestore.applyIfReady(dataFolder);

        try (Stream<Path> files = Files.list(dataFolder)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactlyInAnyOrder(DATABASE_FILE, IMAGES_FOLDER);
        }
    }

    @Test
    void restoreOntoAnEmptyDataFolderNeedsNoSafetyCopy() throws IOException {
        writePending(true);

        PendingRestore.applyIfReady(dataFolder, NOW);

        assertThat(dataFolder.resolve(DATABASE_FILE)).hasContent("restored database");
        assertThat(safetyCopy).doesNotExist();
    }

    @Test
    void restoreInterruptedHalfwayFinishesOnTheNextStart() throws IOException {
        writeCurrentData();
        writePending(true);
        // The earlier start crashed after moving the database, before the photos
        Files.createDirectories(safetyCopy);
        Files.move(dataFolder.resolve(DATABASE_FILE), safetyCopy.resolve(DATABASE_FILE));
        Files.move(pending.resolve(DATABASE_FILE), dataFolder.resolve(DATABASE_FILE));

        PendingRestore.applyIfReady(dataFolder, NOW);

        assertThat(dataFolder.resolve(DATABASE_FILE)).hasContent("restored database");
        assertThat(dataFolder.resolve(IMAGES_FOLDER).resolve("new.png")).hasContent("new");
        assertThat(safetyCopy.resolve(DATABASE_FILE)).hasContent("current database");
        assertThat(safetyCopy.resolve(IMAGES_FOLDER).resolve("old.png")).hasContent("old");
        assertThat(pending).doesNotExist();
    }

    @Test
    void filesThatCannotBeMovedAreAClearError() throws IOException {
        writeCurrentData();
        writePending(true);
        // A file where the safety copy folder should go stops the move
        Files.writeString(safetyCopy, "in the way");

        assertThatThrownBy(() -> PendingRestore.applyIfReady(dataFolder, NOW))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessageContaining("Could not finish restoring the backup");
        assertThat(dataFolder.resolve(DATABASE_FILE)).hasContent("current database");
        assertThat(pending.resolve(COMPLETE_MARKER)).exists();
    }

    @Test
    void deletingAMissingFolderDoesNothing() throws IOException {
        PendingRestore.deleteTree(dataFolder.resolve("missing"));

        assertThat(dataFolder.resolve("missing")).doesNotExist();
    }

    private void writeCurrentData() throws IOException {
        Files.writeString(dataFolder.resolve(DATABASE_FILE), "current database");
        Files.createDirectories(dataFolder.resolve(IMAGES_FOLDER));
        Files.writeString(dataFolder.resolve(IMAGES_FOLDER).resolve("old.png"), "old");
    }

    private void writePending(boolean complete) throws IOException {
        Files.createDirectories(pending.resolve(IMAGES_FOLDER));
        Files.writeString(pending.resolve(DATABASE_FILE), "restored database");
        Files.writeString(pending.resolve(IMAGES_FOLDER).resolve("new.png"), "new");
        if (complete) {
            Files.createFile(pending.resolve(COMPLETE_MARKER));
        }
    }
}
