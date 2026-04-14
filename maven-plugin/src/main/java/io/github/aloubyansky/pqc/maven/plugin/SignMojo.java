package io.github.aloubyansky.pqc.maven.plugin;

import io.github.aloubyansky.pqc.maven.core.GpgSigner;
import io.github.aloubyansky.pqc.maven.core.HybridSigner;
import io.github.aloubyansky.pqc.maven.core.SqRunner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

import javax.inject.Inject;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maven plugin goal that creates hybrid signatures combining classical GPG
 * and post-quantum cryptography for all project artifacts.
 * <p>
 * This Mojo is bound to the VERIFY phase and creates detached .asc signature
 * files for the main artifact, POM, and all attached artifacts. Each signature
 * contains both a classical GPG signature (for backward compatibility) and a
 * PQC signature using ML-DSA-65 + Ed25519.
 * </p>
 * <p>
 * Example configuration:
 * <pre>{@code
 * <plugin>
 *   <groupId>io.github.aloubyansky.pqc.maven</groupId>
 *   <artifactId>pqc-sign-maven-plugin</artifactId>
 *   <version>1.0.0-SNAPSHOT</version>
 *   <executions>
 *     <execution>
 *       <goals>
 *         <goal>sign</goal>
 *       </goals>
 *       <configuration>
 *         <gpgKeyName>user@example.com</gpgKeyName>
 *         <pqcFingerprint>ABC123...</pqcFingerprint>
 *         <sqHome>/path/to/sequoia-keys</sqHome>
 *       </configuration>
 *     </execution>
 *   </executions>
 * </plugin>
 * }</pre>
 *
 * @see HybridSigner
 * @see GpgSigner
 * @see SqRunner
 */
@Mojo(name = "sign", defaultPhase = LifecyclePhase.VERIFY, threadSafe = true)
public class SignMojo extends AbstractMojo {

    /**
     * The Maven project being built.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Maven project helper for attaching artifacts.
     */
    @Inject
    private MavenProjectHelper projectHelper;

    /**
     * GPG key name or email to use for signing.
     * <p>
     * This corresponds to the GPG --local-user option. If not specified,
     * GPG will use its default key from the keyring.
     * </p>
     */
    @Parameter(property = "gpg.keyname")
    private String gpgKeyName;

    /**
     * PQC key fingerprint (64-character hexadecimal) to use for signing.
     * <p>
     * This is required for PQC signing. The key must exist in the Sequoia
     * home directory specified by {@link #sqHome}.
     * </p>
     */
    @Parameter(property = "pqc.fingerprint", required = true)
    private String pqcFingerprint;

    /**
     * Path to the Sequoia home directory containing PQC keys.
     * <p>
     * If not specified, defaults to {@code ~/.local/share/sequoia}.
     * </p>
     */
    @Parameter(property = "pqc.sqHome")
    private File sqHome;

