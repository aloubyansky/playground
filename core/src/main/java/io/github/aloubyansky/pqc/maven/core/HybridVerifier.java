package io.github.aloubyansky.pqc.maven.core;

import java.nio.file.Path;

/**
 * Verifies hybrid signatures containing both classic GPG and PQC components.
 * <p>
 * This class delegates verification to both GPG (for classic signatures) and
 * Sequoia (for PQC signatures), then combines the results into a
 * {@link VerificationReport}. It supports both hybrid signatures (containing
 * both components) and classic-only signatures (for backward compatibility).
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Create verifier with both GPG and PQC support
 * GpgSigner gpg = new GpgSigner("gpg", null);
 * SqRunner sq = new SqRunner(Path.of("/tmp/sq-keys"));
 * String pqcFingerprint = "ABC123...";
 * HybridVerifier verifier = new HybridVerifier(gpg, sq, pqcFingerprint);
 *
 * // Verify a hybrid signature
 * VerificationReport report = verifier.verify(
 *     Path.of("artifact.jar"),
 *     Path.of("artifact.jar.asc")
 * );
 *
 * if (report.isStrictPass()) {
 *     System.out.println("Quantum-safe verification passed!");
 * }
 *
 * // Create verifier for classic-only signatures (no PQC support)
 * HybridVerifier classicOnly = new HybridVerifier(gpg, null, null);
 * VerificationReport report2 = classicOnly.verify(
 *     Path.of("old-artifact.jar"),
 *     Path.of("old-artifact.jar.asc")
 * );
 * // report2.pqcResult() will be NOT_PRESENT
 * }</pre>
 * <p>
 * Note: This class requires both {@code gpg} and {@code sq} executables to be
 * available on the system PATH for full functionality. If {@code sq} is not
 * available or {@link #sq} is null, PQC verification will return
 * {@link VerificationResult#NOT_PRESENT}.
 * </p>
 *
 * @see HybridSigner
 * @see VerificationReport
 * @see VerificationResult
 */
public class HybridVerifier {

    private final GpgSigner gpg;
    private final SqRunner sq;
    private final String pqcFingerprint;

    /**
     * Constructs a HybridVerifier with specified GPG and PQC configuration.
     * <p>
     * The {@code gpg} parameter is currently unused for verification (GPG is
     * invoked directly via {@link CliTool}), but is included for future-proofing
     * and API consistency with {@link HybridSigner}.
     * </p>
     *
     * @param gpg the GPG signer instance (currently unused, reserved for future use)
     * @param sq the Sequoia runner instance for PQC verification, or null to
     *           skip PQC verification
     * @param pqcFingerprint the expected PQC key fingerprint, or null if not
     *                       verifying against a specific key
     * @throws IllegalArgumentException if gpg is null
     */
    public HybridVerifier(GpgSigner gpg, SqRunner sq, String pqcFingerprint) {
        if (gpg == null) {
            throw new IllegalArgumentException("gpg cannot be null");
        }
        this.gpg = gpg;
        this.sq = sq;
        this.pqcFingerprint = pqcFingerprint;
    }

    /**
     * Verifies both classic and PQC signatures for the specified artifact.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Verifies the classic GPG signature using {@code gpg --verify}</li>
     *   <li>Verifies the PQC signature using {@code sq verify} (if {@link #sq} is not null)</li>
     *   <li>Combines the results into a {@link VerificationReport}</li>
     * </ol>
     * </p>
     * <p>
     * The method handles various failure modes gracefully:
     * <ul>
     *   <li>If PQC verifier is null: PQC result is {@link VerificationResult#NOT_PRESENT}</li>
     *   <li>If GPG fails: classic result is {@link VerificationResult#FAIL}</li>
     *   <li>If PQC signature is invalid: PQC result is {@link VerificationResult#FAIL}</li>
     * </ul>
     * </p>
     *
     * @param artifactFile the file that was signed
     * @param signatureFile the detached signature file (may contain classic-only or
     *                      hybrid signature)
     * @return a {@link VerificationReport} containing both classic and PQC results
     * @throws IllegalArgumentException if artifactFile or signatureFile is null
     */
    public VerificationReport verify(Path artifactFile, Path signatureFile) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (signatureFile == null) {
            throw new IllegalArgumentException("signatureFile cannot be null");
        }

        VerificationResult classicResult = verifyClassic(artifactFile, signatureFile);
        String classicKeyId = null; // TODO: Parse from GPG output in future enhancement

        VerificationResult pqcResult;
        String pqcAlgorithm = null;
        String pqcKeyFp = null;

        if (sq != null) {
            pqcResult = verifyPqc(artifactFile, signatureFile);
            if (pqcResult == VerificationResult.PASS || pqcResult == VerificationResult.FAIL) {
                pqcAlgorithm = "ML-DSA-65+Ed25519"; // Default algorithm
                pqcKeyFp = pqcFingerprint;
            }
        } else {
            pqcResult = VerificationResult.NOT_PRESENT;
        }

        return new VerificationReport(
            classicResult,
            classicKeyId,
            pqcResult,
            pqcAlgorithm,
            pqcKeyFp
        );
    }

    /**
     * Verifies the classic GPG signature using the gpg command-line tool.
     * <p>
     * This method runs:
     * {@code gpg --verify <signatureFile> <artifactFile>}
     * </p>
     * <p>
     * The verification result is determined by the exit code:
     * <ul>
     *   <li>Exit code 0: {@link VerificationResult#PASS} (valid signature)</li>
     *   <li>Exit code 2: {@link VerificationResult#PASS} (valid signature with warnings,
     *       e.g., unknown v6 PQC packet in the combined .asc)</li>
     *   <li>Exit code 1: {@link VerificationResult#FAIL} (bad signature)</li>
     *   <li>Other exit codes: {@link VerificationResult#FAIL}</li>
     * </ul>
     * </p>
     *
     * @param artifactFile the file that was signed
     * @param signatureFile the signature file to verify
     * @return {@link VerificationResult#PASS} if verification succeeds,
     *         {@link VerificationResult#FAIL} otherwise
     */
    private VerificationResult verifyClassic(Path artifactFile, Path signatureFile) {
        CliTool.Result result = CliTool.run(
            "gpg",
            "--verify",
            signatureFile.toString(),
            artifactFile.toString()
        );

        // GPG exit codes: 0 = valid, 1 = bad signature, 2 = warnings (e.g., unknown packet version).
        // Exit code 2 with "Good signature" means the classic signature is valid
        // but GPG encountered the v6 PQC packet it doesn't understand.
        boolean goodSignature = result.exitCode() == 0
                || (result.exitCode() == 2 && result.stderr().contains("Good signature"));
        return goodSignature ? VerificationResult.PASS : VerificationResult.FAIL;
    }

    /**
     * Verifies the PQC signature using the Sequoia command-line tool.
     * <p>
     * Delegates to {@link SqRunner#verify(Path, Path, String)} which returns
     * a boolean indicating success or failure.
     * </p>
     *
     * @param artifactFile the file that was signed
     * @param signatureFile the signature file to verify
     * @return {@link VerificationResult#PASS} if verification succeeds,
     *         {@link VerificationResult#FAIL} otherwise
     */
    private VerificationResult verifyPqc(Path artifactFile, Path signatureFile) {
        if (sq == null) {
            return VerificationResult.NOT_PRESENT;
        }
        boolean verified = sq.verify(artifactFile, signatureFile, pqcFingerprint);
        return verified ? VerificationResult.PASS : VerificationResult.FAIL;
    }
}
