package com.bsmi.attendancesystem.service;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a CSV written by {@link AttendanceCsv} back into attendance rows, for the combined
 * multi-site view. It follows RFC 4180 quoting, tolerates either line ending and a leading byte
 * order mark, and undoes the {@code '} that {@link AttendanceCsv} adds in front of a cell a
 * spreadsheet would treat as a formula. Short or malformed rows are padded, never a crash, because
 * the file comes from another computer.
 */
public final class AttendanceCsvReader {
    private static final char BYTE_ORDER_MARK = '﻿';

    private AttendanceCsvReader() {}

    /**
     * @throws UncheckedIOException if the file can't be read
     */
    public static List<StudentAttendanceData> read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the attendance file " + file, e);
        }
    }

    /** Parses CSV text into rows, skipping the header line. */
    static List<StudentAttendanceData> parse(String text) {
        List<List<String>> records = records(stripByteOrderMark(text));
        List<StudentAttendanceData> rows = new ArrayList<>();
        for (int line = 1; line < records.size(); line++) {
            List<String> fields = records.get(line);
            rows.add(
                    new StudentAttendanceData(
                            field(fields, 0),
                            field(fields, 1),
                            field(fields, 2),
                            field(fields, 3),
                            field(fields, 4),
                            field(fields, 5)));
        }
        return rows;
    }

    private static String field(List<String> fields, int index) {
        return index < fields.size() ? unguard(fields.get(index)) : "";
    }

    /** Reverses {@link AttendanceCsv}'s guard: a {@code '} put before a formula-starting cell. */
    private static String unguard(String cell) {
        return cell.length() >= 2
                        && cell.charAt(0) == '\''
                        && AttendanceCsv.FORMULA_STARTS.indexOf(cell.charAt(1)) >= 0
                ? cell.substring(1)
                : cell;
    }

    private static String stripByteOrderMark(String text) {
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

    /** Splits RFC 4180 text into records of fields, honouring quotes and quoted newlines. */
    private static List<List<String>> records(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean started = false; // saw any character or separator on this record
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                started = true;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                started = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (started || field.length() > 0 || !record.isEmpty()) {
                    record.add(field.toString());
                    records.add(record);
                    record = new ArrayList<>();
                    field.setLength(0);
                }
                started = false;
            } else {
                field.append(c);
                started = true;
            }
        }
        if (started || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            records.add(record);
        }
        return records;
    }
}
