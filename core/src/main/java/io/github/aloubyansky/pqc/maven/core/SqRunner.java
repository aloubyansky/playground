package io.github.aloubyansky.pqc.maven.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wrapper for the Sequoia (sq) command-line tool for PQC key generation,
 * signing, verification, and certificate export.
 * <p>
 * This class provides a Java interface to Sequoia's post-quantum cryptography
 * capabilities, specifically using the ML-DSA-65 + Ed25519 hybrid cipher suite
 * as defined in RFC 9580. All operations are isolated to a specific Sequoia
 * home directory via the SEQUOIA_HOME environment variable.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Initialize with a dedicated Sequoia home directory
 * Path sqHome = Path.of("/tmp/my-sq-keys");
 * SqRunner sq = new SqRunner(sqHome);
 *
 * // Generate a PQC key
 * String fingerprint = sq.generateKey("Alice <alice@example.com>");
 *
 * // Sign an artifact
 * String signature = sq.sign(
 *     Path.of("artifact.jar"),
 *     Path.of("artifact.jar.sig"),
 *     fingerprint
 * );
 *
 * // Verify the signature
 * boolean valid = sq.verify(
 *     Path.of("artifact.jar"),
 *     Path.of("artifact.jar.sig"),
 *     fingerprint
 * );
 *
 * // Export the certificate for distribution
 * String cert = sq.exportCert(fingerprint);
 * }</pre>
 * <p>
 * Note: This class requires the {@code sq} executable to be available on the
 * system PATH or at the location specified via the constructor.
 * </p>
 *
 * @see #isAvailable()
 */
public class SqRunner {

    private static final int TIMEOUT_SECONDS = 60;
    private static final Pattern FINGERPRINT_PATTERN =
        Pattern.compile("(?i)(?:fingerprint:?\\s*)?([0-9A-F]{64})");

    private final String sqExecutable;
    private final Path sequoiaHome;

    /**
     * Constructs an SqRunner using the default "sq" executable.
     *
     * @param sequoiaHome the directory to use as SEQUOIA_HOME for key/cert storage
     * @throws IllegalArgumentException if sequoiaHome is null
     */
    public SqRunner(Path sequoiaHome) {
        this("sq", sequoiaHome);
    }

    /**
     * Constructs an SqRunner with a custom sq executable path.
     *
     * @param sqExecutable the path to the sq executable (e.g., "sq" or "/usr/local/bin/sq")
     * @param sequoiaHome the directory to use as SEQUOIA_HOME for key/cert storage
     * @throws IllegalArgumentException if sqExecutable or sequoiaHome is null, or if
     *         sqExecutable is empty
     */
    public SqRunner(String sqExecutable, Path sequoiaHome) {
        if (sqExecutable == null || sqExecutable.isEmpty()) {
            throw new IllegalArgumentException("sqExecutable cannot be null or empty");
        }
        if (sequoiaHome == null) {
            throw new IllegalArgumentException("sequoiaHome cannot be null");
        }
        this.sqExecutable = sqExecutable;
        this.sequoiaHome = sequoiaHome;
    }

    /**
     * Generates a new PQC key using the ML-DSA-65 + Ed25519 hybrid cipher suite.
     * <p>
     * This method runs:
     * {@code sq key generate --userid <userId> --cipher-suite mldsa65-ed25519
     * --profile rfc9580 --own-key}
     * </p>
     * <p>
     * The key is stored in the SEQUOIA_HOME directory and can be used for signing
     * operations via the returned fingerprint.
     * </p>
     *
     * @param userId the user ID for the key (e.g., "Alice <alice@example.com>")
     * @return the 64-character hexadecimal fingerprint of the generated key
     * @throws IllegalArgumentException if userId is null or empty
     * @throws CliTool.CliException if the sq command fails
     * @throws IllegalStateException if the fingerprint cannot be parsed from the output
     */
    public String generateKey(String userId) {
        if (userId == null || userId.isEmpty()) {
            throw new IllegalArgumentException("userId cannot be null or empty");
        }

        String[] args = {
            "key", "generate",
            "--userid", userId,
            "--cipher-suite", "mldsa65-ed25519",
            "--profile", "rfc9580",
            "--own-key",
            "--without-password"
        };

        CliTool.Result result = runSq(args);
        // sq may output key info to stdout or stderr depending on mode
        String combinedOutput = result.stdout() + "\n" + result.stderr();
        return extractFingerprint(combinedOutput);
    }

