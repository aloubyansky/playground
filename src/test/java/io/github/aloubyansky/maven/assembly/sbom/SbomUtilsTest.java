package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SbomUtilsTest {

    @TempDir
    Path tempDir;

    // ---- loadToolProperties ----

    @Test
    void loadToolProperties_returnsNonNull() {
        Properties props = SbomUtils.loadToolProperties();
        assertNotNull(props);
    }

    // ---- parseBuildTimestamp ----

    @Test
    void parseBuildTimestamp_null_returnsNull() {
        assertNull(SbomUtils.parseBuildTimestamp(null));
    }

    @Test
    void parseBuildTimestamp_blank_returnsNull() {
        assertNull(SbomUtils.parseBuildTimestamp("  "));
    }

    @Test
    void parseBuildTimestamp_iso8601() {
        Date result = SbomUtils.parseBuildTimestamp("2024-01-15T10:30:00Z");
        assertNotNull(result);
        assertEquals(1705314600000L, result.getTime());
    }

    @Test
    void parseBuildTimestamp_epochSeconds() {
        Date result = SbomUtils.parseBuildTimestamp("1705314600");
        assertNotNull(result);
        assertEquals(1705314600000L, result.getTime());
    }

    @Test
    void parseBuildTimestamp_dateOnly() {
        Date result = SbomUtils.parseBuildTimestamp("2024-01-15");
        assertNotNull(result);
        assertEquals(1705276800000L, result.getTime());
    }

    @Test
    void parseBuildTimestamp_zero_returnsNull() {
        assertNull(SbomUtils.parseBuildTimestamp("0"));
    }

    @Test
    void parseBuildTimestamp_invalid_returnsNull() {
        assertNull(SbomUtils.parseBuildTimestamp("not-a-date"));
    }

    // ---- computeHash ----

    @Test
    void computeHash_stream() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        String hash = SbomUtils.computeHash(digest, new ByteArrayInputStream(content));

        assertNotNull(hash);
        assertEquals(64, hash.length());
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash);
    }

    @Test
    void computeHash_path() throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String hash = SbomUtils.computeHash(digest, file);
        assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash);
    }

}
