package com.bsmi.attendancesystem.service;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Writes attendance rows as a CSV file that Excel and LibreOffice open as a table: UTF-8, CRLF
 * line ends and quoting as in RFC 4180. A cell that a spreadsheet would run as a formula gets a
 * {@code '} in front, so a student number like {@code =HYPERLINK(...)} shows as plain text.
 */
public final class AttendanceCsv {
    public static final String FILE_EXTENSION = ".csv";

    static final List<String> HEADER =
            List.of("Student#", "Semester", "Course", "Entry Time", "Exit Time", "Status");
    static final String LINE_END = "\r\n";
    // Tells Excel the file is UTF-8; without it Excel garbles names with non-English letters
    static final char BYTE_ORDER_MARK = '\uFEFF';

    private static final String FORMULA_STARTS = "=+-@\t\r";
    private static final String CHARACTERS_THAT_NEED_QUOTES = ",\"\r\n";

    private AttendanceCsv() {}

    /**
     * Saves the rows to {@code file}, replacing it.
     *
     * @throws UncheckedIOException if the file can't be written
     */
    public static void save(List<StudentAttendanceData> rows, Path file) {
        try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(BYTE_ORDER_MARK);
            write(rows, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the attendance to " + file, e);
        }
    }

    /** The chosen file name with {@code .csv} added, as some save dialogs leave it off. */
    public static Path withCsvExtension(Path file) {
        String name = file.getFileName().toString();
        return name.toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION)
                ? file
                : file.resolveSibling(name + FILE_EXTENSION);
    }

    /** Writes a header line, then one line per row. */
    static void write(List<StudentAttendanceData> rows, Writer out) throws IOException {
        writeLine(HEADER.stream(), out);
        for (StudentAttendanceData row : rows) {
            writeLine(
                    Stream.of(
                            row.getStudentNum(),
                            row.getSemester(),
                            row.getCourse(),
                            row.getEntryTime(),
                            row.getExitTime(),
                            row.getStatus()),
                    out);
        }
    }

    private static void writeLine(Stream<String> cells, Writer out) throws IOException {
        out.write(cells.map(AttendanceCsv::cell).collect(Collectors.joining(",")));
        out.write(LINE_END);
    }

    static String cell(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String text = FORMULA_STARTS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes =
                text.chars().anyMatch(c -> CHARACTERS_THAT_NEED_QUOTES.indexOf(c) >= 0);
        return needsQuotes ? '"' + text.replace("\"", "\"\"") + '"' : text;
    }
}
