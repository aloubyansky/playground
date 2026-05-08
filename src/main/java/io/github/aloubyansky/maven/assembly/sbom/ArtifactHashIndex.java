package io.github.aloubyansky.maven.assembly.sbom;

import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.Artifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Content-hash index over Maven artifacts, providing O(1) lookup by
 * file hash and on-demand hash computation with caching.
 *
 * <p>
 * An initial set of artifacts is hashed at construction time. Additional
 * artifacts are hashed lazily on first {@link #hashOf} call and cached
 * for subsequent lookups.
 * </p>
 *
 * <p>
 * When two distinct artifacts produce the same content hash (e.g.,
 * relocated or shadowed JARs), the index either throws an
 * {@link IllegalStateException} or logs a warning, depending on the
 * {@code failOnDuplicateHash} configuration.
 * </p>
 */
class ArtifactHashIndex {

    private static final Logger log = LoggerFactory.getLogger(ArtifactHashIndex.class);

    private final MessageDigest digest;
    private final Map<String, List<Artifact>> hashToArtifacts;
    private final Map<Artifact, String> artifactToHash;

    /**
     * Builds an index of the given artifacts keyed by content hash.
     *
     * <p>
     * Artifacts whose file is {@code null} or does not exist on
     * disk are silently skipped. Hash computation failures are logged
     * at warn level and the artifact is skipped.
     * </p>
     *
     * @param artifacts the artifacts to index
     * @param digest the message digest to use for hashing
     * @param failOnDuplicateHash if {@code true}, throws when two distinct
     *        artifacts produce the same hash; if
     *        {@code false}, logs a warning and indexes both
     * @throws IllegalStateException if {@code failOnDuplicateHash} is
     *         {@code true} and a hash collision is detected
     */
    ArtifactHashIndex(List<Artifact> artifacts, MessageDigest digest,
            boolean failOnDuplicateHash) {
        this.digest = digest;
        hashToArtifacts = new HashMap<>(artifacts.size());
        artifactToHash = new HashMap<>(artifacts.size());
        for (Artifact a : artifacts) {
            if (a.getFile() == null || !a.getFile().isFile()) {
                continue;
            }
            try {
                String hash = SbomUtils.computeHash(digest, a.getFile().toPath());
                List<Artifact> existing = hashToArtifacts.get(hash);
                if (existing != null) {
                    handleDuplicate(a, existing.get(0), hash, failOnDuplicateHash);
                }
                hashToArtifacts.computeIfAbsent(hash, k -> new ArrayList<>(1)).add(a);
                artifactToHash.put(a, hash);
            } catch (IOException e) {
                log.warn("Failed to compute hash for artifact {}", a, e);
            }
        }
    }

    /**
     * Returns the artifacts matching the given content hash.
     *
     * @param hash the hex-encoded content hash to look up
     * @return the matching artifacts, or {@code null} if no artifact
     *         has the given hash
     */
    List<Artifact> lookup(String hash) {
        return hashToArtifacts.get(hash);
    }

    /**
     * Returns the content hash for the given artifact, computing and
     * caching it on first access.
     *
     * @param artifact the artifact to hash
     * @return the hex-encoded content hash, or {@code null} if the
     *         artifact has no file or hashing fails
     */
    String hashOf(Artifact artifact) {
        String hash = artifactToHash.get(artifact);
        if (hash != null) {
            return hash;
        }
        if (artifact.getFile() == null || !artifact.getFile().isFile()) {
            return null;
        }
        try {
            hash = SbomUtils.computeHash(digest, artifact.getFile().toPath());
            artifactToHash.put(artifact, hash);
            return hash;
        } catch (IOException e) {
            log.debug("Failed to compute hash for artifact {}", artifact, e);
            return null;
        }
    }

    /**
     * Handles the case where two distinct artifacts share the same
     * content hash.
     */
    private static void handleDuplicate(Artifact incoming, Artifact existing,
            String hash, boolean fail) {
        String message = "Duplicate artifact hash " + hash + ": "
                + ArtifactCoords.of(existing) + " and " + ArtifactCoords.of(incoming);
        if (fail) {
            throw new IllegalStateException(message);
        }
        log.warn(message);
    }
}
