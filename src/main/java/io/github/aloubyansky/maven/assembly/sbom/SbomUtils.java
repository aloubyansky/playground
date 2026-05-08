package io.github.aloubyansky.maven.assembly.sbom;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared utilities for the SBOM generation pipeline.
 */
final class SbomUtils {

    private static final Logger log = LoggerFactory.getLogger(SbomUtils.class);

    private static final int HASH_BUFFER_SIZE = 8192;
    private static final HexFormat HEX = HexFormat.of();

    private SbomUtils() {
    }

    /**
     * Loads the SBOM generator's Maven coordinates from its embedded
     * {@code pom.properties} resource.
     *
     * @return the loaded properties, or an empty {@link Properties}
     *         instance if the resource is missing or unreadable
     */
    static Properties loadToolProperties() {
        Properties props = new Properties();
        try (InputStream is = SbomUtils.class.getResourceAsStream(
                "/META-INF/maven/io.github.aloubyansky.maven.assembly.sbom"
                        + "/assembly-cyclonedx-generator/pom.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ignored) {
        }
        return props;
    }

    /**
     * Parses the {@code project.build.outputTimestamp} property into a
     * {@link Date} for reproducible builds.
     *
     * <p>
     * Handles three formats used by Maven:
     * </p>
     * <ul>
     * <li>ISO-8601 instant: {@code "2024-01-15T10:30:00Z"}</li>
     * <li>Epoch seconds: {@code "1705312200"}</li>
     * <li>Date only: {@code "2024-01-15"} (interpreted as midnight UTC)</li>
     * </ul>
     *
     * <p>
     * Returns {@code null} when the value is {@code null}, blank,
     * unparseable, or {@code "0"} (which Maven uses to disable
     * reproducible builds).
     * </p>
     *
     * @param value the raw property value
     * @return the parsed timestamp, or {@code null}
     */
    static Date parseBuildTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Instant instant = tryParseEpochSeconds(value);
        if (instant == null) {
            instant = tryParseIso8601(value);
        }
        if (instant == null) {
            instant = tryParseDateOnly(value);
        }
        if (instant == null) {
            log.debug("Could not parse project.build.outputTimestamp '{}', using current time",
                    value);
        }
        return instant != null ? Date.from(instant) : null;
    }

    /**
     * Tries to parse the value as epoch seconds (all-digit string).
     * Returns {@code null} for {@code "0"} (reproducibility disabled).
     */
    private static Instant tryParseEpochSeconds(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return null;
            }
        }
        try {
            long epoch = Long.parseLong(value);
            return epoch == 0 ? null : Instant.ofEpochSecond(epoch);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Tries to parse the value as an ISO-8601 instant.
     */
    private static Instant tryParseIso8601(String value) {
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Tries to parse the value as a date-only string ({@code yyyy-MM-dd}),
     * interpreting it as midnight UTC.
     */
    private static Instant tryParseDateOnly(String value) {
        try {
            return LocalDate.parse(value).atStartOfDay(ZoneOffset.UTC).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /**
     * Computes the hex-encoded hash of the data read from the given
     * stream using the provided digest. The digest is reset before use.
     * The stream is read to completion but not closed.
     *
     * @param digest the message digest to use
     * @param inputStream the data source
     * @return the lowercase hex-encoded hash
     * @throws IOException if reading fails
     */
    static String computeHash(MessageDigest digest, InputStream inputStream) throws IOException {
        digest.reset();
        byte[] buf = new byte[HASH_BUFFER_SIZE];
        int n;
        while ((n = inputStream.read(buf)) != -1) {
            digest.update(buf, 0, n);
        }
        return HEX.formatHex(digest.digest());
    }

    /**
     * Computes the hex-encoded hash of the file at the given path.
     *
     * @param digest the message digest to use
     * @param path the file to hash
     * @return the lowercase hex-encoded hash
     * @throws IOException if the file cannot be read
     */
    static String computeHash(MessageDigest digest, Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return computeHash(digest, is);
        }
    }
}
