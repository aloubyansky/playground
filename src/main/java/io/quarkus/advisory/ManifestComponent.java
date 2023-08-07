package io.quarkus.advisory;

import io.quarkus.maven.dependency.ArtifactKey;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

public class ManifestComponent {

    public static ManifestComponent of(ArtifactKey key, Collection<String> versions) {
        return new ManifestComponent(key, versions);
    }

    private final ArtifactKey key;
    private final Collection<String> versions;

    private ManifestComponent(ArtifactKey key, Collection<String> versions) {
        this.key = key;
        this.versions = Set.copyOf(versions);
    }

    public ArtifactKey getKey() {
        return key;
    }

    public Collection<String> getVersions() {
        return versions;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ManifestComponent that = (ManifestComponent) o;
        return Objects.equals(key, that.key) && Objects.equals(versions, that.versions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, versions);
    }

    @Override
    public String toString() {
        return key + ":" + versions;
    }
}
