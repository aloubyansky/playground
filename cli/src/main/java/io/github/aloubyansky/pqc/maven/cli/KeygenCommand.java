package io.github.aloubyansky.pqc.maven.cli;

import io.github.aloubyansky.pqc.maven.core.SqRunner;
import picocli.CommandLine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Command-line interface for generating new PQC keys.
 * <p>
 * This command generates a new post-quantum cryptographic key using the Sequoia
 * (sq) tool with the ML-DSA-65 + Ed25519 hybrid cipher suite as specified in
 * RFC 9580. The generated key is stored in the Sequoia home directory and can
 * be used for subsequent signing operations.
 * </p>
 * <p>
 * Example usage:
 * <pre>
 * # Generate a key with default Sequoia home (~/.local/share/sequoia)
 * pqc-sign keygen --userid "Alice &lt;alice@example.org&gt;"
 *
 * # Generate a key with custom Sequoia home
 * pqc-sign keygen --userid "Bob &lt;bob@example.org&gt;" --sq-home /path/to/sq-home
 * </pre>
 * </p>
 * <p>
 * On successful key generation, the command outputs:
 * <ul>
 *   <li>The 64-character hexadecimal fingerprint of the generated key</li>
 *   <li>The path to the Sequoia home directory where the key is stored</li>
 * </ul>
 * </p>
 * <p>
 * Note: This command requires the {@code sq} executable to be available on the
 * system PATH. Use {@code sq version} to verify installation.
 * </p>
 *
 * @see SqRunner#generateKey(String)
 */
@CommandLine.Command(
    name = "keygen",
    description = "Generate a new PQC key for signing",
    mixinStandardHelpOptions = true
)
public class KeygenCommand implements Callable<Integer> {

    /**
     * The user ID for the generated key.
     * <p>
     * This should typically be in the format "Name &lt;email@example.org&gt;", though
     * any string identifier is technically valid. The user ID will be associated
     * with the generated key and can be used to identify the key owner.
     * </p>
     */
    @CommandLine.Option(
        names = {"--userid"},
        required = true,
        description = "User ID for the key (e.g., \"Alice <alice@example.org>\")"
    )
    private String userId;

    /**
     * The Sequoia home directory where keys will be stored.
     * <p>
     * If not specified, defaults to the standard Sequoia home directory:
     * {@code ~/.local/share/sequoia} on Unix-like systems.
     * </p>
     * <p>
     * The Sequoia home directory contains:
     * <ul>
     *   <li>Generated keys and certificates</li>
     *   <li>Imported public keys</li>
     *   <li>Configuration files</li>
     * </ul>
     * </p>
     */
    @CommandLine.Option(
        names = {"--sq-home"},
        description = "Sequoia home directory (default: ~/.local/share/sequoia)"
    )
    private String sqHome;

    /**
     * Executes the key generation command.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Resolves the Sequoia home directory (using default if not specified)</li>
     *   <li>Creates an {@link SqRunner} instance</li>
     *   <li>Generates the PQC key with the specified user ID</li>
     *   <li>Prints the fingerprint and key storage location</li>
     * </ol>
     * </p>
     * <p>
     * On error, this method catches all exceptions, prints a user-friendly error
     * message to stderr, and returns exit code 1.
     * </p>
     *
     * @return 0 on success, 1 on error
     */
    @Override
    public Integer call() {
        try {
            Path sqHomeDir = resolveSequoiaHome();
            SqRunner sq = new SqRunner(sqHomeDir);

            String fingerprint = sq.generateKey(userId);

            printSuccessMessage(fingerprint, sqHomeDir);
            return 0;
        } catch (Exception e) {
            printErrorMessage(e);
            return 1;
        }
    }

    /**
     * Resolves the Sequoia home directory path.
     * <p>
     * If {@link #sqHome} is specified, it is used as-is. Otherwise, the default
     * Sequoia home directory is returned: {@code ~/.local/share/sequoia}.
     * </p>
     * <p>
     * The {@code ~} symbol is expanded to the user's home directory using the
     * {@code user.home} system property.
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
     * Prints a success message with the generated key fingerprint and storage location.
     * <p>
     * Example output:
     * <pre>
     * PQC key generated successfully!
     *
     * Fingerprint: ABC123DEF456...
     * Stored in:   /home/user/.local/share/sequoia
     *
     * Use this fingerprint with the 'sign' command.
     * </pre>
     * </p>
     *
     * @param fingerprint the fingerprint of the generated key
     * @param sqHomeDir the Sequoia home directory where the key is stored
     */
    private void printSuccessMessage(String fingerprint, Path sqHomeDir) {
        System.out.println("PQC key generated successfully!");
        System.out.println();
        System.out.println("Fingerprint: " + fingerprint);
        System.out.println("Stored in:   " + sqHomeDir.toAbsolutePath());
        System.out.println();
        System.out.println("Use this fingerprint with the 'sign' command.");
    }

    /**
     * Prints a user-friendly error message to stderr.
     * <p>
     * This method extracts the most relevant error message from the exception
     * chain and displays it in a clear format. The full stack trace is not
     * printed to avoid overwhelming the user.
     * </p>
     *
     * @param e the exception that occurred during key generation
     */
    private void printErrorMessage(Exception e) {
        System.err.println("Error generating PQC key:");
        System.err.println("  " + extractErrorMessage(e));
        System.err.println();
        System.err.println("Make sure the 'sq' command is installed and available on your PATH.");
    }

    /**
     * Extracts a user-friendly error message from an exception.
     * <p>
     * If the exception has a message, it is returned. Otherwise, the simple
     * class name of the exception is returned (e.g., "IOException" instead of
     * "java.io.IOException").
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
