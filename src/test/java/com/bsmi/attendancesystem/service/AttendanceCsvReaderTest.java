package com.bsmi.attendancesystem.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AttendanceCsvReaderTest {
    @TempDir Path dir;

    @Test
    void roundTripsRowsThroughAFile() {
        List<StudentAttendanceData> rows =
                List.of(
                        row("1001", "3rd Sem", "BCA", "09:00", "15:00", "Present"),
                        row("1002", "1st Sem", "BSc", "", "", "Absent"));
        Path file = dir.resolve("attendance-lab-20260102-030405.csv");
        AttendanceCsv.save(rows, file);

        List<StudentAttendanceData> read = AttendanceCsvReader.read(file);

        assertThat(read)
                .extracting(
                        StudentAttendanceData::getStudentNum,
                        StudentAttendanceData::getCourse,
                        StudentAttendanceData::getEntryTime,
                        StudentAttendanceData::getStatus)
                .containsExactly(
                        tuple("1001", "BCA", "09:00", "Present"),
                        tuple("1002", "BSc", "", "Absent"));
    }

    @Test
    void keepsCommasQuotesAndNewlinesInsideACell() throws IOException {
        StringWriter out = new StringWriter();
        AttendanceCsv.write(List.of(row("1,2", "a\"b", "c\nd", "e", "f", "g")), out);

        StudentAttendanceData parsed = AttendanceCsvReader.parse(out.toString()).get(0);

        assertThat(parsed.getStudentNum()).isEqualTo("1,2");
        assertThat(parsed.getSemester()).isEqualTo("a\"b");
        assertThat(parsed.getCourse()).isEqualTo("c\nd");
    }

    @Test
    void stripsTheFormulaGuardBackOff() throws IOException {
        StringWriter out = new StringWriter();
        AttendanceCsv.write(List.of(row("=1+1", "+x", "-y", "@z", "normal", "'kept")), out);

        StudentAttendanceData parsed = AttendanceCsvReader.parse(out.toString()).get(0);

        assertThat(parsed.getStudentNum()).isEqualTo("=1+1");
        assertThat(parsed.getSemester()).isEqualTo("+x");
        assertThat(parsed.getCourse()).isEqualTo("-y");
        assertThat(parsed.getEntryTime()).isEqualTo("@z");
        assertThat(parsed.getExitTime()).isEqualTo("normal");
        assertThat(parsed.getStatus()).isEqualTo("'kept");
    }

    @Test
    void aShortRowIsPaddedRatherThanCrashing() {
        String text = "Student#,Semester,Course,Entry Time,Exit Time,Status\r\n1001,3rd Sem\r\n";

        StudentAttendanceData parsed = AttendanceCsvReader.parse(text).get(0);

        assertThat(parsed.getStudentNum()).isEqualTo("1001");
        assertThat(parsed.getSemester()).isEqualTo("3rd Sem");
        assertThat(parsed.getCourse()).isEmpty();
        assertThat(parsed.getStatus()).isEmpty();
    }

    @Test
    void skipsTheHeaderAByteOrderMarkAndBlankLines() {
        String text =
                "﻿Student#,Semester,Course,Entry Time,Exit Time,Status\r\n"
                        + "\r\n"
                        + "1001,A,B,C,D,Present\r\n";

        List<StudentAttendanceData> rows = AttendanceCsvReader.parse(text);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo("Present");
    }

    @Test
    void anEmptyTextGivesNoRows() {
        assertThat(AttendanceCsvReader.parse("")).isEmpty();
    }

    private static StudentAttendanceData row(
            String studentNum,
            String semester,
            String course,
            String entry,
            String exit,
            String status) {
        return new StudentAttendanceData(studentNum, semester, course, entry, exit, status);
    }
}
