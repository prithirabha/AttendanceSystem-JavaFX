package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CombinedAttendanceTest {
    @TempDir Path folder;

    @Test
    void keepsTheNewestFilePerSiteSortedByName() {
        writeShared("lab-2", "20260102-030405", "1002");
        writeShared("lab-2", "20260103-090000", "2002"); // newer wins
        writeShared("library", "20260101-120000", "1001");

        List<CombinedAttendance.Site> sites = CombinedAttendance.readFolder(folder);

        assertThat(sites)
                .extracting(CombinedAttendance.Site::name)
                .containsExactly("lab-2", "library");
        assertThat(sites.get(0).exportedAt()).isEqualTo(LocalDateTime.of(2026, 1, 3, 9, 0, 0));
        assertThat(sites.get(0).rows())
                .extracting(StudentAttendanceData::getStudentNum)
                .containsExactly("2002");
        assertThat(sites.get(1).rows())
                .extracting(StudentAttendanceData::getStudentNum)
                .containsExactly("1001");
    }

    @Test
    void anEmptyFolderGivesNoSites() {
        assertThat(CombinedAttendance.readFolder(folder)).isEmpty();
    }

    @Test
    void filesThatAreNotSharedAttendanceAreIgnored() throws IOException {
        Files.writeString(folder.resolve("attendance-no-timestamp.csv"), "x");
        Files.writeString(folder.resolve("random.txt"), "x");
        writeShared("library", "20260101-120000", "1001");

        List<CombinedAttendance.Site> sites = CombinedAttendance.readFolder(folder);

        assertThat(sites).extracting(CombinedAttendance.Site::name).containsExactly("library");
    }

    private void writeShared(String site, String timestamp, String studentNum) {
        StudentAttendanceData row =
                new StudentAttendanceData(studentNum, "S", "C", "09:00", "15:00", "Present");
        AttendanceCsv.save(
                List.of(row), folder.resolve("attendance-" + site + "-" + timestamp + ".csv"));
    }
}
