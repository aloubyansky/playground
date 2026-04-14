package io.github.aloubyansky.pqc.maven.core;

/**
 * Represents the outcome of a signature verification operation.
 * <p>
 * This enum is used by {@link HybridVerifier} to report the result of both
 * classic (GPG) and PQC (Sequoia) signature verification. It distinguishes
 * between successful verification, failed verification, missing keys, and
 * absent signatures.
 * </p>
 *
 * @see HybridVerifier
 * @see VerificationReport
 */
public enum VerificationResult {

    /**
     * The signature is valid and verification passed.
     * <p>
     * This indicates that the signature was successfully verified against the
     * expected public key and the signed data matches the signature.
     * </p>
     */
    PASS,

    /**
     * The signature verification failed.
     * <p>
     * This indicates that either:
     * <ul>
     *   <li>The signature is invalid (does not match the signed data)</li>
     *   <li>The signature was created by a different key than expected</li>
     *   <li>The signed data has been modified since signing</li>
     * </ul>
     * </p>
     */
    FAIL,

    /**
     * The required key for verification is not available.
     * <p>
     * This indicates that the verification tool does not have access to the
     * public key needed to verify the signature. This is distinct from
     * {@link #FAIL}, as it represents a configuration issue rather than an
     * invalid signature.
     * </p>
     */
    NO_KEY,

    /**
     * The signature is not present in the signature file.
     * <p>
     * This is used specifically for PQC verification when the signature file
     * contains only a classic GPG signature and no PQC signature. This allows
     * {@link VerificationReport} to distinguish between classic-only signatures
     * and hybrid signatures where the PQC component failed.
     * </p>
     */
    NOT_PRESENT
}
