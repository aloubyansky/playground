package io.github.aloubyansky.pqc.maven.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HybridVerifier} and {@link VerificationReport}.
 * <p>
 * These tests focus on the VerificationReport logic without requiring external
 * tools like gpg or sq. The tests verify:
 * <ul>
 *   <li>Strict pass requires both classic and PQC signatures to pass</li>
 *   <li>Transitional pass requires only classic signature to pass</li>
 *   <li>Report formatting handles various result combinations</li>
 *   <li>Special cases like NOT_PRESENT and NO_KEY are handled correctly</li>
 * </ul>
 * </p>
 */
class HybridVerifierTest {

    /**
     * Tests that when both classic and PQC signatures pass, the report indicates
     * both strict and transitional pass.
     */
    @Test
    void report_bothPass() {
        VerificationReport report = new VerificationReport(
            VerificationResult.PASS,
            "0xABCD1234",
            VerificationResult.PASS,
            "ML-DSA-65+Ed25519",
            "abc123def456"
        );

        assertTrue(report.isStrictPass(),
            "Strict pass should be true when both signatures pass");
        assertTrue(report.isTransitionalPass(),
            "Transitional pass should be true when classic signature passes");

        String formatted = report.format();
        assertTrue(formatted.contains("PASS"),
            "Formatted report should contain PASS");
        assertTrue(formatted.contains("0xABCD1234"),
            "Formatted report should contain classic key ID");
        assertTrue(formatted.contains("ML-DSA-65+Ed25519"),
            "Formatted report should contain PQC algorithm");
        assertTrue(formatted.contains("abc123def456"),
            "Formatted report should contain PQC fingerprint");
    }

    /**
     * Tests that when classic passes but PQC has no key, strict pass fails but
     * transitional pass succeeds.
     */
    @Test
    void report_classicPassPqcNoKey() {
        VerificationReport report = new VerificationReport(
            VerificationResult.PASS,
            "0xABCD1234",
            VerificationResult.NO_KEY,
            "ML-DSA-65+Ed25519",
            null
        );

        assertFalse(report.isStrictPass(),
            "Strict pass should be false when PQC verification has no key");
        assertTrue(report.isTransitionalPass(),
            "Transitional pass should be true when classic signature passes");

        String formatted = report.format();
        assertTrue(formatted.contains("NO_KEY"),
            "Formatted report should contain NO_KEY for PQC result");
    }

    /**
     * Tests that when classic passes but PQC signature is not present (classic-only
     * signature), strict pass fails but transitional pass succeeds.
     */
    @Test
    void report_classicPassPqcNotPresent() {
        VerificationReport report = new VerificationReport(
            VerificationResult.PASS,
            "0xABCD1234",
            VerificationResult.NOT_PRESENT,
            null,
            null
        );

        assertFalse(report.isStrictPass(),
            "Strict pass should be false when PQC signature is not present");
        assertTrue(report.isTransitionalPass(),
            "Transitional pass should be true when classic signature passes");

        String formatted = report.format();
        assertTrue(formatted.contains("NOT PRESENT"),
            "Formatted report should contain NOT PRESENT for classic-only signature");
    }

    /**
     * Tests that when both classic and PQC signatures fail, both strict and
     * transitional pass fail.
     */
    @Test
    void report_bothFail() {
        VerificationReport report = new VerificationReport(
            VerificationResult.FAIL,
            "0xABCD1234",
            VerificationResult.FAIL,
            "ML-DSA-65+Ed25519",
            "abc123def456"
        );

        assertFalse(report.isStrictPass(),
            "Strict pass should be false when both signatures fail");
        assertFalse(report.isTransitionalPass(),
            "Transitional pass should be false when classic signature fails");

        String formatted = report.format();
        assertTrue(formatted.contains("FAIL"),
            "Formatted report should contain FAIL");
    }

    /**
     * Tests that when classic fails but PQC passes, both strict and transitional
     * pass fail (classic must always pass).
     */
    @Test
    void report_classicFailPqcPass() {
        VerificationReport report = new VerificationReport(
            VerificationResult.FAIL,
            "0xABCD1234",
            VerificationResult.PASS,
            "ML-DSA-65+Ed25519",
            "abc123def456"
        );

        assertFalse(report.isStrictPass(),
            "Strict pass should be false when classic signature fails");
        assertFalse(report.isTransitionalPass(),
            "Transitional pass should be false when classic signature fails");
    }

    /**
     * Tests that when key ID is null, the formatted report omits the [key:] part
     * for the classic line only.
     */
    @Test
    void report_nullKeyId() {
        VerificationReport report = new VerificationReport(
            VerificationResult.PASS,
            null,
            VerificationResult.PASS,
            "ML-DSA-65+Ed25519",
            "abc123def456"
        );

        String formatted = report.format();
        String[] lines = formatted.split("\n");
        boolean classicLineHasNoKey = false;
        for (String line : lines) {
            if (line.contains("Classic (GPG)") && !line.contains("[key:")) {
                classicLineHasNoKey = true;
                break;
            }
        }
        assertTrue(classicLineHasNoKey,
            "Classic line should not contain [key:] when keyId is null");
        assertTrue(formatted.contains("PASS"),
            "Formatted report should still contain PASS");
    }

    /**
     * Tests that when PQC fingerprint is null, the formatted report omits the
     * [key:] part for PQC.
     */
    @Test
    void report_nullPqcFingerprint() {
        VerificationReport report = new VerificationReport(
            VerificationResult.PASS,
            "0xABCD1234",
            VerificationResult.PASS,
            "ML-DSA-65+Ed25519",
            null
        );

        String formatted = report.format();
        String[] lines = formatted.split("\n");
        boolean pqcLineHasNoKey = false;
        for (String line : lines) {
            if (line.contains("ML-DSA-65+Ed25519") && !line.contains("[key:")) {
                pqcLineHasNoKey = true;
                break;
            }
        }
        assertTrue(pqcLineHasNoKey,
            "PQC line should not contain [key:] when fingerprint is null");
    }
}
