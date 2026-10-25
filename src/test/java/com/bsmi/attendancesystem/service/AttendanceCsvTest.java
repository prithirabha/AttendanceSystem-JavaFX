package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AttendanceCsvTest {
    private static final String HEADER_LINE =
            "Student#,Semester,Course,Entry Time,Exit Time,Status\r\n";

    @TempDir Path tempDir;

    @Test
    void writesAHeaderThenOneCrlfLinePerRow() throws IOException {
        List<StudentAttendanceData> rows =
                List.of(
                        row("1001", "2026-10-25 09:00:00", "2026-10-25 15:30:00", "Present"),
                        row("1002", "2026-10-25 09:05:00", null, "Present"));

        assertThat(csvOf(rows))
                .isEqualTo(
                        HEADER_LINE
                                + "1001,First Year,BCA,2026-10-25 09:00:00,2026-10-25 15:30:00,"
                                + "Present\r\n"
                                + "1002,First Year,BCA,2026-10-25 09:05:00,,Present\r\n");
    }

    @Test
    void noRowsGivesTheHeaderOnly() throws IOException {
        assertThat(csvOf(List.of())).isEqualTo(HEADER_LINE);
    }

    @Test
    void cellWithACommaIsQuoted() {
        assertThat(AttendanceCsv.cell("BCA, evening")).isEqualTo("\"BCA, evening\"");
    }

    @Test
    void quoteInsideACellIsDoubled() {
        assertThat(AttendanceCsv.cell("the \"A\" batch")).isEqualTo("\"the \"\"A\"\" batch\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"line\nbreak", "line\r\nbreak"})
    void cellWithALineBreakIsQuoted(String value) {
        assertThat(AttendanceCsv.cell(value)).isEqualTo('"' + value + '"');
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\t=1+1"})
    void cellThatASpreadsheetWouldRunGetsAQuoteInFront(String formula) {
        assertThat(AttendanceCsv.cell(formula)).isEqualTo("'" + formula);
    }

    @Test
    void formulaStartingWithACarriageReturnIsAlsoQuotedForTheLineBreak() {
        assertThat(AttendanceCsv.cell("\r=1+1")).isEqualTo("\"'\r=1+1\"");
    }

    @Test
    void formulaThatAlsoNeedsQuotesGetsBoth() {
        assertThat(AttendanceCsv.cell("=HYPERLINK(\"http://x\",\"y\")"))
                .isEqualTo("\"'=HYPERLINK(\"\"http://x\"\",\"\"y\"\")\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"10-01", "a=b", "x@y", "1+1"})
    void formulaCharacterAfterTheStartIsLeftAlone(String value) {
        assertThat(AttendanceCsv.cell(value)).isEqualTo(value);
    }

    @Test
    void missingValueIsAnEmptyCell() {
        assertThat(AttendanceCsv.cell(null)).isEmpty();
        assertThat(AttendanceCsv.cell("")).isEmpty();
    }

    @Test
    void savedFileIsUtf8WithAByteOrderMarkForExcel() throws IOException {
        Path file = tempDir.resolve("attendance.csv");
        StudentAttendanceData named = new StudentAttendanceData(
                "1001", "First Year", "Bengali বাংলা", "2026-10-25 09:00:00", null, "Present");

        AttendanceCsv.save(List.of(named), file);

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(saved).startsWith(AttendanceCsv.BYTE_ORDER_MARK + HEADER_LINE);
        assertThat(saved).contains("Bengali বাংলা");
    }

    @Test
    void savingReplacesAnOlderFile() throws IOException {
        Path file = tempDir.resolve("attendance.csv");
        Files.writeString(file, "old content that is longer than the new file will be".repeat(9));

        AttendanceCsv.save(List.of(), file);

        assertThat(Files.readString(file)).isEqualTo(AttendanceCsv.BYTE_ORDER_MARK + HEADER_LINE);
    }

    @Test
    void savingIntoAMissingFolderFailsWithUncheckedIo() {
        Path file = tempDir.resolve("no-such-folder").resolve("attendance.csv");

        assertThatThrownBy(() -> AttendanceCsv.save(List.of(), file))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Could not save the attendance to " + file);
        assertThat(Files.exists(file)).isFalse();
    }

    @Test
    void writerThatFailsPassesTheErrorOn() {
        Writer broken = new Writer() {
            @Override
            public void write(char[] chars, int offset, int length) throws IOException {
                throw new IOException("disk full");
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };

        assertThatThrownBy(() -> AttendanceCsv.write(List.of(), broken))
                .isInstanceOf(IOException.class)
                .hasMessage("disk full");
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance", "attendance.txt"})
    void csvIsAddedWhenTheNameLacksIt(String name) {
        assertThat(AttendanceCsv.withCsvExtension(tempDir.resolve(name)))
                .isEqualTo(tempDir.resolve(name + ".csv"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"attendance.csv", "ATTENDANCE.CSV"})
    void fileNameEndingInCsvIsKept(String name) {
        assertThat(AttendanceCsv.withCsvExtension(tempDir.resolve(name)))
                .isEqualTo(tempDir.resolve(name));
    }

    private static StudentAttendanceData row(
            String studentNum, String entryTime, String exitTime, String status) {
        return new StudentAttendanceData(
                studentNum, "First Year", "BCA", entryTime, exitTime, status);
    }

    private static String csvOf(List<StudentAttendanceData> rows) throws IOException {
        StringWriter out = new StringWriter();
        AttendanceCsv.write(rows, out);
        return out.toString();
    }
}
