package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactHashIndexTest {

    @TempDir
    Path tempDir;

    private MessageDigest digest;

    @BeforeEach
    void setUp() throws Exception {
        digest = MessageDigest.getInstance("SHA-256");
    }

    @Test
    void lookup_returnsArtifactsForKnownHash() throws Exception {
        File file = createTempFile("content-a");
        Artifact artifact = createArtifact("org.example", "lib-a", "1.0", file);

        ArtifactHashIndex index = new ArtifactHashIndex(List.of(artifact), digest, true);

        String hash = SbomUtils.computeHash(digest, file.toPath());
        List<Artifact> result = index.lookup(hash);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(artifact, result.get(0));
    }

    @Test
    void lookup_returnsNullForUnknownHash() throws Exception {
        File file = createTempFile("content-a");
        Artifact artifact = createArtifact("org.example", "lib-a", "1.0", file);

        ArtifactHashIndex index = new ArtifactHashIndex(List.of(artifact), digest, true);

        assertNull(index.lookup("0000000000000000000000000000000000000000000000000000000000000000"));
    }

    @Test
    void duplicateHash_throwsByDefault() throws Exception {
        File file = createTempFile("same-content");
        File fileCopy = createTempFile("same-content");
        Artifact a = createArtifact("org.example", "lib-a", "1.0", file);
        Artifact b = createArtifact("org.example", "lib-b", "2.0", fileCopy);

        assertThrows(IllegalStateException.class,
                () -> new ArtifactHashIndex(List.of(a, b), digest, true));
    }

    @Test
    void duplicateHash_warnsWhenConfigured() throws Exception {
        File file = createTempFile("same-content");
        File fileCopy = createTempFile("same-content");
        Artifact a = createArtifact("org.example", "lib-a", "1.0", file);
        Artifact b = createArtifact("org.example", "lib-b", "2.0", fileCopy);

        ArtifactHashIndex index = new ArtifactHashIndex(List.of(a, b), digest, false);

        String hash = SbomUtils.computeHash(digest, file.toPath());
        List<Artifact> result = index.lookup(hash);
        assertNotNull(result);
        assertEquals(2, result.size(), "both artifacts should be indexed");
    }

    @Test
    void skipsArtifactsWithNoFile() {
        Artifact noFile = createArtifact("org.example", "no-file", "1.0", null);

        ArtifactHashIndex index = new ArtifactHashIndex(List.of(noFile), digest, true);

        assertNull(index.lookup("anything"));
    }

    private File createTempFile(String content) throws Exception {
        Path file = Files.createTempFile(tempDir, "artifact-", ".jar");
        Files.writeString(file, content);
        return file.toFile();
    }

    private static Artifact createArtifact(String groupId, String artifactId,
            String version, File file) {
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, version, "compile", "jar", null,
                new DefaultArtifactHandler("jar"));
        artifact.setFile(file);
        return artifact;
    }
}
