package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchiveAnalyzerTest {

    @TempDir
    Path tempDir;

    @Mock
    EffectiveModelResolver effectiveModelResolver;

    @Mock
    RepositorySystem repoSystem;

    @Mock
    MavenProject project;

    @Mock
    MavenSession session;

    private MessageDigest digest;

    @BeforeEach
    void setUp() throws Exception {
        digest = MessageDigest.getInstance("SHA-256");
        lenient().when(session.getProjects()).thenReturn(List.of());
    }

    @Test
    void matchedArtifactClassifiedAsMavenEntry() throws Exception {
        Path jarFile = createTestJar("lib-a-1.0.jar", "content-a");
        Artifact artifact = createArtifact("org.example", "lib-a", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        String hash = SbomUtils.computeHash(digest, jarFile);
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("lib/lib-a-1.0.jar", hash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, null);

        assertEquals(1, content.mavenEntries().size());
        assertEquals(0, content.unmatchedFiles().size());
        assertEquals("org.example", content.mavenEntries().get(0).artifactId().groupId());
        assertEquals("lib/lib-a-1.0.jar", content.mavenEntries().get(0).archivePath());
    }

    @Test
    void unmatchedFileClassifiedCorrectly() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());

        String hash = SbomUtils.computeHash(digest,
                new java.io.ByteArrayInputStream("config-data".getBytes(StandardCharsets.UTF_8)));
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("conf/app.properties", hash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, null);

        assertEquals(0, content.mavenEntries().size());
        assertEquals(1, content.unmatchedFiles().size());
        assertEquals("conf/app.properties", content.unmatchedFiles().get(0).path());
    }

    @Test
    void baseDirPrefixStripped() throws Exception {
        Path jarFile = createTestJar("lib-a-1.0.jar", "content-strip");
        Artifact artifact = createArtifact("org.example", "lib-a", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        String hash = SbomUtils.computeHash(digest, jarFile);
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("myapp-1.0/lib/lib-a-1.0.jar", hash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, "myapp-1.0/");

        assertEquals(1, content.mavenEntries().size());
        assertEquals("lib/lib-a-1.0.jar", content.mavenEntries().get(0).archivePath());
    }

    @Test
    void unpackedArtifactDetectedByContentHash() throws Exception {
        Path entryFile = createTestFile("entry.txt", "unpacked-entry-data");
        byte[] entryBytes = Files.readAllBytes(entryFile);

        Path warFile = tempDir.resolve("mywar-1.0.war");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(warFile))) {
            jos.putNextEntry(new JarEntry("WEB-INF/entry.txt"));
            jos.write(entryBytes);
            jos.closeEntry();
        }

        Artifact warArtifact = createArtifact("org.example", "mywar", "1.0", "war", warFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(warArtifact));

        String entryHash = SbomUtils.computeHash(digest, entryFile);
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("web/entry.txt", entryHash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, null);

        assertEquals(1, content.mavenEntries().size());
        assertEquals("mywar", content.mavenEntries().get(0).artifactId().artifactId());
        assertEquals(0, content.unmatchedFiles().size());
    }

    @Test
    void nestedJarIdentifiedViaPomProperties() throws Exception {
        Path nestedJar = createJarWithPomProperties("nested-1.0.jar",
                "org.nested", "nested", "1.0", "nested-content");

        Path warFile = tempDir.resolve("parent-1.0.war");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(warFile))) {
            jos.putNextEntry(new JarEntry("WEB-INF/lib/nested-1.0.jar"));
            jos.write(Files.readAllBytes(nestedJar));
            jos.closeEntry();
        }

        Artifact warArtifact = createArtifact("org.example", "parent", "1.0", "war", warFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(warArtifact));

        String nestedHash = SbomUtils.computeHash(digest, nestedJar);
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("web/parent/WEB-INF/lib/nested-1.0.jar", nestedHash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, null);

        assertEquals(1, content.mavenEntries().size(), "parent WAR should be matched");
        assertEquals(1, content.nestedEntries().size(), "nested JAR should be identified");
        assertEquals("nested", content.nestedEntries().get(0).artifactId().artifactId());
        assertEquals("org.nested", content.nestedEntries().get(0).artifactId().groupId());
    }

    @Test
    void multipleEntriesMixedClassification() throws Exception {
        Path jarFile = createTestJar("known-1.0.jar", "known-content");
        Artifact artifact = createArtifact("org.example", "known", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        String knownHash = SbomUtils.computeHash(digest, jarFile);
        String unknownHash = SbomUtils.computeHash(digest,
                new java.io.ByteArrayInputStream("unknown-content".getBytes(StandardCharsets.UTF_8)));

        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("lib/known-1.0.jar", knownHash),
                new ArchiveContent.FileEntry("conf/settings.xml", unknownHash));

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(entries, null);

        assertEquals(1, content.mavenEntries().size());
        assertEquals(1, content.unmatchedFiles().size());
        assertEquals("conf/settings.xml", content.unmatchedFiles().get(0).path());
    }

    @Test
    void emptyEntriesProducesEmptyContent() {
        when(project.getArtifacts()).thenReturn(Set.of());

        ArchiveAnalyzer analyzer = createAnalyzer();
        ArchiveContent content = analyzer.analyze(List.of(), null);

        assertEquals(0, content.mavenEntries().size());
        assertEquals(0, content.unmatchedFiles().size());
        assertEquals(0, content.nestedEntries().size());
    }

    @Test
    void duplicateHashFailsWhenConfigured() throws Exception {
        Path fileA = createTestFile("a.jar", "same-content");
        Path fileB = createTestFile("b.jar", "same-content");
        Artifact a = createArtifact("org.example", "a", "1.0", "jar", fileA.toFile());
        Artifact b = createArtifact("org.example", "b", "2.0", "jar", fileB.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(a, b));

        String hash = SbomUtils.computeHash(digest, fileA);
        List<ArchiveContent.FileEntry> entries = List.of(
                new ArchiveContent.FileEntry("lib/a.jar", hash));

        ArchiveAnalyzer analyzer = new ArchiveAnalyzer(
                effectiveModelResolver, repoSystem, project, session, digest, true);
        assertThrows(IllegalStateException.class,
                () -> analyzer.analyze(entries, null));
    }

    // --- helpers ---

    private ArchiveAnalyzer createAnalyzer() {
        return new ArchiveAnalyzer(
                effectiveModelResolver, repoSystem, project, session, digest, false);
    }

    private Path createTestJar(String name, String content) throws Exception {
        Path jarPath = tempDir.resolve(name);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("data.txt"));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createJarWithPomProperties(String name, String groupId,
            String artifactId, String version, String content) throws Exception {
        Path jarPath = tempDir.resolve(name);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("data.txt"));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            String propsPath = "META-INF/maven/" + groupId + "/" + artifactId
                    + "/pom.properties";
            jos.putNextEntry(new JarEntry(propsPath));
            String props = "groupId=" + groupId + "\n"
                    + "artifactId=" + artifactId + "\n"
                    + "version=" + version + "\n";
            jos.write(props.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createTestFile(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static Artifact createArtifact(String groupId, String artifactId,
            String version, String type, File file) {
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, version, "compile", type, null,
                new DefaultArtifactHandler(type));
        artifact.setFile(file);
        return artifact;
    }
}
