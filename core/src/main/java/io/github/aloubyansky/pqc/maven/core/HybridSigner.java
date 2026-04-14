package io.github.aloubyansky.pqc.maven.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Orchestrates hybrid signing combining classical GPG (v4 packet) and
 * post-quantum cryptography via Sequoia (v6 packet) into a single .asc file.
 * <p>
 * This class coordinates two separate signing operations:
 * <ol>
 *   <li>Classical signature using GPG</li>
 *   <li>Post-quantum signature using Sequoia's ML-DSA-65 + Ed25519 cipher suite</li>
 * </ol>
 * The two ASCII-armored signatures are then combined into a single armored block
 * using {@link AscCombiner}, resulting in a hybrid signature that provides both
 * classical and post-quantum security.
 * </p>
 * <p>
 * Example usage with real tools:
 * <pre>{@code
 * GpgSigner gpg = new GpgSigner(null);
 * SqRunner sq = new SqRunner(Path.of("/tmp/sq-keys"));
 * String pqcFingerprint = sq.generateKey("Alice <alice@example.com>");
 *
 * HybridSigner signer = HybridSigner.create(gpg, sq, pqcFingerprint);
 * signer.sign(Path.of("artifact.jar"), Path.of("artifact.jar.asc"));
 * }</pre>
 * <p>
 * For testing purposes, the constructor accepts functional interfaces that can
 * be easily mocked:
 * <pre>{@code
 * HybridSigner signer = new HybridSigner(
 *     (file, output) -> mockClassicSign(file, output),
 *     (file, output, fp) -> mockPqcSign(file, output, fp)
 * );
 * }</pre>
 * </p>
 *
 * @see AscCombiner
 * @see GpgSigner
 * @see SqRunner
 */
public class HybridSigner {

    /**
     * Functional interface for classical signing operations.
     * <p>
     * Implementations should create a detached ASCII-armored signature for the
     * provided artifact file and write it to the output path.
     * </p>
     */
    @FunctionalInterface
    public interface ClassicSignFn {
        /**
         * Signs the artifact file and writes the signature to the output path.
         *
         * @param artifactFile the file to sign
         * @param outputSig the path where the signature will be written
         * @return the ASCII-armored signature content
         * @throws Exception if signing fails
         */
        String sign(Path artifactFile, Path outputSig) throws Exception;
    }

    /**
     * Functional interface for post-quantum signing operations.
     * <p>
     * Implementations should create a detached ASCII-armored signature using
     * the specified key fingerprint and write it to the output path.
     * </p>
     */
    @FunctionalInterface
    public interface PqcSignFn {
        /**
         * Signs the artifact file using the specified key fingerprint.
         *
         * @param artifactFile the file to sign
         * @param outputSig the path where the signature will be written
         * @param fingerprint the PQC key fingerprint to use for signing
         * @return the ASCII-armored signature content
         * @throws Exception if signing fails
         */
        String sign(Path artifactFile, Path outputSig, String fingerprint) throws Exception;
    }

    private final ClassicSignFn classicSign;
    private final PqcSignFn pqcSign;
    private String pqcFingerprint;

    /**
     * Constructs a HybridSigner with custom signing functions.
     * <p>
     * This constructor is primarily intended for testing with mock signers.
     * For production use, consider using {@link #create(GpgSigner, SqRunner, String)}
     * instead.
     * </p>
     *
     * @param classicSign the classical signing function
     * @param pqcSign the post-quantum signing function
     * @throws IllegalArgumentException if either parameter is null
     */
    public HybridSigner(ClassicSignFn classicSign, PqcSignFn pqcSign) {
        if (classicSign == null) {
            throw new IllegalArgumentException("classicSign cannot be null");
        }
        if (pqcSign == null) {
            throw new IllegalArgumentException("pqcSign cannot be null");
        }
        this.classicSign = classicSign;
        this.pqcSign = pqcSign;
    }

    /**
     * Sets the PQC key fingerprint to use for signing.
     * <p>
     * This method supports method chaining for convenient configuration.
     * </p>
     *
     * @param fingerprint the 64-character hexadecimal fingerprint
     * @return this HybridSigner instance for method chaining
     * @throws IllegalArgumentException if fingerprint is null or empty
     */
    public HybridSigner withPqcFingerprint(String fingerprint) {
        if (fingerprint == null || fingerprint.isEmpty()) {
            throw new IllegalArgumentException("fingerprint cannot be null or empty");
        }
        this.pqcFingerprint = fingerprint;
        return this;
    }