    /**
     * Creates a detached signature for the specified artifact file.
     * <p>
     * This method runs:
     * {@code sq sign --detached --signer <fingerprint> --signature-file <outputSig>
     * <artifactFile>}
     * </p>
     *
     * @param artifactFile the file to sign
     * @param outputSig the path where the signature file will be written
     * @param fingerprint the fingerprint of the signing key
     * @return the armored signature content as a String
     * @throws IllegalArgumentException if any parameter is null or if fingerprint is empty
     * @throws CliTool.CliException if the sq command fails
     * @throws java.io.UncheckedIOException if reading the signature file fails
     */
    public String sign(Path artifactFile, Path outputSig, String fingerprint) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (outputSig == null) {
            throw new IllegalArgumentException("outputSig cannot be null");
        }
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("fingerprint cannot be null or empty");
        }

        // --signature-file implies detached signing (no --detached flag needed)
        String[] args = {
            "sign",
            "--signer", fingerprint,
            "--signature-file", outputSig.toString(),
            artifactFile.toString()
        };

        runSq(args);
        return readSignatureFile(outputSig);
    }

    /**
     * Verifies a detached signature for the specified artifact file.
     * <p>
     * This method runs:
     * {@code sq verify --signer <fingerprint> --signature-file <signatureFile>
     * <artifactFile>}
     * </p>
     *
     * @param artifactFile the file that was signed
     * @param signatureFile the detached signature file
     * @param signerFingerprint the expected signer's fingerprint, or null to skip
     *                          signer verification
     * @return true if the signature is valid, false otherwise
     * @throws IllegalArgumentException if artifactFile or signatureFile is null
     */
    public boolean verify(Path artifactFile, Path signatureFile, String signerFingerprint) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (signatureFile == null) {
            throw new IllegalArgumentException("signatureFile cannot be null");
        }

        String[] args = buildVerifyCommand(artifactFile, signatureFile, signerFingerprint);

        try {
            runSq(args);
            return true;
        } catch (CliTool.CliException e) {
            return false;
        }
    }

    /**
     * Exports the certificate for the specified key fingerprint.
     * <p>
     * This method runs:
     * {@code sq cert export --cert <fingerprint>}
     * </p>
     * <p>
     * The exported certificate can be distributed to others for signature verification.
     * </p>
     *
     * @param fingerprint the fingerprint of the certificate to export
     * @return the armored certificate as a String
     * @throws IllegalArgumentException if fingerprint is null or empty
     * @throws CliTool.CliException if the sq command fails
     */
    public String exportCert(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("fingerprint cannot be null or empty");
        }

        String[] args = {
            "cert", "export",
            "--cert", fingerprint
        };

        CliTool.Result result = runSq(args);
        return result.stdout();
    }

    /**
     * Checks if the sq executable is available and functional.
     * <p>
     * This method runs {@code sq version} and returns true if the command
     * succeeds (exit code 0). This can be used to verify that Sequoia is properly
     * installed before attempting to use the runner.
     * </p>
     *
     * @return true if sq is available and responds to version command, false otherwise
     */
    public static boolean isAvailable() {
        try {
            CliTool.Result result = CliTool.run("sq", "version");
            return result.exitCode() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs an sq command with the SEQUOIA_HOME environment variable set.
     * <p>
     * This is a specialized version of CliTool.run() that sets the SEQUOIA_HOME
     * environment variable to isolate key storage and configuration.
     * </p>
     *
     * @param args the sq command arguments (without the executable name)
     * @return the result of the command execution
     * @throws CliTool.CliException if the exit code is non-zero
     * @throws UncheckedIOException if an I/O error occurs during execution
     * @throws RuntimeException if the process is interrupted or times out
     */
    private CliTool.Result runSq(String... args) {
        List<String> command = buildCommand(args);
        ProcessBuilder pb = new ProcessBuilder(command);
        configureEnvironment(pb);

        Process process = startProcess(pb);
        // Read stdout and stderr concurrently to avoid pipe buffer deadlock
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getErrorStream()));
        String stdout = readStream(process.getInputStream());
        String stderr = stderrFuture.join();
        int exitCode = waitForCompletion(process);

        CliTool.Result result = new CliTool.Result(exitCode, stdout, stderr);
        checkExitCode(result, command);

        return result;
    }

    /**
     * Builds the complete command list by prepending the sq executable.
     *
     * @param args the sq command arguments
     * @return the complete command list
     */
    private List<String> buildCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add(sqExecutable);
        command.add("--overwrite");
        for (String arg : args) {
            command.add(arg);
        }
        return command;
    }

    /**
     * Configures the ProcessBuilder environment with SEQUOIA_HOME.
     *
     * @param pb the ProcessBuilder to configure
     */
    private void configureEnvironment(ProcessBuilder pb) {
        pb.environment().put("SEQUOIA_HOME", sequoiaHome.toString());
    }

    /**
     * Starts the process using the provided ProcessBuilder.
     *
     * @param pb the ProcessBuilder configured with the command
     * @return the started Process
     * @throws UncheckedIOException if an I/O error occurs
     */
    private Process startProcess(ProcessBuilder pb) {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start sq process", e);
        }
    }

    /**
     * Waits for the process to complete within the timeout period.
     *
     * @param process the process to wait for
     * @return the exit code of the process
     * @throws RuntimeException if the process times out or is interrupted
     */
    private int waitForCompletion(Process process) {
        try {
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException(
                    "sq process did not complete within " + TIMEOUT_SECONDS + " seconds"
                );
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new RuntimeException("sq process was interrupted", e);
        }
    }

    /**
     * Reads all content from an InputStream and returns it as a String.
     *
     * @param inputStream the stream to read from
     * @return the complete content of the stream as a String
     * @throws UncheckedIOException if an I/O error occurs
     */
    private String readStream(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append(System.lineSeparator());
                }
                sb.append(line);
            }
            return sb.toString();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read sq output stream", e);
        }
    }

    /**
     * Checks the exit code and throws CliException if non-zero.
     *
     * @param result the command result
     * @param command the command that was executed
     * @throws CliTool.CliException if the exit code is non-zero
     */
    private void checkExitCode(CliTool.Result result, List<String> command) {
        if (result.exitCode() != 0) {
            String commandStr = String.join(" ", command);
            String message = String.format(
                "Command '%s' failed with exit code %d%s",
                commandStr,
                result.exitCode(),
                result.stderr().isEmpty() ? "" : ": " + result.stderr().trim()
            );
            throw new CliTool.CliException(message, result.exitCode());
        }
    }

    /**
     * Builds the verify command array, optionally including the signer fingerprint.
     *
     * @param artifactFile the file that was signed
     * @param signatureFile the signature file
     * @param signerFingerprint the expected signer's fingerprint, or null
     * @return the verify command arguments
     */
    private String[] buildVerifyCommand(Path artifactFile, Path signatureFile,
                                        String signerFingerprint) {
        List<String> args = new ArrayList<>();
        args.add("verify");

        if (signerFingerprint != null && !signerFingerprint.isEmpty()) {
            args.add("--signer");
            args.add(signerFingerprint);
        }

        args.add("--signature-file");
        args.add(signatureFile.toString());
        args.add(artifactFile.toString());

        return args.toArray(new String[0]);
    }

    /**
     * Extracts the key fingerprint from sq key generate output.
     * <p>
     * This method tries multiple patterns to handle different output formats:
     * <ul>
     *   <li>"Fingerprint: ABCD..." (with label)</li>
     *   <li>"fingerprint: abcd..." (case-insensitive)</li>
     *   <li>Bare 64-character hex string on its own line</li>
     * </ul>
     * </p>
     *
     * @param output the stdout from sq key generate
     * @return the extracted fingerprint
     * @throws IllegalStateException if no valid fingerprint is found
     */
    private String extractFingerprint(String output) {
        if (output == null || output.isEmpty()) {
            throw new IllegalStateException("Cannot extract fingerprint from empty output");
        }

        String[] lines = output.split("\\r?\\n");
        for (String line : lines) {
            Matcher matcher = FINGERPRINT_PATTERN.matcher(line.trim());
            if (matcher.find()) {
                return matcher.group(1).toUpperCase();
            }
        }

        throw new IllegalStateException(
            "Failed to extract fingerprint from sq output: " + output
        );
    }

    /**
     * Reads the signature file content and returns it as a string.
     *
     * @param signatureFile the signature file to read
     * @return the signature content
     * @throws UncheckedIOException if reading fails
     */
    private String readSignatureFile(Path signatureFile) {
        try {
            return Files.readString(signatureFile);
        } catch (IOException e) {
            throw new UncheckedIOException(
                "Failed to read signature file: " + signatureFile,
                e
            );
        }
    }
}
