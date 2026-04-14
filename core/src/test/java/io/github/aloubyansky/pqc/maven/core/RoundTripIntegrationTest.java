package io.github.aloubyansky.pqc.maven.core;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Full round-trip integration tests for hybrid PQC signing and verification.
 * <p>
 * This test class validates the complete end-to-end flow of signing artifacts
 * with hybrid (classical + post-quantum) signatures and verifying them. These
 * tests require both GnuPG (gpg) and Sequoia (sq) to be installed and available
 * on the system PATH.
 * </p>
 * <p>
 * The test suite is automatically disabled via {@link EnabledIf} if either
 * tool is unavailable, making it safe to run in environments where these
 * tools may not be installed.
 * </p>
 * <p>
 * Test flow:
 * <ol>
 *   <li>Generate a PQC key using Sequoia's ML-DSA-65 + Ed25519 cipher suite</li>
 *   <li>Sign artifacts using {@link HybridSigner} (GPG + Sequoia)</li>
 *   <li>Verify signatures using {@link HybridVerifier}</li>
 *   <li>Validate backward compatibility with GPG-only verification</li>
 *   <li>Verify tamper detection capabilities</li>
 * </ol>
 * </p>
 *
 * @see HybridSigner
 * @see HybridVerifier
 * @see VerificationReport
 */
@EnabledIf("toolsAvailable")
class RoundTripIntegrationTest {

    private static Path sqHome;
    private static String pqcFingerprint;

