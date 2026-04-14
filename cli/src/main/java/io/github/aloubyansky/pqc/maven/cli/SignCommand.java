package io.github.aloubyansky.pqc.maven.cli;

import io.github.aloubyansky.pqc.maven.core.GpgSigner;
import io.github.aloubyansky.pqc.maven.core.HybridSigner;
import io.github.aloubyansky.pqc.maven.core.SqRunner;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command-line interface for creating hybrid signatures.
 * <p>
 * This command creates a hybrid signature that combines classical GPG (v4 packet)
 * and post-quantum cryptography (v6 packet using ML-DSA-65 + Ed25519) into a
 * single ASCII-armored signature file. The resulting signature provides both
 * classical and quantum-resistant security.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * # Sign with PQC only (using default GPG key)
 * pqc-sign sign --file artifact.jar --pqc-fingerprint ABC123DEF456...
 *
 * # Sign with specific GPG key and PQC key
 * pqc-sign sign --file artifact.jar \
 *               --pqc-fingerprint ABC123DEF456... \
 *               --gpg-key user@example.org
 *
 * # Sign with custom output path and Sequoia home
 * pqc-sign sign --file artifact.jar \
 *               --pqc-fingerprint ABC123DEF456... \
 *               --output custom-signature.asc \
 *               --sq-home /path/to/sq-home
 * </pre>
 * </p>
 * <p>
 * The command outputs the path to the generated signature file on success.
 * </p>
 * <p>
 * Requirements:
 * <ul>
 *   <li>{@code gpg} executable must be available on the system PATH</li>
 *   <li>{@code sq} executable must be available on the system PATH</li>
 *   <li>A GPG key must be configured (either default or specified via --gpg-key)</li>
 *   <li>The PQC key must exist in the Sequoia home directory</li>
 * </ul>
 * </p>
 *
 * @see HybridSigner
 * @see GpgSigner
 * @see SqRunner
 */
@CommandLine.Command(
    name = "sign",
    description = "Create a hybrid signature combining GPG and PQC",
    mixinStandardHelpOptions = true
)
public class SignCommand implements Callable<Integer> {

    /**
     * The artifact file to sign.
     * <p>
     * This is typically a Maven artifact (e.g., {@code artifact.jar}), but can be
     * any file that needs to be signed.
     * </p>
     */
    @CommandLine.Option(
        names = {"--file"},
        required = true,
        description = "Artifact file to sign"
    )
    private String file;

    /**
     * The PQC key fingerprint to use for signing.
     * <p>
     * This should be the 64-character hexadecimal fingerprint returned by the
     * {@code keygen} command. The key must exist in the Sequoia home directory.
     * </p>
     */
    @CommandLine.Option(
        names = {"--pqc-fingerprint"},
        required = true,
        description = "PQC key fingerprint (64-char hex)"
    )
    private String pqcFingerprint;

    /**
     * The GPG key identifier to use for signing.
     * <p>
     * This can be a key ID, fingerprint, or email address associated with the GPG
     * key. If not specified, GPG will use the default key configured in the user's
     * GPG keyring.
     * </p>
     */
    @CommandLine.Option(
        names = {"--gpg-key"},
        description = "GPG key ID/email (default: use GPG's default key)"
    )
    private String gpgKey;

    /**
     * The Sequoia home directory where the PQC key is stored.
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
     * The output path for the generated signature file.
     * <p>
     * If not specified, the signature will be written to {@code <file>.asc}
     * (e.g., {@code artifact.jar.asc} for {@code artifact.jar}).
     * </p>
     */
    @CommandLine.Option(
        names = {"--output"},
        description = "Output signature file path (default: <file>.asc)"
    )
    private String output;

    /**
     * Executes the signing command.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Resolves file paths (artifact, output, Sequoia home)</li>
     *   <li>Creates {@link GpgSigner} and {@link SqRunner} instances</li>
     *   <li>Creates a {@link HybridSigner} using the factory method</li>
     *   <li>Generates the hybrid signature</li>
     *   <li>Prints the output file path</li>
     * </ol>
     * </p>
     * <p>
     * On error, catches all exceptions, prints a user-friendly error message to
     * stderr, and returns exit code 1.
     * </p>
     *
     * @return 0 on success, 1 on error
     */
    @Override
    public Integer call() {
        try {
            Path artifactFile = resolveArtifactFile();
            Path outputFile = resolveOutputFile(artifactFile);
            Path sqHomeDir = resolveSequoiaHome();

            GpgSigner gpgSigner = createGpgSigner();
            SqRunner sqRunner = createSqRunner(sqHomeDir);
            HybridSigner signer = HybridSigner.create(gpgSigner, sqRunner, pqcFingerprint);

            signer.sign(artifactFile, outputFile);

            printSuccessMessage(outputFile);
            return 0;
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
     * Resolves the output signature file path.
     * <p>
     * If {@link #output} is specified, it is used as-is. Otherwise, the default
     * path is {@code <artifactFile>.asc}.
     * </p>
     *
     * @param artifactFile the artifact file being signed
     * @return the output signature file path
     */
    private Path resolveOutputFile(Path artifactFile) {
        if (output != null && !output.isEmpty()) {
            return Paths.get(output);
        }
        return Paths.get(artifactFile.toString() + ".asc");
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
     * Creates a {@link GpgSigner} instance with the configured GPG key.
     * <p>
     * If {@link #gpgKey} is specified, it is used as the key identifier. Otherwise,
     * {@code null} is passed, which causes GPG to use its default key.
     * </p>
     *
     * @return a configured GpgSigner instance
     */
    private GpgSigner createGpgSigner() {
        return new GpgSigner(gpgKey);
    }

    /**
     * Creates an {@link SqRunner} instance with the configured Sequoia home directory.
     *
     * @param sqHomeDir the Sequoia home directory path
     * @return a configured SqRunner instance
     */
    private SqRunner createSqRunner(Path sqHomeDir) {
        return new SqRunner(sqHomeDir);
    }

    /**
     * Prints a success message with the output signature file path.
     * <p>
     * Example output:
     * <pre>
     * Hybrid signature created successfully!
     *
     * Signature file: /path/to/artifact.jar.asc
     * </pre>
     * </p>
     *
     * @param outputFile the path to the generated signature file
     */
    private void printSuccessMessage(Path outputFile) {
        System.out.println("Hybrid signature created successfully!");
        System.out.println();
        System.out.println("Signature file: " + outputFile.toAbsolutePath());
    }

    /**
     * Prints a user-friendly error message to stderr.
     * <p>
     * This method extracts the most relevant error message from the exception
     * chain and displays it in a clear format.
     * </p>
     *
     * @param e the exception that occurred during signing
     */
    private void printErrorMessage(Exception e) {
        System.err.println("Error creating signature:");
        System.err.println("  " + extractErrorMessage(e));
        System.err.println();
        System.err.println("Ensure that:");
        System.err.println("  - The 'gpg' and 'sq' commands are installed and available");
        System.err.println("  - You have a valid GPG key configured");
        System.err.println("  - The PQC key exists in the Sequoia home directory");
        System.err.println("  - The artifact file exists and is readable");
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
