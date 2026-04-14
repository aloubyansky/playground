package io.github.aloubyansky.pqc.maven.cli;

import io.github.aloubyansky.pqc.maven.core.GpgSigner;
import io.github.aloubyansky.pqc.maven.core.HybridVerifier;
import io.github.aloubyansky.pqc.maven.core.SqRunner;
import io.github.aloubyansky.pqc.maven.core.VerificationReport;
import io.github.aloubyansky.pqc.maven.core.VerificationResult;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command-line interface for verifying hybrid signatures.
 * <p>
 * This command verifies both classical GPG and post-quantum cryptographic
 * signatures in a hybrid signature file. It supports two verification modes:
 * <ul>
 *   <li><b>Transitional mode</b> (default): Only the classical GPG signature must
 *       pass. PQC signature is optional.</li>
 *   <li><b>Strict mode</b> (--strict flag): Both GPG and PQC signatures must pass.</li>
 * </ul>
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * # Verify with transitional mode (classic signature sufficient)
 * pqc-sign verify --file artifact.jar --signature artifact.jar.asc
 *
 * # Verify with strict mode (both signatures required)
 * pqc-sign verify --file artifact.jar --signature artifact.jar.asc --strict
 *
 * # Verify against a specific PQC key
 * pqc-sign verify --file artifact.jar \
 *                 --signature artifact.jar.asc \
 *                 --pqc-fingerprint ABC123DEF456...
 *
 * # Verify with custom Sequoia home
 * pqc-sign verify --file artifact.jar \
 *                 --signature artifact.jar.asc \
 *                 --sq-home /path/to/sq-home
 * </pre>
 * </p>
 * <p>
 * The command outputs a detailed verification report showing the status of both
 * classical and PQC signatures, and exits with:
 * <ul>
 *   <li>Exit code 0: Verification passed (according to the selected mode)</li>
 *   <li>Exit code 1: Verification failed</li>
 * </ul>
 * </p>
 * <p>
 * Requirements:
 * <ul>
 *   <li>{@code gpg} executable must be available on the system PATH</li>
 *   <li>{@code sq} executable must be available for PQC verification (optional in
 *       transitional mode)</li>
 *   <li>The signer's public keys must be available in the respective keystores</li>
 * </ul>
 * </p>
 *
 * @see HybridVerifier
 * @see VerificationReport
 */
@CommandLine.Command(
    name = "verify",
    description = "Verify a hybrid signature",
    mixinStandardHelpOptions = true
)
public class VerifyCommand implements Callable<Integer> {

    /**
     * The artifact file to verify.
     * <p>
     * This is the file that was originally signed and whose signature will be
     * verified.
     * </p>
     */
    @CommandLine.Option(
        names = {"--file"},
        required = true,
        description = "Artifact file to verify"
    )
    private String file;

    /**
     * The detached signature file to verify against.
     * <p>
     * This is typically an ASCII-armored signature file with the {@code .asc}
     * extension (e.g., {@code artifact.jar.asc}).
     * </p>
     */
    @CommandLine.Option(
        names = {"--signature"},
        required = true,
        description = "Signature file (.asc)"
    )
    private String signature;

    /**
     * The expected PQC key fingerprint for signature verification.
     * <p>
     * If specified, the PQC signature will be verified against this specific key.
     * If not specified, PQC verification will succeed as long as any valid PQC
     * signature is present (without checking the signer identity).
     * </p>
     */
    @CommandLine.Option(
        names = {"--pqc-fingerprint"},
        description = "Expected PQC key fingerprint (optional)"
    )
    private String pqcFingerprint;

    /**
     * The Sequoia home directory where PQC keys are stored.
     * <p>
     * If not specified, defaults to {@code ~/.local/share/sequoia}.
     * </p>
     */
    @CommandLine.Option(
        names = {"--sq-home"},
        description = "Sequoia home directory (default: ~/.local/share/sequoia)"
    )
    private String sqHome;

    /**
     * Whether to require both signatures to pass (strict mode).
     * <p>
     * In strict mode, both the classical GPG signature and the PQC signature must
     * pass verification. This provides quantum-resistant security guarantees.
     * </p>
     * <p>
     * In transitional mode (default), only the classical GPG signature is required
     * to pass. This allows for gradual migration to PQC signatures while maintaining
     * backward compatibility.
     * </p>
     */
    @CommandLine.Option(
        names = {"--strict"},
        description = "Require both GPG and PQC signatures to pass (default: false)"
    )
    private boolean strict;

    /**
     * Executes the verification command.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Resolves file paths (artifact, signature, Sequoia home)</li>
     *   <li>Creates {@link GpgSigner} and {@link SqRunner} instances (if available)</li>
     *   <li>Creates a {@link HybridVerifier}</li>
     *   <li>Performs verification and generates a report</li>
     *   <li>Prints the verification report</li>
     *   <li>Determines the exit code based on the verification mode</li>
     * </ol>
     * </p>
     * <p>
     * On error, catches all exceptions, prints a user-friendly error message to
     * stderr, and returns exit code 1.
     * </p>
     *
     * @return 0 if verification passes, 1 if verification fails or an error occurs
     */
    @Override
    public Integer call() {
        try {
            Path artifactFile = resolveArtifactFile();
            Path signatureFile = resolveSignatureFile();
            Path sqHomeDir = resolveSequoiaHome();

            GpgSigner gpgSigner = createGpgSigner();
            SqRunner sqRunner = createSqRunnerIfAvailable(sqHomeDir);
            HybridVerifier verifier = new HybridVerifier(gpgSigner, sqRunner, pqcFingerprint);

            VerificationReport report = verifier.verify(artifactFile, signatureFile);

            printVerificationReport(report);

            return determineExitCode(report);
        } catch (Exception e) {
            printErrorMessage(e);
            return 1;
        }
    }

