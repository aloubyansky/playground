package io.github.aloubyansky.maven.assembly.sbom;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.util.artifact.JavaScopes;

/**
 * A {@link DependencySelector} factory that wraps a delegate selector
 * to record dependency edges during Maven dependency collection.
 *
 * <p>
 * This class serves as the root selector installed on the repository
 * session. It never selects dependencies directly (throws
 * {@link UnsupportedOperationException} if called), but produces
 * {@link EdgeCollectorSelector} instances for child contexts that both
 * delegate selection decisions and record the parent-child edges.
 * </p>
 *
 * <p>
 * The edges map is thread-safe ({@link ConcurrentHashMap}) since
 * Aether may collect dependencies in parallel.
 * </p>
 */
class EdgeCollectorSelectorFactory implements DependencySelector {

    private final DependencySelector delegate;
    private final Map<ArtifactCoords, Set<ArtifactCoords>> edges;

    EdgeCollectorSelectorFactory(DependencySelector delegate,
            Map<ArtifactCoords, Set<ArtifactCoords>> edges) {
        this.delegate = delegate;
        this.edges = edges;
    }

    @Override
    public boolean selectDependency(Dependency dependency) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
        return new EdgeCollectorSelector(
                delegate.deriveChildSelector(context), edges,
                context.getArtifact());
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass())
            return false;
        return delegate.equals(((EdgeCollectorSelectorFactory) o).delegate);
    }

    @Override
    public int hashCode() {
        return 31 * delegate.hashCode();
    }

    /**
     * A {@link DependencySelector} that delegates all selection decisions
     * to a wrapped selector while recording parent-child edges for every
     * dependency encountered during collection.
     *
     * <p>
     * Each instance tracks the current parent artifact. When
     * {@link #selectDependency} is called, the parent-child edge is
     * recorded regardless of whether the delegate accepts the dependency,
     * except for {@code test}-scoped dependencies which are excluded to
     * avoid misleading edges in the SBOM. The downstream
     * {@code filterEdges} step ensures only assembly-present artifacts
     * appear in the final dependency graph.
     * </p>
     */
    static class EdgeCollectorSelector implements DependencySelector {

        private final DependencySelector delegate;
        private final Map<ArtifactCoords, Set<ArtifactCoords>> edges;
        private final org.eclipse.aether.artifact.Artifact parent;

        EdgeCollectorSelector(DependencySelector delegate,
                Map<ArtifactCoords, Set<ArtifactCoords>> edges,
                org.eclipse.aether.artifact.Artifact parent) {
            this.delegate = Objects.requireNonNull(delegate);
            this.edges = edges;
            this.parent = parent;
        }

        @Override
        public boolean selectDependency(Dependency dependency) {
            boolean selected = delegate.selectDependency(dependency);
            if (parent != null && !JavaScopes.TEST.equals(dependency.getScope())) {
                edges.computeIfAbsent(ArtifactCoords.of(parent), k -> ConcurrentHashMap.newKeySet())
                        .add(ArtifactCoords.of(dependency.getArtifact()));
            }
            return selected;
        }

        @Override
        public DependencySelector deriveChildSelector(DependencyCollectionContext context) {
            return new EdgeCollectorSelector(
                    delegate.deriveChildSelector(context), edges,
                    context.getArtifact());
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass())
                return false;
            EdgeCollectorSelector that = (EdgeCollectorSelector) o;
            return Objects.equals(parent, that.parent) && delegate.equals(that.delegate);
        }

        @Override
        public int hashCode() {
            return 31 * delegate.hashCode() + Objects.hashCode(parent);
        }
    }

}
