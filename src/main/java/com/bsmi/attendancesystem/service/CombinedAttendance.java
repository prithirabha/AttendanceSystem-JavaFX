package com.bsmi.attendancesystem.service;

import com.bsmi.attendancesystem.StudentAttendanceData;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Reads the attendance several sites publish into one shared folder (see {@link SiteName}). Each
 * site's files are named {@code attendance-<site>-<timestamp>.csv}; this keeps only the newest
 * file per site and reads it, so the combined view shows each site's latest snapshot. It reads
 * rows through {@link AttendanceCsvReader} and never opens another computer's database, so a
 * shared file cannot run code here.
 */
public final class CombinedAttendance {
    // attendance-<site>-<yyyyMMdd-HHmmss>.csv; the site may itself contain dashes
    private static final Pattern SHARED_FILE =
            Pattern.compile("attendance-(.+)-(\\d{8}-\\d{6})\\.csv");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private CombinedAttendance() {}

    /**
     * @return the newest file per site in {@code folder}, read into rows and sorted by site name
     * @throws UncheckedIOException if the folder can't be listed
     */
    public static List<Site> readFolder(Path folder) {
        Map<String, Path> newestFile = new HashMap<>();
        Map<String, LocalDateTime> newestWhen = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile)
                    .forEach(file -> keepNewest(file, newestFile, newestWhen));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the shared folder " + folder, e);
        }
        List<Site> sites = new ArrayList<>();
        for (String name : newestFile.keySet().stream().sorted().toList()) {
            sites.add(
                    new Site(
                            name,
                            newestWhen.get(name),
                            AttendanceCsvReader.read(newestFile.get(name))));
        }
        return sites;
    }

    private static void keepNewest(
            Path file, Map<String, Path> newestFile, Map<String, LocalDateTime> newestWhen) {
        Matcher match = SHARED_FILE.matcher(file.getFileName().toString());
        if (!match.matches()) {
            return;
        }
        String site = match.group(1);
        LocalDateTime when;
        try {
            when = LocalDateTime.parse(match.group(2), TIMESTAMP);
        } catch (DateTimeParseException notATimestamp) {
            return;
        }
        if (!newestWhen.containsKey(site) || when.isAfter(newestWhen.get(site))) {
            newestWhen.put(site, when);
            newestFile.put(site, file);
        }
    }

    /** One site's latest published attendance. */
    public record Site(String name, LocalDateTime exportedAt, List<StudentAttendanceData> rows) {}
}
