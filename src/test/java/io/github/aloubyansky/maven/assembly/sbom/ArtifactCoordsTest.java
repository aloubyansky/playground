package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.junit.jupiter.api.Test;

class ArtifactCoordsTest {

    @Test
    void toStringWithoutClassifier() {
        ArtifactCoords id = ArtifactCoords.of("org.example", "foo", "1.0");
        assertEquals("org.example:foo:1.0", id.toString());
    }

    @Test
    void toStringWithClassifier() {
        ArtifactCoords id = new ArtifactCoords("org.example", "foo", "1.0", "jar", "sources");
        assertEquals("org.example:foo:1.0:sources", id.toString());
    }

    @Test
    void toStringWithNonJarType() {
        ArtifactCoords id = new ArtifactCoords("org.example", "foo", "1.0", "war", null);
        assertEquals("org.example:foo:war:1.0", id.toString());
    }

    @Test
    void toStringWithNonJarTypeAndClassifier() {
        ArtifactCoords id = new ArtifactCoords("org.example", "foo", "1.0", "war", "classes");
        assertEquals("org.example:foo:war:1.0:classes", id.toString());
    }

    @Test
    void emptyClassifierNormalizedToNull() {
        ArtifactCoords withEmpty = new ArtifactCoords("org.example", "foo", "1.0", null, "");
        ArtifactCoords withNull = new ArtifactCoords("org.example", "foo", "1.0", null, null);
        assertEquals(withNull, withEmpty);
        assertNull(withEmpty.classifier());
        assertEquals(withNull.hashCode(), withEmpty.hashCode());
    }

    @Test
    void toGavOmitsClassifier() {
        ArtifactCoords id = new ArtifactCoords("org.example", "foo", "1.0", "jar", "sources");
        assertEquals("org.example:foo:1.0", id.toGav());
    }

    @Test
    void toGavSameAsToStringWhenNoClassifier() {
        ArtifactCoords id = ArtifactCoords.of("org.example", "foo", "1.0");
        assertEquals(id.toGav(), id.toString());
    }

    @Test
    void fromMavenArtifact() {
        DefaultArtifact artifact = new DefaultArtifact(
                "org.example", "bar", "2.0", "compile", "jar", null,
                new DefaultArtifactHandler("jar"));
        ArtifactCoords id = ArtifactCoords.of(artifact);
        assertEquals("org.example", id.groupId());
        assertEquals("bar", id.artifactId());
        assertEquals("2.0", id.version());
        assertEquals("jar", id.type());
        assertNull(id.classifier());
    }

    @Test
    void fromMavenArtifactWithClassifier() {
        DefaultArtifact artifact = new DefaultArtifact(
                "org.example", "bar", "2.0", "compile", "jar", "tests",
                new DefaultArtifactHandler("jar"));
        ArtifactCoords id = ArtifactCoords.of(artifact);
        assertEquals("tests", id.classifier());
        assertEquals("org.example:bar:2.0:tests", id.toString());
    }

    @Test
    void fromAetherArtifact() {
        org.eclipse.aether.artifact.DefaultArtifact artifact = new org.eclipse.aether.artifact.DefaultArtifact(
                "org.example", "baz", "javadoc", "jar", "3.0");
        ArtifactCoords id = ArtifactCoords.of(artifact);
        assertEquals("org.example", id.groupId());
        assertEquals("baz", id.artifactId());
        assertEquals("3.0", id.version());
        assertEquals("javadoc", id.classifier());
    }

    @Test
    void equalityByFields() {
        ArtifactCoords a = new ArtifactCoords("org.example", "foo", "1.0", "jar", "sources");
        ArtifactCoords b = new ArtifactCoords("org.example", "foo", "1.0", "jar", "sources");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void differentClassifierNotEqual() {
        ArtifactCoords a = ArtifactCoords.of("org.example", "foo", "1.0");
        ArtifactCoords b = new ArtifactCoords("org.example", "foo", "1.0", "jar", "sources");
        assertNotEquals(a, b);
    }

    @Test
    void differentVersionNotEqual() {
        ArtifactCoords a = ArtifactCoords.of("org.example", "foo", "1.0");
        ArtifactCoords b = ArtifactCoords.of("org.example", "foo", "2.0");
        assertNotEquals(a, b);
    }

    @Test
    void nullGroupIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new ArtifactCoords(null, "foo", "1.0", null, null));
    }

    @Test
    void nullArtifactIdThrows() {
        assertThrows(NullPointerException.class,
                () -> new ArtifactCoords("org.example", null, "1.0", null, null));
    }

    @Test
    void nullVersionThrows() {
        assertThrows(NullPointerException.class,
                () -> new ArtifactCoords("org.example", "foo", null, null, null));
    }
}
