package io.github.aloubyansky.maven.assembly.sbom;

import java.util.Objects;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;

/**
 * Immutable Maven artifact coordinates used as a deduplication key
 * across the SBOM generation pipeline.
 *
 * <p>
 * The record normalizes empty classifiers to {@code null} so that
 * coordinates with an empty string classifier and those with a
 * {@code null} classifier are considered equal.
 * </p>
 *
 * <p>
 * The {@link #toString()} representation follows the format
 * {@code groupId:artifactId:version[:classifier]}.
 * </p>
 *
 * @param groupId the Maven groupId
 * @param artifactId the Maven artifactId
 * @param version the artifact version
 * @param type the packaging type (e.g. "jar", "war"), or {@code null}
 * @param classifier the Maven classifier, or {@code null} if none
 */
record ArtifactCoords(String groupId, String artifactId, String version,
        String type, String classifier) {

    private static final String SEPARATOR = ":";

    ArtifactCoords {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(artifactId, "artifactId");
        Objects.requireNonNull(version, "version");
        if (type == null || type.isEmpty()) {
            type = "jar";
        }
        if (classifier != null && classifier.isEmpty()) {
            classifier = null;
        }
    }

    /**
     * Creates an {@link ArtifactCoords} without type or classifier.
     */
    static ArtifactCoords of(String groupId, String artifactId, String version) {
        return new ArtifactCoords(groupId, artifactId, version, null, null);
    }

    /**
     * Creates an {@link ArtifactCoords} from a Maven {@link Artifact}.
     */
    static ArtifactCoords of(Artifact a) {
        return new ArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getVersion(),
                a.getType(), a.getClassifier());
    }

    /**
     * Creates an {@link ArtifactCoords} from an Aether artifact.
     */
    static ArtifactCoords of(org.eclipse.aether.artifact.Artifact a) {
        return new ArtifactCoords(a.getGroupId(), a.getArtifactId(), a.getVersion(),
                a.getExtension(), a.getClassifier());
    }

    /**
     * Creates an {@link ArtifactCoords} from a {@link MavenProject}.
     */
    static ArtifactCoords of(MavenProject p) {
        return new ArtifactCoords(p.getGroupId(), p.getArtifactId(), p.getVersion(),
                p.getPackaging(), null);
    }

    /**
     * Returns the {@code groupId:artifactId:version} portion of the
     * coordinates, always omitting type and classifier.
     */
    String toGav() {
        return groupId + SEPARATOR + artifactId + SEPARATOR + version;
    }

    /**
     * Returns the full coordinate string. The format is
     * {@code groupId:artifactId:version} for plain JARs,
     * {@code groupId:artifactId:type:version} for non-JAR types,
     * and appends {@code :classifier} when present.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(SEPARATOR).append(artifactId).append(SEPARATOR);
        if (!"jar".equals(type)) {
            sb.append(type).append(SEPARATOR);
        }
        sb.append(version);
        if (classifier != null) {
            sb.append(SEPARATOR).append(classifier);
        }
        return sb.toString();
    }
}
