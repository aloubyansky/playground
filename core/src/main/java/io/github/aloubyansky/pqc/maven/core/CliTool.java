package io.github.aloubyansky.pqc.maven.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for executing command-line tools and capturing their output.
 * <p>
 * This class provides a simple interface for running external processes via
 * {@link ProcessBuilder}, capturing both stdout and stderr, and handling
 * exit codes. It supports both checked and unchecked execution modes.
 * </p>
 * <p>
 * Example usage:
 * <pre>{@code
 * // Unchecked execution - returns result regardless of exit code
 * Result result = CliTool.run("echo", "hello");
 * if (result.exitCode() == 0) {
 *     System.out.println(result.stdout());
 * }
 *
 * // Checked execution - throws CliException on non-zero exit
 * try {
 *     CliTool.runChecked("gpg", "--version");
 * } catch (CliException e) {
 *     System.err.println("Command failed: " + e.getMessage());
 * }
 * }</pre>
 */
public final class CliTool {

    private static final int TIMEOUT_SECONDS = 60;

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private CliTool() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Executes a command and returns the result without throwing an exception
     * on non-zero exit codes.
     * <p>
     * The process is given up to 60 seconds to complete. Both stdout and stderr
     * are captured and returned in the {@link Result}.
     * </p>
     *
     * @param command the command and its arguments to execute
     * @return a {@link Result} containing exit code, stdout, and stderr
     * @throws IllegalArgumentException if command is null or empty
     * @throws UncheckedIOException if an I/O error occurs during execution
     * @throws RuntimeException if the process is interrupted or times out
     */
    public static Result run(String... command) {
        validateCommand(command);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = startProcess(pb);

        // Read stdout and stderr concurrently to avoid pipe buffer deadlock
        CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                () -> readStream(process.getErrorStream()));
        String stdout = readStream(process.getInputStream());
        String stderr = stderrFuture.join();

        int exitCode = waitForCompletion(process);

        return new Result(exitCode, stdout, stderr);
    }

    /**
     * Executes a command and throws {@link CliException} if the exit code is non-zero.
     * <p>
     * This is a convenience method for cases where command failure should be
     * treated as an exceptional condition. The exception message includes the
     * command, exit code, and stderr output.
     * </p>
     *
     * @param command the command and its arguments to execute
     * @return a {@link Result} containing exit code 0, stdout, and stderr
     * @throws CliException if the exit code is non-zero
     * @throws IllegalArgumentException if command is null or empty
     * @throws UncheckedIOException if an I/O error occurs during execution
     * @throws RuntimeException if the process is interrupted or times out
     */
    public static Result runChecked(String... command) {
        Result result = run(command);

        if (result.exitCode() != 0) {
            String commandStr = String.join(" ", command);
            String message = String.format(
                "Command '%s' failed with exit code %d%s",
                commandStr,
                result.exitCode(),
                result.stderr().isEmpty() ? "" : ": " + result.stderr().trim()
            );
            throw new CliException(message, result.exitCode());
        }

        return result;
    }

    /**
     * Validates that the command array is not null or empty.
     *
     * @param command the command to validate
     * @throws IllegalArgumentException if command is null or empty
     */
    private static void validateCommand(String... command) {
        if (command == null || command.length == 0) {
            throw new IllegalArgumentException("Command cannot be null or empty");
        }
    }

    /**
     * Starts the process using the provided ProcessBuilder.
     *
     * @param pb the ProcessBuilder configured with the command
     * @return the started Process
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static Process startProcess(ProcessBuilder pb) {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to start process", e);
        }
    }

    /**
     * Waits for the process to complete within the timeout period.
     *
     * @param process the process to wait for
     * @return the exit code of the process
     * @throws RuntimeException if the process times out or is interrupted
     */
    private static int waitForCompletion(Process process) {
        try {
            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new RuntimeException(
                    "Process did not complete within " + TIMEOUT_SECONDS + " seconds"
                );
            }
            return process.exitValue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new RuntimeException("Process was interrupted", e);
        }
    }

    /**
     * Reads all content from an InputStream and returns it as a String.
     * <p>
     * This method reads the stream line by line, joining them with system
     * line separators. The stream is automatically closed after reading.
     * </p>
     *
     * @param inputStream the stream to read from
     * @return the complete content of the stream as a String
     * @throws UncheckedIOException if an I/O error occurs
     */
    private static String readStream(InputStream inputStream) {
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
            throw new UncheckedIOException("Failed to read stream", e);
        }
    }

    /**
     * Represents the result of executing a command-line tool.
     *
     * @param exitCode the process exit code (0 typically indicates success)
     * @param stdout the complete stdout output as a string
     * @param stderr the complete stderr output as a string
     */
    public record Result(int exitCode, String stdout, String stderr) {
    }

    /**
     * Exception thrown when a command executed via {@link #runChecked(String...)}
     * returns a non-zero exit code.
     */
    public static class CliException extends RuntimeException {

        private final int exitCode;

        /**
         * Constructs a new CliException with the specified message and exit code.
         *
         * @param message the detail message
         * @param exitCode the exit code from the failed command
         */
        public CliException(String message, int exitCode) {
            super(message);
            this.exitCode = exitCode;
        }

        /**
         * Returns the exit code from the failed command.
         *
         * @return the exit code
         */
        public int getExitCode() {
            return exitCode;
        }
    }
}