    /**
     * Resolves the artifact file path from the --file option.
     *
     * @return the artifact file path
     */
    private Path resolveArtifactFile() {
        return Paths.get(file);
    }

    /**
     * Resolves the signature file path from the --signature option.
     *
     * @return the signature file path
     */
    private Path resolveSignatureFile() {
        return Paths.get(signature);
    }

    /**
     * Resolves the Sequoia home directory path.
     * <p>
     * If {@link #sqHome} is specified, it is used as-is. Otherwise, the default
     * Sequoia home directory is returned: {@code ~/.local/share/sequoia}.
     * </p>
     *
     * @return the resolved Sequoia home directory path
     */
    private Path resolveSequoiaHome() {
        if (sqHome != null && !sqHome.isEmpty()) {
            return expandTilde(sqHome);
        }

        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".local", "share", "sequoia");
    }

    /**
     * Expands the tilde (~) character to the user's home directory.
     * <p>
     * If the path starts with {@code ~/}, the tilde is replaced with the value
     * of the {@code user.home} system property. Otherwise, the path is returned
     * unchanged.
     * </p>
     *
     * @param path the path to expand
     * @return the path with tilde expanded, or the original path if no tilde present
     */
    private Path expandTilde(String path) {
        if (path.startsWith("~/")) {
            String userHome = System.getProperty("user.home");
            return Paths.get(userHome, path.substring(2));
        }
        return Paths.get(path);
    }

    /**
     * Creates a {@link GpgSigner} instance.
     * <p>
     * The GPG key is set to {@code null} to use GPG's default verification behavior,
     * which accepts signatures from any key in the keyring.
     * </p>
     *
     * @return a configured GpgSigner instance
     */
    private GpgSigner createGpgSigner() {
        return new GpgSigner(null);
    }

    /**
     * Creates an {@link SqRunner} instance if the sq executable is available.
     * <p>
     * This method checks if {@code sq} is available using {@link SqRunner#isAvailable()}.
     * If it is not available, {@code null} is returned, which will cause the
     * {@link HybridVerifier} to skip PQC verification and report
     * {@link VerificationResult#NOT_PRESENT} for the PQC component.
     * </p>
     *
     * @param sqHomeDir the Sequoia home directory path
     * @return a configured SqRunner instance, or null if sq is not available
     */
    private SqRunner createSqRunnerIfAvailable(Path sqHomeDir) {
        if (!SqRunner.isAvailable()) {
            return null;
        }
        return new SqRunner(sqHomeDir);
    }

    /**
     * Prints the verification report to stdout.
     * <p>
     * The report includes:
     * <ul>
     *   <li>Classic (GPG) signature verification result</li>
     *   <li>PQC signature verification result</li>
     *   <li>Overall assessment based on the verification mode</li>
     * </ul>
     * </p>
     *
     * @param report the verification report to print
     */
    private void printVerificationReport(VerificationReport report) {
        System.out.println(report.format());
    }

    /**
     * Determines the exit code based on the verification report and mode.
     * <p>
     * In strict mode, both signatures must pass (exit code 0 only if
     * {@link VerificationReport#isStrictPass()} returns true).
     * </p>
     * <p>
     * In transitional mode, only the classical signature must pass (exit code 0 if
     * {@link VerificationReport#isTransitionalPass()} returns true).
     * </p>
     *
     * @param report the verification report
     * @return 0 if verification passed according to the mode, 1 otherwise
     */
    private int determineExitCode(VerificationReport report) {
        if (strict) {
            return report.isStrictPass() ? 0 : 1;
        } else {
            return report.isTransitionalPass() ? 0 : 1;
        }
    }

    /**
     * Prints a user-friendly error message to stderr.
     * <p>
     * This method extracts the most relevant error message from the exception
     * chain and displays it in a clear format.
     * </p>
     *
     * @param e the exception that occurred during verification
     */
    private void printErrorMessage(Exception e) {
        System.err.println("Error verifying signature:");
        System.err.println("  " + extractErrorMessage(e));
        System.err.println();
        System.err.println("Ensure that:");
        System.err.println("  - The 'gpg' command is installed and available");
        System.err.println("  - The signer's public GPG key is in your keyring");
        System.err.println("  - For PQC verification: the 'sq' command is installed");
        System.err.println("  - The artifact and signature files exist and are readable");
    }

    /**
     * Extracts a user-friendly error message from an exception.
     * <p>
     * If the exception has a message, it is returned. Otherwise, the simple
     * class name of the exception is returned.
     * </p>
     *
     * @param e the exception to extract the message from
     * @return a user-friendly error message
     */
    private String extractErrorMessage(Exception e) {
        String message = e.getMessage();
        if (message != null && !message.isEmpty()) {
            return message;
        }
        return e.getClass().getSimpleName();
    }
}