    /**
     * Checks if both GPG and PQC-enabled Sequoia are available on the system.
     * <p>
     * This method is used by JUnit's {@link EnabledIf} annotation to conditionally
     * enable the test class. It verifies not just that {@code sq} is installed, but
     * that it supports the PQC cipher suite ({@code mldsa65-ed25519}). The standard
     * Sequoia release (1.3.x) does not include PQC support — version 1.4.0-pqc.1+
     * from the {@code pqc} branch is required.
     * </p>
     *
     * @return true if both {@code gpg} and PQC-enabled {@code sq} are available
     */
    static boolean toolsAvailable() {
        if (!GpgSigner.isAvailable() || !SqRunner.isAvailable()) {
            return false;
        }
        // Check that sq actually supports PQC cipher suites
        try {
            CliTool.Result result = CliTool.run("sq", "key", "generate", "--help");
            return result.stdout().contains("mldsa65-ed25519")
                    || result.stderr().contains("mldsa65-ed25519");
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sets up the test environment by creating a temporary Sequoia home directory
     * and generating a PQC key for use in all tests.
     * <p>
     * The generated key uses Sequoia's ML-DSA-65 + Ed25519 hybrid cipher suite,
     * providing both classical and post-quantum security. The key fingerprint is
     * stored in {@link #pqcFingerprint} for use by all test methods.
     * </p>
     *
     * @param tempDir a temporary directory provided by JUnit, used as SEQUOIA_HOME
     * @throws Exception if key generation fails
     */
    @BeforeAll
    static void generatePqcKey(@TempDir Path tempDir) throws Exception {
        sqHome = tempDir.resolve("sq-home");
        Files.createDirectories(sqHome);
        SqRunner sq = new SqRunner(sqHome);
        pqcFingerprint = sq.generateKey("PQC Test <test@pqc-sign.example>");
    }

    /**
     * Tests the complete round-trip flow: sign with hybrid signer, verify with
     * hybrid verifier, and validate that both signatures pass.
     * <p>
     * This test verifies that:
     * <ul>
     *   <li>Hybrid signing produces a valid combined signature file</li>
     *   <li>The classical GPG signature component is valid</li>
     *   <li>The PQC Sequoia signature component is valid</li>
     *   <li>{@link VerificationReport#isStrictPass()} returns true (both components pass)</li>
     * </ul>
     * </p>
     * <p>
     * The verification report is printed to stdout to aid in debugging and to
     * demonstrate the human-readable format of {@link VerificationReport#format()}.
     * </p>
     *
     * @param tempDir a temporary directory for test artifacts
     * @throws Exception if signing or verification fails
     */
    @Test
    void fullRoundTrip_signAndVerify(@TempDir Path tempDir) throws Exception {
        // Arrange: Create test artifact
        Path artifact = createTestArtifact(tempDir, "test-artifact.jar");
        Path signature = tempDir.resolve("test-artifact.jar.asc");

        // Arrange: Create hybrid signer and verifier
        HybridSigner signer = createHybridSigner();
        HybridVerifier verifier = createHybridVerifier();

        // Act: Sign the artifact
        signer.sign(artifact, signature);

        // Act: Verify the signature
        VerificationReport report = verifier.verify(artifact, signature);

        // Assert: Both classic and PQC signatures should pass
        assertEquals(VerificationResult.PASS, report.classicResult(),
            "Classic (GPG) signature should be valid");
        assertEquals(VerificationResult.PASS, report.pqcResult(),
            "PQC (Sequoia) signature should be valid");
        assertTrue(report.isStrictPass(),
            "Strict verification (both signatures) should pass");

        // Print the report for manual inspection
        System.out.println("=== Full Round-Trip Verification Report ===");
        System.out.println(report.format());
    }

    /**
     * Tests backward compatibility by verifying that GPG can verify the combined
     * .asc file even though it contains a v6 PQC packet.
     * <p>
     * This test ensures that:
     * <ul>
     *   <li>The hybrid signature file is a valid GPG signature file</li>
     *   <li>GPG's {@code --verify} command succeeds (exit code 0)</li>
     *   <li>Systems without PQC support can still verify the classical signature</li>
     * </ul>
     * </p>
     * <p>
     * This is critical for gradual migration scenarios where some systems may not
     * yet have PQC verification capabilities but still need to validate artifacts.
     * </p>
     *
     * @param tempDir a temporary directory for test artifacts
     * @throws Exception if signing or verification fails
     */
    @Test
    void backwardCompat_gpgVerifiesCombinedAsc(@TempDir Path tempDir) throws Exception {
        // Arrange: Create and sign test artifact
        Path artifact = createTestArtifact(tempDir, "compat-test.jar");
        Path signature = tempDir.resolve("compat-test.jar.asc");
        HybridSigner signer = createHybridSigner();
        signer.sign(artifact, signature);

        // Act: Verify with GPG command-line tool directly
        CliTool.Result result = CliTool.run(
            "gpg",
            "--verify",
            signature.toString(),
            artifact.toString()
        );

        // Assert: GPG should successfully verify despite v6 PQC packet.
        // GPG may return exit code 2 (warnings) due to the unknown v6 packet,
        // but the classic signature is still verified ("Good signature").
        assertTrue(result.exitCode() == 0
                || (result.exitCode() == 2 && result.stderr().contains("Good signature")),
            "GPG should verify the combined .asc file (backward compatible). "
            + "Exit: " + result.exitCode() + " stderr: " + result.stderr());

        System.out.println("=== Backward Compatibility Test ===");
        System.out.println("GPG verified hybrid signature successfully");
        if (!result.stderr().isEmpty()) {
            System.out.println("GPG output:");
            System.out.println(result.stderr());
        }
    }

    /**
     * Tests that verification correctly fails when the artifact has been tampered
     * with after signing.
     * <p>
     * This test verifies that:
     * <ul>
     *   <li>Modifying the artifact after signing invalidates both signatures</li>
     *   <li>Both classical and PQC verification results are FAIL</li>
     *   <li>The signature mechanism provides tamper detection</li>
     * </ul>
     * </p>
     * <p>
     * This is a critical security property - any modification to the signed
     * artifact should be detected by the verification process.
     * </p>
     *
     * @param tempDir a temporary directory for test artifacts
     * @throws Exception if signing or verification setup fails
     */
    @Test
    void tamperedArtifact_verificationFails(@TempDir Path tempDir) throws Exception {
        // Arrange: Create and sign test artifact
        Path artifact = createTestArtifact(tempDir, "tamper-test.jar");
        Path signature = tempDir.resolve("tamper-test.jar.asc");
        HybridSigner signer = createHybridSigner();
        signer.sign(artifact, signature);

        // Act: Tamper with the artifact after signing
        Files.writeString(artifact, "TAMPERED CONTENT - THIS SHOULD FAIL VERIFICATION");

        // Act: Verify the signature
        HybridVerifier verifier = createHybridVerifier();
        VerificationReport report = verifier.verify(artifact, signature);

        // Assert: Both signatures should fail due to tampering
        assertEquals(VerificationResult.FAIL, report.classicResult(),
            "Classic (GPG) signature should fail for tampered artifact");
        assertEquals(VerificationResult.FAIL, report.pqcResult(),
            "PQC (Sequoia) signature should fail for tampered artifact");

        System.out.println("=== Tampered Artifact Verification Report ===");
        System.out.println(report.format());
    }

    /**
     * Creates a test artifact file with sample content.
     * <p>
     * This helper method creates a file in the specified directory with
     * deterministic content that can be signed and verified.
     * </p>
     *
     * @param tempDir the directory where the artifact will be created
     * @param filename the name of the artifact file
     * @return the Path to the created artifact
     * @throws Exception if file creation fails
     */
    private Path createTestArtifact(Path tempDir, String filename) throws Exception {
        Path artifact = tempDir.resolve(filename);
        Files.writeString(artifact, "This is a test artifact for PQC signing verification.\n" +
            "It contains some sample content that will be signed and verified.\n" +
            "The content is not important, only that it remains unchanged.\n");
        return artifact;
    }

    /**
     * Creates a {@link HybridSigner} configured with the default GPG key and
     * the PQC key generated in {@link #generatePqcKey(Path)}.
     * <p>
     * This helper method encapsulates the signer creation logic and ensures
     * consistent configuration across all test methods.
     * </p>
     *
     * @return a configured HybridSigner instance
     */
    private HybridSigner createHybridSigner() {
        GpgSigner gpg = new GpgSigner(null);  // null = use default GPG key
        SqRunner sq = new SqRunner(sqHome);
        return HybridSigner.create(gpg, sq, pqcFingerprint);
    }

    /**
     * Creates a {@link HybridVerifier} configured with the default GPG key and
     * the PQC key generated in {@link #generatePqcKey(Path)}.
     * <p>
     * This helper method encapsulates the verifier creation logic and ensures
     * consistent configuration across all test methods.
     * </p>
     *
     * @return a configured HybridVerifier instance
     */
    private HybridVerifier createHybridVerifier() {
        GpgSigner gpg = new GpgSigner(null);  // null = use default GPG key
        SqRunner sq = new SqRunner(sqHome);
        return new HybridVerifier(gpg, sq, pqcFingerprint);
    }
}
