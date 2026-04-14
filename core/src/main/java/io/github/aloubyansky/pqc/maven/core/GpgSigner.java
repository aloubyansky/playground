package io.github.aloubyansky.pqc.maven.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for the GnuPG (gpg) command-line tool for classic detached signing.
 * <p>
 * This class provides a simple Java interface to GPG's signing functionality,
 * specifically for creating detached ASCII-armored signatures. It supports
 * both the default GPG key and explicitly specified keys via the --local-user
 * option.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Using the default GPG key
 * GpgSigner signer = new GpgSigner(null);
 * String signature = signer.sign(
 *     Path.of("artifact.jar"),
 *     Path.of("artifact.jar.asc")
 * );
 *
 * // Using a specific key
 * GpgSigner signer = new GpgSigner("user@example.com");
 * String signature = signer.sign(
 *     Path.of("artifact.jar"),
 *     Path.of("artifact.jar.asc")
 * );
 * }</pre>
 * <p>
 * Note: This class requires the {@code gpg} executable to be available on the
 * system PATH or at the location specified via the constructor.
 * </p>
 *
 * @see #isAvailable()
 */
public class GpgSigner {

    private final String gpgExecutable;
    private final String keyName;

    /**
     * Constructs a GpgSigner using the default "gpg" executable.
     *
     * @param keyName the key name/ID to use with --local-user, or null to use
     *                GPG's default key
     */
    public GpgSigner(String keyName) {
        this("gpg", keyName);
    }

    /**
     * Constructs a GpgSigner with a custom GPG executable path.
     *
     * @param gpgExecutable the path to the gpg executable (e.g., "gpg" or "/usr/bin/gpg")
     * @param keyName the key name/ID to use with --local-user, or null to use
     *                GPG's default key
     * @throws IllegalArgumentException if gpgExecutable is null or empty
     */
    public GpgSigner(String gpgExecutable, String keyName) {
        if (gpgExecutable == null || gpgExecutable.isEmpty()) {
            throw new IllegalArgumentException("gpgExecutable cannot be null or empty");
        }
        this.gpgExecutable = gpgExecutable;
        this.keyName = keyName;
    }

    /**
     * Creates a detached ASCII-armored signature for the specified artifact file.
     * <p>
     * This method invokes GPG with the following options:
     * <ul>
     *   <li>--batch: Non-interactive mode</li>
     *   <li>--yes: Assume "yes" for prompts (e.g., overwrite existing signature)</li>
     *   <li>--armor: Create ASCII-armored output</li>
     *   <li>--detach-sign: Create a detached signature</li>
     *   <li>--local-user: Specify signing key (if keyName is set)</li>
     *   <li>--output: Specify output signature file path</li>
     * </ul>
     * </p>
     *
     * @param artifactFile the file to sign
     * @param outputSig the path where the signature file will be written
     * @return the ASCII-armored signature content as a String
     * @throws IllegalArgumentException if artifactFile or outputSig is null
     * @throws CliTool.CliException if the GPG command fails
     * @throws java.io.UncheckedIOException if reading the signature file fails
     */
    public String sign(Path artifactFile, Path outputSig) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (outputSig == null) {
            throw new IllegalArgumentException("outputSig cannot be null");
        }

        String[] command = buildSignCommand(artifactFile, outputSig);
        CliTool.runChecked(command);

        return readSignatureFile(outputSig);
    }

    /**
     * Checks if the GPG executable is available and functional.
     * <p>
     * This method runs {@code gpg --version} and returns true if the command
     * succeeds (exit code 0). This can be used to verify that GPG is properly
     * installed before attempting to use the signer.
     * </p>
     *
     * @return true if GPG is available and responds to --version, false otherwise
     */
    public static boolean isAvailable() {
        try {
            CliTool.Result result = CliTool.run("gpg", "--version");
            return result.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Builds the GPG command array for signing.
     *
     * @param artifactFile the file to sign
     * @param outputSig the output signature file path
     * @return the complete command array
     */
    private String[] buildSignCommand(Path artifactFile, Path outputSig) {
        List<String> command = new ArrayList<>();
        command.add(gpgExecutable);
        command.add("--batch");
        command.add("--yes");
        command.add("--armor");
        command.add("--detach-sign");

        if (keyName != null && !keyName.isEmpty()) {
            command.add("--local-user");
            command.add(keyName);
        }

        command.add("--output");
        command.add(outputSig.toString());
        command.add(artifactFile.toString());

        return command.toArray(new String[0]);
    }

    /**
     * Reads the signature file content and returns it as a string.
     *
     * @param signatureFile the signature file to read
     * @return the signature content
     * @throws java.io.UncheckedIOException if reading fails
     */
    private String readSignatureFile(Path signatureFile) {
        try {
            return Files.readString(signatureFile);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(
                "Failed to read signature file: " + signatureFile,
                e
            );
        }
    }
}
