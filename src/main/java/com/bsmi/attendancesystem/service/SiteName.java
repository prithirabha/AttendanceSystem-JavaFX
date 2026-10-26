package com.bsmi.attendancesystem.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * The name of this computer's site (for example {@code library} or {@code lab-2}), used to label
 * the attendance it publishes to a shared folder. It is kept on this computer only - in a plain
 * file in the data folder, never in the database - so restoring one site's backup onto another
 * computer does not change who this computer says it is. The {@code ATTENDANCE_SITE} environment
 * variable overrides the saved name.
 */
public final class SiteName {
    public static final String SITE_VARIABLE = "ATTENDANCE_SITE";

    static final String SITE_FILE = "site";
    static final String FALLBACK = "site";
    private static final int MAX_LENGTH = 40;
    // Keeps the name safe to drop straight into a file name on every OS
    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9._-]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("^-+|-+$");
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private SiteName() {}

    /**
     * @return the site name: the {@code ATTENDANCE_SITE} variable, else the name saved in the data
     *     folder, else this computer's host name, else {@code "site"}
     */
    public static String current(Path dataFolder) {
        return resolve(System.getenv(SITE_VARIABLE), readSaved(dataFolder), hostName());
    }

    /** @return true when no site name is set yet, so the UI should ask for one */
    public static boolean isUnset(Path dataFolder) {
        return sanitize(System.getenv(SITE_VARIABLE)).isEmpty()
                && sanitize(readSaved(dataFolder)).isEmpty();
    }

    /** Saves the site name for next time; the {@code ATTENDANCE_SITE} variable still wins over it. */
    public static void save(Path dataFolder, String name) {
        try {
            Files.writeString(dataFolder.resolve(SITE_FILE), sanitize(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the site name", e);
        }
    }

    /** @return a file name like {@code attendance-library-20260102-030405.csv} */
    public static String fileName(String site, LocalDateTime when) {
        return "attendance-" + sanitize(site) + "-" + when.format(TIMESTAMP) + AttendanceCsv.FILE_EXTENSION;
    }

    /** Picks the first non-empty of the candidates once cleaned, falling back to {@code "site"}. */
    static String resolve(String fromEnvironment, String saved, String hostName) {
        for (String candidate : new String[] {fromEnvironment, saved, hostName}) {
            String clean = sanitize(candidate);
            if (!clean.isEmpty()) {
                return clean;
            }
        }
        return FALLBACK;
    }

    /** Trims, replaces anything unsafe for a file name with a dash, and caps the length. */
    static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String clean = EDGE_DASHES.matcher(UNSAFE.matcher(raw.strip()).replaceAll("-")).replaceAll("");
        return clean.length() > MAX_LENGTH ? clean.substring(0, MAX_LENGTH) : clean;
    }

    private static String readSaved(Path dataFolder) {
        Path file = dataFolder.resolve(SITE_FILE);
        try {
            return Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException cannotRead) {
            return "";
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception noHostName) {
            return "";
        }
    }
}