    /**
     * Creates a hybrid signature combining classical and post-quantum signatures.
     * <p>
     * This method performs the following steps:
     * <ol>
     *   <li>Creates temporary files for individual signatures</li>
     *   <li>Generates classical signature using GPG</li>
     *   <li>Generates PQC signature using Sequoia</li>
     *   <li>Combines both signatures into a single armored block</li>
     *   <li>Writes the combined signature to the output file</li>
     *   <li>Cleans up temporary files</li>
     * </ol>
     * </p>
     *
     * @param artifactFile the file to sign
     * @param outputAsc the path where the combined .asc signature will be written
     * @throws IllegalArgumentException if artifactFile or outputAsc is null
     * @throws IllegalStateException if pqcFingerprint has not been set
     * @throws Exception if any signing operation fails
     */
    public void sign(Path artifactFile, Path outputAsc) throws Exception {
        validateSignParameters(artifactFile, outputAsc);

        Path tempClassicSig = null;
        Path tempPqcSig = null;

        try {
            tempClassicSig = createTempFile("classic-sig");
            tempPqcSig = createTempFile("pqc-sig");

            String classicAsc = generateClassicSignature(artifactFile, tempClassicSig);
            String pqcAsc = generatePqcSignature(artifactFile, tempPqcSig);
            String combinedAsc = combineSignatures(classicAsc, pqcAsc);

            writeOutputFile(outputAsc, combinedAsc);
        } finally {
            cleanupTempFiles(tempClassicSig, tempPqcSig);
        }
    }

    /**
     * Factory method that creates a HybridSigner wired with real GPG and Sequoia tools.
     * <p>
     * This is the recommended way to create a HybridSigner for production use.
     * </p>
     *
     * @param gpg the GpgSigner instance to use for classical signing
     * @param sq the SqRunner instance to use for PQC signing
     * @param pqcFingerprint the PQC key fingerprint to use for signing
     * @return a new HybridSigner instance configured with the provided tools
     * @throws IllegalArgumentException if any parameter is null or pqcFingerprint is empty
     */
    public static HybridSigner create(GpgSigner gpg, SqRunner sq, String pqcFingerprint) {
        if (gpg == null) {
            throw new IllegalArgumentException("gpg cannot be null");
        }
        if (sq == null) {
            throw new IllegalArgumentException("sq cannot be null");
        }
        if (pqcFingerprint == null || pqcFingerprint.isEmpty()) {
            throw new IllegalArgumentException("pqcFingerprint cannot be null or empty");
        }

        return new HybridSigner(gpg::sign, sq::sign)
            .withPqcFingerprint(pqcFingerprint);
    }

    /**
     * Validates the parameters passed to the sign method.
     *
     * @param artifactFile the artifact file to validate
     * @param outputAsc the output path to validate
     * @throws IllegalArgumentException if validation fails
     * @throws IllegalStateException if pqcFingerprint is not set
     */
    private void validateSignParameters(Path artifactFile, Path outputAsc) {
        if (artifactFile == null) {
            throw new IllegalArgumentException("artifactFile cannot be null");
        }
        if (outputAsc == null) {
            throw new IllegalArgumentException("outputAsc cannot be null");
        }
        if (pqcFingerprint == null || pqcFingerprint.isEmpty()) {
            throw new IllegalStateException(
                "pqcFingerprint must be set before signing (use withPqcFingerprint())"
            );
        }
    }

    /**
     * Creates a temporary file with the specified prefix.
     *
     * @param prefix the prefix for the temporary file name
     * @return the path to the created temporary file
     * @throws IOException if file creation fails
     */
    private Path createTempFile(String prefix) throws IOException {
        return Files.createTempFile(prefix, ".asc");
    }

    /**
     * Generates a classical signature using the configured classic signing function.
     *
     * @param artifactFile the file to sign
     * @param tempSig the temporary file for the signature
     * @return the ASCII-armored classical signature
     * @throws Exception if signing fails
     */
    private String generateClassicSignature(Path artifactFile, Path tempSig) throws Exception {
        return classicSign.sign(artifactFile, tempSig);
    }

    /**
     * Generates a PQC signature using the configured PQC signing function.
     *
     * @param artifactFile the file to sign
     * @param tempSig the temporary file for the signature
     * @return the ASCII-armored PQC signature
     * @throws Exception if signing fails
     */
    private String generatePqcSignature(Path artifactFile, Path tempSig) throws Exception {
        return pqcSign.sign(artifactFile, tempSig, pqcFingerprint);
    }

    /**
     * Combines classical and PQC signatures into a single armored block.
     *
     * @param classicAsc the classical ASCII-armored signature
     * @param pqcAsc the PQC ASCII-armored signature
     * @return the combined ASCII-armored signature
     */
    private String combineSignatures(String classicAsc, String pqcAsc) {
        return AscCombiner.combine(classicAsc, pqcAsc);
    }

    /**
     * Writes the combined signature to the output file.
     *
     * @param outputAsc the output file path
     * @param combinedAsc the combined signature content
     * @throws IOException if writing fails
     */
    private void writeOutputFile(Path outputAsc, String combinedAsc) throws IOException {
        Files.writeString(outputAsc, combinedAsc);
    }

    /**
     * Cleans up temporary files in a safe manner, ignoring any deletion errors.
     *
     * @param tempFiles the temporary file paths to delete (may be null)
     */
    private void cleanupTempFiles(Path... tempFiles) {
        for (Path tempFile : tempFiles) {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Ignore cleanup errors - best effort
                }
            }
        }
    }
}
