package io.github.aloubyansky.pqc.maven.plugin;

import io.github.aloubyansky.pqc.maven.core.GpgSigner;
import io.github.aloubyansky.pqc.maven.core.HybridVerifier;
import io.github.aloubyansky.pqc.maven.core.SqRunner;
import io.github.aloubyansky.pqc.maven.core.VerificationReport;
import io.github.aloubyansky.pqc.maven.core.VerificationResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.nio.file.Path;

/**
 * Maven plugin goal that verifies hybrid signatures containing both classical
 * GPG and post-quantum cryptography components.
 * <p>
 * This Mojo can be used standalone (without a project) to verify any signed
 * artifact. It supports both strict mode (requiring both GPG and PQC signatures)
 * and transitional mode (requiring only GPG signature).
 * </p>
 * <p>
 * Example command-line usage:
 * <pre>{@code
 * mvn io.github.aloubyansky.pqc.maven:pqc-sign-maven-plugin:verify \
 *   -Dfile=artifact.jar \
 *   -Dsignature=artifact.jar.asc \
 *   -Dpqc.fingerprint=ABC123... \
 *   -Dpqc.strict=true
 * }</pre>
 * <p>
 * Example plugin configuration:
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.aloubyansky.pqc.maven</groupId>
 *   <artifactId>pqc-sign-maven-plugin</artifactId>
 *   <version>1.0.0-SNAPSHOT</version>
 *   <executions>
 *     <execution>
 *       <goals>
 *         <goal>verify</goal>
 *       </goals>
 *       <configuration>
 *         <file>${project.build.directory}/${project.build.finalName}.jar</file>
 *         <signature>${project.build.directory}/${project.build.finalName}.jar.asc</signature>
 *         <pqcFingerprint>ABC123...</pqcFingerprint>
 *         <strict>true</strict>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * @see HybridVerifier
 * @see VerificationReport
 */
@Mojo(name = "verify", requiresProject = false, threadSafe = true)
public class VerifyMojo extends AbstractMojo {

    /**
     * The file to verify.
     * <p>
     * This is the artifact file that was signed (e.g., a JAR, WAR, or POM file).
     * </p>
     */
    @Parameter(property = "file", required = true)
    private File file;

    /**
     * The detached signature file.
     * <p>
     * This is typically the artifact filename with a .asc extension
     * (e.g., {@code artifact.jar.asc}).
     * </p>
     */
    @Parameter(property = "signature", required = true)
    private File signature;

    /**
     * Expected PQC key fingerprint (64-character hexadecimal).
     * <p>
     * If specified, the verifier will check that the PQC signature was
     * created by this specific key. If not specified, any valid PQC
     * signature will be accepted.
     * </p>
     */
    @Parameter(property = "pqc.fingerprint")
    private String pqcFingerprint;

    /**
     * Path to the Sequoia home directory containing PQC keys/certificates.
     * <p>
     * If not specified, defaults to {@code ~/.local/share/sequoia}.
     * </p>
     */
    @Parameter(property = "pqc.sqHome")
    private File sqHome;

    /**
     * Whether to enforce strict verification mode.
     * <p>
     * In strict mode, both GPG and PQC signatures must be present and valid.
     * In transitional mode (default), only the GPG signature must be valid,
     * allowing for backward compatibility with classic-only signatures.
     * </p>
     */
    @Parameter(property = "pqc.strict", defaultValue = "false")
    private boolean strict;

    /**
     * Executes the verification process.
     * <p>
     * This method orchestrates the entire verification workflow:
     * <ol>
     *   <li>Validates input files exist</li>
     *   <li>Resolves the Sequoia home directory</li>
     *   <li>Creates the hybrid verifier</li>
     *   <li>Verifies the signature</li>
     *   <li>Logs the verification report</li>
     *   <li>Throws exception if verification fails per policy</li>
     * </ol>
     * </p>
     *
     * @throws MojoExecutionException if verification fails or files are missing
     */
    @Override
    public void execute() throws MojoExecutionException {
        validateInputFiles();

        getLog().info("Verifying signature for: " + file.getName());
        getLog().info("Using signature file: " + signature.getName());

        Path sequoiaHome = resolveSequoiaHome();
        HybridVerifier verifier = createVerifier(sequoiaHome);
        VerificationReport report = performVerification(verifier);

        logReport(report);
        checkVerificationResult(report);
    }

    /**
     * Validates that the input files exist and are readable.
     *
     * @throws MojoExecutionException if files don't exist
     */
    private void validateInputFiles() throws MojoExecutionException {
        if (!file.exists()) {
            throw new MojoExecutionException("File does not exist: " + file.getAbsolutePath());
        }
        if (!signature.exists()) {
            throw new MojoExecutionException(
                "Signature file does not exist: " + signature.getAbsolutePath()
            );
        }
    }

