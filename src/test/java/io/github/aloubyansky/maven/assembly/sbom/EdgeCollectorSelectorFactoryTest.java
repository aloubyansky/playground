package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EdgeCollectorSelectorFactoryTest {

    @Mock
    DependencySelector delegate;

    @Test
    void factorySelectDependencyThrows() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        EdgeCollectorSelectorFactory factory = new EdgeCollectorSelectorFactory(delegate, edges);

        assertThrows(UnsupportedOperationException.class,
                () -> factory.selectDependency(mock(Dependency.class)));
    }

    @Test
    void selectorRecordsCompileScopedEdge() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        org.eclipse.aether.artifact.Artifact parentArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.parent",
                "parent", "jar", "1.0");
        org.eclipse.aether.artifact.Artifact childArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.child",
                "child", "jar", "2.0");

        when(delegate.selectDependency(any())).thenReturn(true);
        when(delegate.deriveChildSelector(any())).thenReturn(delegate);

        EdgeCollectorSelectorFactory factory = new EdgeCollectorSelectorFactory(delegate, edges);
        DependencyCollectionContext context = mockContext(parentArtifact);
        DependencySelector childSelector = factory.deriveChildSelector(context);

        Dependency dep = new Dependency(childArtifact, "compile");
        boolean result = childSelector.selectDependency(dep);

        assertTrue(result);
        ArtifactCoords parentId = ArtifactCoords.of(parentArtifact);
        assertTrue(edges.containsKey(parentId), "edge map should contain parent");
        assertTrue(edges.get(parentId).contains(ArtifactCoords.of(childArtifact)),
                "parent's children should contain child");
    }

    @Test
    void testScopedDependencyNotRecorded() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        org.eclipse.aether.artifact.Artifact parentArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.parent",
                "parent", "jar", "1.0");
        org.eclipse.aether.artifact.Artifact testArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.test",
                "test-lib", "jar", "1.0");

        when(delegate.selectDependency(any())).thenReturn(true);
        when(delegate.deriveChildSelector(any())).thenReturn(delegate);

        EdgeCollectorSelectorFactory factory = new EdgeCollectorSelectorFactory(delegate, edges);
        DependencyCollectionContext context = mockContext(parentArtifact);
        DependencySelector childSelector = factory.deriveChildSelector(context);

        Dependency dep = new Dependency(testArtifact, "test");
        childSelector.selectDependency(dep);

        assertTrue(edges.isEmpty(), "test-scoped dependency should not be recorded");
    }

    @Test
    void selectorPreservesDelegateDecision() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        org.eclipse.aether.artifact.Artifact parentArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.parent",
                "parent", "jar", "1.0");
        org.eclipse.aether.artifact.Artifact childArtifact = new org.eclipse.aether.artifact.DefaultArtifact("org.child",
                "child", "jar", "2.0");

        when(delegate.selectDependency(any())).thenReturn(false);
        when(delegate.deriveChildSelector(any())).thenReturn(delegate);

        EdgeCollectorSelectorFactory factory = new EdgeCollectorSelectorFactory(delegate, edges);
        DependencySelector childSelector = factory.deriveChildSelector(mockContext(parentArtifact));

        Dependency dep = new Dependency(childArtifact, "compile");
        boolean result = childSelector.selectDependency(dep);

        assertFalse(result, "should preserve delegate's false decision");
        assertFalse(edges.isEmpty(), "edge should still be recorded even when delegate rejects");
    }

    @Test
    void factoryEqualsAndHashCode() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        DependencySelector delegate2 = mock(DependencySelector.class);

        EdgeCollectorSelectorFactory a = new EdgeCollectorSelectorFactory(delegate, edges);
        EdgeCollectorSelectorFactory b = new EdgeCollectorSelectorFactory(delegate, edges);
        EdgeCollectorSelectorFactory c = new EdgeCollectorSelectorFactory(delegate2, edges);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    void selectorEqualsAndHashCode() {
        Map<ArtifactCoords, Set<ArtifactCoords>> edges = new ConcurrentHashMap<>();
        org.eclipse.aether.artifact.Artifact artifact = new org.eclipse.aether.artifact.DefaultArtifact("org.a", "a", "jar",
                "1.0");

        var a = new EdgeCollectorSelectorFactory.EdgeCollectorSelector(delegate, edges, artifact);
        var b = new EdgeCollectorSelectorFactory.EdgeCollectorSelector(delegate, edges, artifact);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    private static DependencyCollectionContext mockContext(
            org.eclipse.aether.artifact.Artifact artifact) {
        DependencyCollectionContext ctx = mock(DependencyCollectionContext.class);
        lenient().when(ctx.getArtifact()).thenReturn(artifact);
        return ctx;
    }
}