    /**
     * Executes the signing process for all project artifacts.
     * <p>
     * This method orchestrates the entire signing workflow:
     * <ol>
     *   <li>Resolves the Sequoia home directory</li>
     *   <li>Creates the hybrid signer</li>
     *   <li>Collects all files to sign (main artifact, POM, attached artifacts)</li>
     *   <li>Signs each file and attaches the signature</li>
     * </ol>
     * </p>
     *
     * @throws MojoExecutionException if signing fails for any artifact
     */
    @Override
    public void execute() throws MojoExecutionException {
        getLog().info("Starting hybrid signing process...");

        Path sequoiaHome = resolveSequoiaHome();
        HybridSigner signer = createSigner(sequoiaHome);
        List<FileToSign> filesToSign = collectFilesToSign();

        getLog().info("Signing " + filesToSign.size() + " artifact(s)...");

        for (FileToSign fileToSign : filesToSign) {
            signAndAttach(fileToSign, signer);
        }

        getLog().info("Hybrid signing completed successfully");
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
     * Creates a HybridSigner configured with GPG and Sequoia tools.
     * <p>
     * This method initializes the signer with the configured GPG key name
     * and PQC fingerprint.
     * </p>
     *
     * @param sequoiaHome the path to the Sequoia home directory
     * @return a configured HybridSigner instance
     * @throws MojoExecutionException if signer creation fails
     */
    private HybridSigner createSigner(Path sequoiaHome) throws MojoExecutionException {
        try {
            GpgSigner gpg = new GpgSigner(gpgKeyName);
            SqRunner sq = new SqRunner(sequoiaHome);
            return HybridSigner.create(gpg, sq, pqcFingerprint);
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to create hybrid signer", e);
        }
    }

    /**
     * Collects all files that need to be signed.
     * <p>
     * This includes:
     * <ul>
     *   <li>The main artifact file (e.g., JAR, WAR)</li>
     *   <li>The project POM file</li>
     *   <li>All attached artifacts (sources, javadoc, etc.)</li>
     * </ul>
     * Existing .asc signature files are excluded to prevent double-signing.
     * </p>
     *
     * @return a list of FileToSign instances
     */
    private List<FileToSign> collectFilesToSign() {
        List<FileToSign> files = new ArrayList<>();

        // Main artifact
        Artifact mainArtifact = project.getArtifact();
        File mainFile = mainArtifact.getFile();
        if (mainFile != null && mainFile.exists() && !mainFile.getName().endsWith(".asc")) {
            String extension = getExtension(mainFile);
            files.add(new FileToSign(mainFile, extension, null));
            getLog().debug("Added main artifact: " + mainFile.getName());
        }

        // POM file
        File pomFile = project.getFile();
        if (pomFile != null && pomFile.exists()) {
            files.add(new FileToSign(pomFile, "pom", null));
            getLog().debug("Added POM: " + pomFile.getName());
        }

        // Attached artifacts
        for (Artifact artifact : project.getAttachedArtifacts()) {
            File file = artifact.getFile();
            if (file != null && file.exists() && !file.getName().endsWith(".asc")) {
                String extension = getExtension(file);
                String classifier = getClassifier(artifact);
                files.add(new FileToSign(file, extension, classifier));
                getLog().debug("Added attached artifact: " + file.getName() +
                    " (classifier=" + classifier + ")");
            }
        }

        return files;
    }

    /**
     * Signs a file and attaches the signature as an artifact.
     * <p>
     * This method creates a .asc file containing the hybrid signature and
     * attaches it to the Maven project so it will be deployed along with
     * the signed artifact.
     * </p>
     *
     * @param fileToSign the file to sign
     * @param signer the hybrid signer to use
     * @throws MojoExecutionException if signing or attachment fails
     */
    private void signAndAttach(FileToSign fileToSign, HybridSigner signer)
            throws MojoExecutionException {
        File file = fileToSign.file;
        Path artifactPath = file.toPath();
        Path signaturePath = Path.of(file.getAbsolutePath() + ".asc");

        getLog().info("Signing: " + file.getName());

        try {
            signer.sign(artifactPath, signaturePath);
            attachSignature(fileToSign, signaturePath.toFile());
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to sign " + file.getName(), e);
        }
    }

    /**
     * Attaches a signature file as a Maven artifact.
     * <p>
     * The signature is attached with the same extension and classifier as
     * the original file, plus an ".asc" suffix.
     * </p>
     *
     * @param fileToSign the original file metadata
     * @param signatureFile the signature file to attach
     */
    private void attachSignature(FileToSign fileToSign, File signatureFile) {
        String classifier = fileToSign.classifier;
        String extension = fileToSign.extension + ".asc";

        projectHelper.attachArtifact(project, extension, classifier, signatureFile);

        String name = signatureFile.getName();
        if (classifier != null && !classifier.isEmpty()) {
            name += " (classifier=" + classifier + ")";
        }
        getLog().debug("Attached signature: " + name);
    }

    /**
     * Extracts the classifier from an artifact.
     * <p>
     * The classifier is used to distinguish between different variants of
     * the same artifact (e.g., sources, javadoc).
     * </p>
     *
     * @param artifact the artifact to extract from
     * @return the classifier, or null if none
     */
    private String getClassifier(Artifact artifact) {
        String classifier = artifact.getClassifier();
        return (classifier != null && !classifier.isEmpty()) ? classifier : null;
    }

    /**
     * Extracts the file extension from a file.
     * <p>
     * This extracts everything after the last dot in the filename.
     * If there's no dot, returns the entire filename.
     * </p>
     *
     * @param file the file to extract from
     * @return the extension (without the leading dot)
     */
    private String getExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot >= 0) ? name.substring(lastDot + 1) : name;
    }

    /**
     * Internal record to hold file metadata for signing.
     * <p>
     * This bundles together the file, its extension, and classifier so they
     * can be easily passed around during the signing process.
     * </p>
     */
    private static class FileToSign {
        final File file;
        final String extension;
        final String classifier;

        FileToSign(File file, String extension, String classifier) {
            this.file = file;
            this.extension = extension;
            this.classifier = classifier;
        }
    }
}