    /**
     * Resolves the Sequoia home directory, using the default if not specified.
     * <p>
     * The default location is {@code ~/.local/share/sequoia}, which matches
     * the Sequoia CLI tool's default behavior.
     * </p>
     *
     * @return the resolved Sequoia home path
     * @throws MojoExecutionException if the path cannot be resolved
     */
    private Path resolveSequoiaHome() throws MojoExecutionException {
        if (sqHome != null) {
            return sqHome.toPath();
        }

        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isEmpty()) {
            throw new MojoExecutionException(
                "Cannot resolve Sequoia home: user.home property not set"
            );
        }

        return Path.of(userHome, ".local", "share", "sequoia");
    }

    /**
     * Creates a HybridVerifier configured with GPG and Sequoia tools.
     * <p>
     * If Sequoia is not available, the verifier will still work but will
     * only be able to verify GPG signatures (PQC result will be NOT_PRESENT).
     * </p>
     *
     * @param sequoiaHome the path to the Sequoia home directory
     * @return a configured HybridVerifier instance
     * @throws MojoExecutionException if verifier creation fails
     */
    private HybridVerifier createVerifier(Path sequoiaHome) throws MojoExecutionException {
        try {
            GpgSigner gpg = new GpgSigner(null);
            SqRunner sq = createSqRunner(sequoiaHome);
            return new HybridVerifier(gpg, sq, pqcFingerprint);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create hybrid verifier", e);
        }
    }

    /**
     * Creates a SqRunner if Sequoia is available, otherwise returns null.
     * <p>
     * This allows graceful fallback to GPG-only verification when Sequoia
     * is not installed or not in the PATH.
     * </p>
     *
     * @param sequoiaHome the Sequoia home directory
     * @return a SqRunner instance, or null if Sequoia is not available
     */
    private SqRunner createSqRunner(Path sequoiaHome) {
        if (SqRunner.isAvailable()) {
            return new SqRunner(sequoiaHome);
        } else {
            getLog().warn("Sequoia (sq) not found - PQC verification will be skipped");
            return null;
        }
    }

    /**
     * Performs the signature verification.
     *
     * @param verifier the hybrid verifier to use
     * @return the verification report
     * @throws MojoExecutionException if verification process fails
     */
    private VerificationReport performVerification(HybridVerifier verifier)
            throws MojoExecutionException {
        try {
            return verifier.verify(file.toPath(), signature.toPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Verification failed", e);
        }
    }

    /**
     * Logs the verification report to the Maven console.
     * <p>
     * The report includes details about both GPG and PQC verification results.
     * </p>
     *
     * @param report the verification report to log
     */
    private void logReport(VerificationReport report) {
        getLog().info("");
        getLog().info("========================================");
        for (String line : report.format().split("\n")) {
            getLog().info(line);
        }
        getLog().info("========================================");
        getLog().info("");
    }

    /**
     * Checks the verification result against the configured policy.
     * <p>
     * This method enforces the verification policy:
     * <ul>
     *   <li>In strict mode: both GPG and PQC must pass</li>
     *   <li>In transitional mode: only GPG must pass</li>
     *   <li>Always fails if PQC result is FAIL (regardless of mode)</li>
     * </ul>
     * </p>
     *
     * @param report the verification report
     * @throws MojoExecutionException if verification fails per policy
     */
    private void checkVerificationResult(VerificationReport report)
            throws MojoExecutionException {
        // Always fail on PQC FAIL (not NO_KEY or NOT_PRESENT)
        if (report.pqcResult() == VerificationResult.FAIL) {
            throw new MojoExecutionException(
                "PQC signature verification failed - signature is invalid"
            );
        }

        // In strict mode, require both signatures to pass
        if (strict && !report.isStrictPass()) {
            if (report.pqcResult() == VerificationResult.NOT_PRESENT) {
                throw new MojoExecutionException(
                    "Strict mode requires PQC signature, but only classic signature is present"
                );
            } else if (report.pqcResult() == VerificationResult.NO_KEY) {
                throw new MojoExecutionException(
                    "Strict mode requires PQC signature verification, but PQC key is not available"
                );
            } else {
                throw new MojoExecutionException(
                    "Strict mode requires both signatures to pass"
                );
            }
        }

        // In any mode, require at least the classic signature to pass
        if (!report.isTransitionalPass()) {
            throw new MojoExecutionException(
                "Classic GPG signature verification failed"
            );
        }

        getLog().info("Verification successful!");
    }
}
