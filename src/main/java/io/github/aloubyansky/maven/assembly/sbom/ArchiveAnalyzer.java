package io.github.aloubyansky.maven.assembly.sbom;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matches archive entries to Maven artifacts by content hash and
 * detects unpacked (exploded) archives within the assembly.
 *
 * <p>
 * This class encapsulates the artifact identification pipeline:
 * hash-based matching, unpacked artifact detection via ZIP content
 * scanning, and nested JAR identification via dependency resolution
 * or embedded {@code pom.properties}.
 * </p>
 */
class ArchiveAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ArchiveAnalyzer.class);
    public static final String JAR = "jar";

    private final EffectiveModelResolver effectiveModelResolver;
    private final RepositorySystem repoSystem;
    private final MavenProject project;
    private final MavenSession session;
    // not thread-safe — safe here because the matcher runs on a single thread
    private final MessageDigest messageDigest;
    private final boolean failOnDuplicateHash;
    private final Map<ArtifactCoords, MavenProject> reactorModuleIndex;
    private List<Artifact> allArtifacts;
    private ArtifactHashIndex hashIndex;

    /**
     * Result of scanning a ZIP file's contents against unmatched
     * archive entries.
     *
     * @param matchedArchiveEntries archive entries matched by hash
     * @param hashToZipEntryNames content hash to ZIP entry name mapping
     */
    record ZipScanResult(Set<ArchiveContent.FileEntry> matchedArchiveEntries,
            Map<String, List<String>> hashToZipEntryNames) {

        /** Returns {@code true} if at least one entry matched. */
        boolean hasMatchedEntries() {
            return !matchedArchiveEntries.isEmpty();
        }

        /**
         * Derives the common unpack prefix from matched entries.
         *
         * @return the prefix (with trailing slash), or {@code null}
         */
        String computeUnpackPrefix() {
            String prefix = null;
            for (ArchiveContent.FileEntry entry : matchedArchiveEntries) {
                List<String> zipNames = hashToZipEntryNames.get(entry.hash());
                if (zipNames == null) {
                    return null;
                }
                String entryPrefix = null;
                for (String zipName : zipNames) {
                    if (entry.path().endsWith(zipName)) {
                        entryPrefix = entry.path().substring(
                                0, entry.path().length() - zipName.length());
                        break;
                    }
                }
                if (entryPrefix == null) {
                    return null;
                }
                if (prefix == null) {
                    prefix = entryPrefix;
                } else if (!prefix.equals(entryPrefix)) {
                    return null;
                }
            }
            return prefix;
        }
    }

    ArchiveAnalyzer(EffectiveModelResolver effectiveModelResolver,
            RepositorySystem repoSystem,
            MavenProject project,
            MavenSession session,
            MessageDigest messageDigest,
            boolean failOnDuplicateHash) {
        this.effectiveModelResolver = effectiveModelResolver;
        this.repoSystem = repoSystem;
        this.project = project;
        this.session = session;
        this.messageDigest = messageDigest;
        this.failOnDuplicateHash = failOnDuplicateHash;
        this.reactorModuleIndex = indexReactorModules(session.getProjects());
    }

    /**
     * Indexes reactor projects by artifact id for O(1) lookup.
     */
    private static Map<ArtifactCoords, MavenProject> indexReactorModules(
            List<MavenProject> projects) {
        Map<ArtifactCoords, MavenProject> index = new HashMap<>(projects.size());
        for (MavenProject p : projects) {
            index.put(ArtifactCoords.of(p), p);
        }
        return index;
    }

    /**
     * Returns the project's resolved dependencies plus its own artifact,
     * collecting them on first access.
     */
    private List<Artifact> allArtifacts() {
        if (allArtifacts == null) {
            allArtifacts = new ArrayList<>();
            if (project.getArtifacts() != null) {
                allArtifacts.addAll(project.getArtifacts());
            }
            Artifact projectArtifact = project.getArtifact();
            if (projectArtifact != null && projectArtifact.getFile() != null) {
                allArtifacts.add(projectArtifact);
            }
        }
        return allArtifacts;
    }

    /**
     * Analyzes archive entries against Maven artifacts and returns
     * classified results.
     *
     * @param entries the archive file entries with content hashes
     * @param baseDirPrefix the base directory prefix to strip, or {@code null}
     * @return the classified assembly content
     * @throws IllegalStateException if duplicate hashes are detected
     *         and the matcher is configured to fail on duplicates
     */
    ArchiveContent analyze(List<ArchiveContent.FileEntry> entries, String baseDirPrefix) {
        ArchiveContent content = new ArchiveContent();
        Set<Artifact> matchedArtifacts = new HashSet<>();
        Map<String, ArchiveContent.FileEntry> unmatchedByPath = new HashMap<>();

        classifyArchiveEntries(entries, baseDirPrefix, content,
                matchedArtifacts, unmatchedByPath);
        if (!unmatchedByPath.isEmpty()) {
            detectUnpackedArtifacts(matchedArtifacts, unmatchedByPath, content);
        }

        for (ArchiveContent.FileEntry entry : unmatchedByPath.values()) {
            content.addUnmatchedFile(entry);
        }

        return content;
    }

    /**
     * Classifies archive entries as matched or unmatched by hash lookup.
     */
    private void classifyArchiveEntries(List<ArchiveContent.FileEntry> entries,
            String baseDirPrefix,
            ArchiveContent content,
            Set<Artifact> matchedArtifacts,
            Map<String, ArchiveContent.FileEntry> unmatchedByPath) {
        hashIndex = new ArtifactHashIndex(allArtifacts(), messageDigest, failOnDuplicateHash);
        for (ArchiveContent.FileEntry entry : entries) {
            String relativePath = stripBaseDir(entry.path(), baseDirPrefix);
            if (relativePath.isEmpty()) {
                continue;
            }
            List<Artifact> artifacts = hashIndex.lookup(entry.hash());
            if (artifacts != null) {
                for (Artifact artifact : artifacts) {
                    matchedArtifacts.add(artifact);
                    content.addMavenEntry(new ArchiveContent.MavenEntry(
                            ArtifactCoords.of(artifact), relativePath, entry.hash()));
                }
            } else {
                unmatchedByPath.put(relativePath,
                        new ArchiveContent.FileEntry(relativePath, entry.hash()));
            }
        }
    }

    /**
     * Detects artifacts that were unpacked into the assembly.
     * Matched entries are removed from {@code unmatchedByPath}.
     */
    private void detectUnpackedArtifacts(
            Set<Artifact> matchedArtifacts,
            Map<String, ArchiveContent.FileEntry> unmatchedByPath,
            ArchiveContent content) {

        Map<String, List<ArchiveContent.FileEntry>> entriesByHash = indexEntriesByHash(unmatchedByPath.values());

        for (Artifact artifact : allArtifacts()) {
            if (entriesByHash.isEmpty()) {
                break;
            }
            if (matchedArtifacts.contains(artifact)) {
                continue;
            }
            if (artifact.getFile() == null || !artifact.getFile().isFile()) {
                continue;
            }
            tryMatchUnpackedArtifact(artifact, entriesByHash,
                    matchedArtifacts, unmatchedByPath, content);
        }
    }

    /**
     * Indexes entries by content hash.
     */
    private static Map<String, List<ArchiveContent.FileEntry>> indexEntriesByHash(
            Iterable<ArchiveContent.FileEntry> entries) {
        Map<String, List<ArchiveContent.FileEntry>> index = new HashMap<>();
        for (ArchiveContent.FileEntry e : entries) {
            if (e.hash() != null) {
                index.computeIfAbsent(e.hash(), k -> new ArrayList<>(1)).add(e);
            }
        }
        return index;
    }

    // ---- private helpers ----

    /**
     * Strips the base directory prefix from an archive path.
     */
    private static String stripBaseDir(String archivePath, String baseDirPrefix) {
        if (baseDirPrefix != null && archivePath.startsWith(baseDirPrefix)) {
            return archivePath.substring(baseDirPrefix.length());
        }
        return archivePath;
    }

    /**
     * Attempts to match a single artifact as an unpacked archive.
     */
    private void tryMatchUnpackedArtifact(Artifact artifact,
            Map<String, List<ArchiveContent.FileEntry>> entriesByHash,
            Set<Artifact> matchedArtifacts,
            Map<String, ArchiveContent.FileEntry> unmatchedByPath,
            ArchiveContent content) {
        try (ZipFile zf = new ZipFile(artifact.getFile())) {
            ZipScanResult scan = scanZipForMatches(zf, entriesByHash);
            if (!scan.hasMatchedEntries()) {
                return;
            }

            matchedArtifacts.add(artifact);
            String occurrence = scan.computeUnpackPrefix();
            if (occurrence == null) {
                log.debug("Could not determine unpack prefix for {}", artifact);
            }
            content.addMavenEntry(new ArchiveContent.MavenEntry(
                    ArtifactCoords.of(artifact), occurrence, hashIndex.hashOf(artifact)));

            Map<String, Artifact> nestedArtifactsByHash = buildNestedArtifactHashMap(artifact);
            ArtifactCoords parentCoords = ArtifactCoords.of(artifact);

            for (ArchiveContent.FileEntry archiveEntry : scan.matchedArchiveEntries) {
                unmatchedByPath.remove(archiveEntry.path());
                entriesByHash.remove(archiveEntry.hash());
                identifyNestedArtifact(archiveEntry, zf,
                        scan.hashToZipEntryNames, nestedArtifactsByHash,
                        parentCoords, matchedArtifacts, content);
            }
        } catch (IOException e) {
            log.debug("Could not read {} as ZIP, skipping unpacked detection",
                    artifact.getFile(), e);
        }
    }

    /**
     * Scans ZIP entries and matches hashes against unmatched archive entries.
     */
    private ZipScanResult scanZipForMatches(ZipFile zf,
            Map<String, List<ArchiveContent.FileEntry>> entriesByHash)
            throws IOException {
        Set<ArchiveContent.FileEntry> matchedArchiveEntries = new HashSet<>();
        Map<String, List<String>> hashToZipEntryNames = new HashMap<>();

        Enumeration<? extends ZipEntry> zipEntries = zf.entries();
        while (zipEntries.hasMoreElements()) {
            ZipEntry ze = zipEntries.nextElement();
            if (ze.isDirectory()) {
                continue;
            }
            String hash;
            try (InputStream entryStream = zf.getInputStream(ze)) {
                hash = SbomUtils.computeHash(messageDigest, entryStream);
            }
            hashToZipEntryNames.computeIfAbsent(hash, k -> new ArrayList<>(1))
                    .add(ze.getName());
            List<ArchiveContent.FileEntry> matching = entriesByHash.get(hash);
            if (matching != null) {
                matchedArchiveEntries.addAll(matching);
            }
        }
        return new ZipScanResult(matchedArchiveEntries, hashToZipEntryNames);
    }

    /**
     * Identifies a nested artifact via hash lookup or pom.properties fallback.
     */
    private void identifyNestedArtifact(ArchiveContent.FileEntry archiveEntry, ZipFile parentZip,
            Map<String, List<String>> hashToZipEntryNames,
            Map<String, Artifact> nestedArtifactsByHash,
            ArtifactCoords parentId, Set<Artifact> matchedArtifacts,
            ArchiveContent content) {
        if (archiveEntry.hash() != null) {
            Artifact nestedArtifact = nestedArtifactsByHash.get(archiveEntry.hash());
            if (nestedArtifact != null) {
                registerNestedArtifact(nestedArtifact, archiveEntry, parentId, matchedArtifacts, content);
                return;
            }
        }
        tryIdentifyFromPomProperties(archiveEntry, parentZip,
                hashToZipEntryNames, parentId, matchedArtifacts, content);
    }

    /**
     * Attempts to identify a nested JAR by reading embedded pom.properties.
     */
    private void tryIdentifyFromPomProperties(ArchiveContent.FileEntry archiveEntry, ZipFile parentZip,
            Map<String, List<String>> hashToZipEntryNames,
            ArtifactCoords parentId,
            Set<Artifact> matchedArtifacts,
            ArchiveContent content) {
        if (archiveEntry.hash() == null) {
            return;
        }
        List<String> zipEntryNames = hashToZipEntryNames.get(archiveEntry.hash());
        if (zipEntryNames == null) {
            return;
        }
        for (String zipEntryName : zipEntryNames) {
            Properties pomProps = readPomProperties(parentZip, zipEntryName);
            if (pomProps == null) {
                continue;
            }
            String gId = pomProps.getProperty("groupId");
            String aId = pomProps.getProperty("artifactId");
            String ver = pomProps.getProperty("version");
            if (gId != null && aId != null && ver != null) {
                Artifact nested = new org.apache.maven.artifact.DefaultArtifact(
                        gId, aId, ver, "compile", JAR, null,
                        new org.apache.maven.artifact.handler.DefaultArtifactHandler(JAR));
                registerNestedArtifact(nested, archiveEntry, parentId, matchedArtifacts, content);
                return;
            }
        }
    }

    /**
     * Records a nested artifact and its dependency relationship.
     */
    private void registerNestedArtifact(Artifact artifact, ArchiveContent.FileEntry archiveEntry,
            ArtifactCoords parentId, Set<Artifact> matchedArtifacts,
            ArchiveContent content) {
        matchedArtifacts.add(artifact);
        ArtifactCoords nestedId = ArtifactCoords.of(artifact);
        content.addNestedEntry(new ArchiveContent.NestedMavenEntry(
                parentId, nestedId, archiveEntry.path(), archiveEntry.hash()));
        content.addDependencyEdge(parentId, nestedId);
        String classifier = artifact.getClassifier();
        content.addNestedDependency(parentId, new Dependency(
                new org.eclipse.aether.artifact.DefaultArtifact(
                        artifact.getGroupId(), artifact.getArtifactId(),
                        classifier != null ? classifier : "",
                        artifact.getType(), artifact.getVersion()),
                "compile"));
    }

    /**
     * Reads pom.properties from a nested JAR entry.
     */
    private static Properties readPomProperties(ZipFile outerZip, String entryName) {
        ZipEntry entry = outerZip.getEntry(entryName);
        if (entry == null) {
            return null;
        }
        try (InputStream is = outerZip.getInputStream(entry);
                ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.getName().startsWith("META-INF/maven/")
                        && ze.getName().endsWith("/pom.properties")) {
                    Properties props = new Properties();
                    props.load(zis);
                    return props;
                }
            }
        } catch (IOException e) {
            log.debug("Failed to parse pom.properties from nested JAR {}", entryName, e);
        }
        return null;
    }

    /**
     * Builds a hash-to-artifact map for the given artifact's dependencies.
     */
    private Map<String, Artifact> buildNestedArtifactHashMap(Artifact artifact) {
        ArtifactCoords coords = ArtifactCoords.of(artifact);
        MavenProject module = reactorModuleIndex.get(coords);
        if (module != null && module.getArtifacts() != null) {
            return buildHashMapFromArtifacts(module.getArtifacts());
        }
        return buildHashMapFromEffectiveModel(artifact);
    }

    /**
     * Builds a hash-to-artifact map from pre-resolved Maven artifacts.
     */
    private Map<String, Artifact> buildHashMapFromArtifacts(Set<Artifact> artifacts) {
        Map<String, Artifact> map = new HashMap<>(artifacts.size());
        for (Artifact a : artifacts) {
            String hash = hashIndex.hashOf(a);
            if (hash != null) {
                map.put(hash, a);
            }
        }
        return map;
    }

    private static final Set<String> EXCLUDED_SCOPES = Set.of("test", "provided", "system");

    /**
     * Resolves dependencies from an artifact's effective POM and builds
     * a hash-to-artifact map. Only compile and runtime scoped dependencies
     * are included; test, provided, and system scopes are excluded.
     */
    private Map<String, Artifact> buildHashMapFromEffectiveModel(Artifact artifact) {
        Model model = effectiveModelResolver.resolveEffectiveModel(
                artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion());
        if (model == null || model.getDependencies() == null) {
            return Map.of();
        }

        Map<String, Artifact> map = new HashMap<>();
        for (org.apache.maven.model.Dependency dep : model.getDependencies()) {
            String scope = dep.getScope();
            if (scope == null || !EXCLUDED_SCOPES.contains(scope)) {
                resolveAndHashDependency(dep, map);
            }
        }
        return map;
    }

    /**
     * Resolves a single dependency's artifact and adds it to the hash map.
     */
    private void resolveAndHashDependency(org.apache.maven.model.Dependency dep,
            Map<String, Artifact> map) {
        try {
            org.eclipse.aether.artifact.DefaultArtifact aetherArtifact = new org.eclipse.aether.artifact.DefaultArtifact(
                    dep.getGroupId(), dep.getArtifactId(),
                    dep.getClassifier(),
                    dep.getType() != null ? dep.getType() : JAR,
                    dep.getVersion());
            ArtifactRequest request = new ArtifactRequest(
                    aetherArtifact, project.getRemoteProjectRepositories(), null);
            ArtifactResult result = repoSystem.resolveArtifact(
                    session.getRepositorySession(), request);
            File file = result.getArtifact().getFile();
            if (file != null && file.isFile()) {
                Artifact mavenArtifact = new org.apache.maven.artifact.DefaultArtifact(
                        dep.getGroupId(), dep.getArtifactId(), dep.getVersion(),
                        dep.getScope() != null ? dep.getScope() : "compile",
                        dep.getType() != null ? dep.getType() : JAR,
                        dep.getClassifier(),
                        new org.apache.maven.artifact.handler.DefaultArtifactHandler(
                                dep.getType() != null ? dep.getType() : JAR));
                mavenArtifact.setFile(file);
                String hash = hashIndex.hashOf(mavenArtifact);
                if (hash != null) {
                    map.put(hash, mavenArtifact);
                }
            }
        } catch (Exception e) {
            log.debug("Failed to resolve dependency {}:{}:{}",
                    dep.getGroupId(), dep.getArtifactId(), dep.getVersion(), e);
        }
    }
}
