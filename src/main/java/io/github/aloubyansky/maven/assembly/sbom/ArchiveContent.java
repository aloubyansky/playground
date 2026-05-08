package io.github.aloubyansky.maven.assembly.sbom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.aether.graph.Dependency;

/**
 * The result of analyzing an assembly archive's contents against Maven
 * artifacts. Contains classified entries (matched Maven artifacts,
 * nested artifacts inside unpacked archives, and unmatched files) plus
 * dependency relationship data.
 *
 * <p>
 * Instances are built incrementally by {@link ArchiveAnalyzer} and
 * consumed by {@link SbomContainerDescriptorHandler} to populate the
 * BOM with license information and component entries.
 * </p>
 */
class ArchiveContent {

    /**
     * An archive entry matched to a Maven artifact by content hash.
     */
    record MavenEntry(ArtifactCoords artifactId, String archivePath, String hash) {
    }

    /**
     * A Maven artifact found nested inside an unpacked parent archive
     * (e.g., a JAR inside an unpacked WAR).
     */
    record NestedMavenEntry(ArtifactCoords parentId, ArtifactCoords artifactId,
            String archivePath, String hash) {
    }

    /**
     * An archive file entry pairing its path with its content hash.
     */
    record FileEntry(String path, String hash) {
    }

    /**
     * An explicit parent-child dependency edge discovered during
     * unpacked artifact detection.
     */
    record DependencyEdge(ArtifactCoords parent, ArtifactCoords child) {
    }

    private final List<MavenEntry> mavenEntries = new ArrayList<>();
    private final List<NestedMavenEntry> nestedEntries = new ArrayList<>();
    private final List<FileEntry> unmatchedFiles = new ArrayList<>();
    private final List<DependencyEdge> explicitDependencies = new ArrayList<>();
    private final Map<ArtifactCoords, List<Dependency>> nestedDepsByParent = new HashMap<>();

    List<MavenEntry> mavenEntries() {
        return mavenEntries;
    }

    List<NestedMavenEntry> nestedEntries() {
        return nestedEntries;
    }

    List<FileEntry> unmatchedFiles() {
        return unmatchedFiles;
    }

    List<DependencyEdge> explicitDependencies() {
        return explicitDependencies;
    }

    Map<ArtifactCoords, List<Dependency>> nestedDepsByParent() {
        return nestedDepsByParent;
    }

    void addMavenEntry(MavenEntry entry) {
        mavenEntries.add(entry);
    }

    void addNestedEntry(NestedMavenEntry entry) {
        nestedEntries.add(entry);
    }

    void addUnmatchedFile(FileEntry entry) {
        unmatchedFiles.add(entry);
    }

    void addDependencyEdge(ArtifactCoords parent, ArtifactCoords child) {
        explicitDependencies.add(new DependencyEdge(parent, child));
    }

    void addNestedDependency(ArtifactCoords parentId, Dependency dependency) {
        nestedDepsByParent.computeIfAbsent(parentId, k -> new ArrayList<>())
                .add(dependency);
    }

    /**
     * Collects all known artifact ids from both top-level and nested
     * entries, for use in dependency graph filtering.
     */
    Set<ArtifactCoords> collectKnownArtifactCoords() {
        Set<ArtifactCoords> ids = new HashSet<>();
        for (MavenEntry e : mavenEntries) {
            ids.add(e.artifactId());
        }
        for (NestedMavenEntry e : nestedEntries) {
            ids.add(e.artifactId());
        }
        return ids;
    }
}
