package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Path;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SiteNameTest {
    @TempDir Path dataFolder;

    @Test
    void environmentWinsOverSavedAndHostName() {
        assertThat(SiteName.resolve("library", "lab", "host")).isEqualTo("library");
    }

    @Test
    void savedNameIsUsedWhenThereIsNoEnvironmentValue() {
        assertThat(SiteName.resolve(null, "lab-2", "host")).isEqualTo("lab-2");
    }

    @Test
    void hostNameIsTheLastResortBeforeTheFallback() {
        assertThat(SiteName.resolve("  ", "", "front-desk")).isEqualTo("front-desk");
        assertThat(SiteName.resolve(null, null, null)).isEqualTo(SiteName.FALLBACK);
    }

    @Test
    void sanitizeMakesANameSafeForAFileName() {
        assertThat(SiteName.sanitize("  Library / Room 3  ")).isEqualTo("Library-Room-3");
        assertThat(SiteName.sanitize("---lab---")).isEqualTo("lab");
        assertThat(SiteName.sanitize("a".repeat(100))).hasSize(40);
        assertThat(SiteName.sanitize(null)).isEmpty();
    }

    @Test
    void theFileNameHoldsTheSiteAndTheTimestamp() {
        String name = SiteName.fileName("lab-2", LocalDateTime.of(2026, 1, 2, 3, 4, 5));

        assertThat(name).isEqualTo("attendance-lab-2-20260102-030405.csv");
    }

    @Test
    void aSavedNameIsCleanedAndReadBack() {
        assumeTrue(System.getenv(SiteName.SITE_VARIABLE) == null, "ATTENDANCE_SITE is set");
        assertThat(SiteName.isUnset(dataFolder)).isTrue();

        SiteName.save(dataFolder, "Main Hall");

        assertThat(SiteName.isUnset(dataFolder)).isFalse();
        assertThat(SiteName.current(dataFolder)).isEqualTo("Main-Hall");
    }
}
