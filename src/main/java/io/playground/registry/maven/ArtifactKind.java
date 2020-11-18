package io.playground.registry.maven;

import java.util.function.Predicate;

import io.quarkus.maven.ArtifactCoords;

public class ArtifactKind {

	protected final Predicate<ArtifactCoords> predicate;
	protected final ArtifactContentProvider pomContentProvider;
	protected final ArtifactContentProvider mainContentProvider;
	protected final ArtifactContentProvider metadataContentProvider;
	
	public ArtifactKind(Predicate<ArtifactCoords> predicate,
			ArtifactContentProvider pomContentProvider,
			ArtifactContentProvider mainContentProvider,
			ArtifactContentProvider metadataContentProvider) {
		this.predicate = predicate;
		this.pomContentProvider = pomContentProvider;
		this.mainContentProvider = mainContentProvider;
		this.metadataContentProvider = metadataContentProvider;
	}
	
	public boolean matches(ArtifactCoords coords) {
		return predicate.test(coords);
	}
	
	public ArtifactContentProvider pomProvider() {
		return pomContentProvider;
	}
	
	public ArtifactContentProvider mainProvider() {
		return mainContentProvider;
	}
	
	public ArtifactContentProvider metadataProvider() {
		return metadataContentProvider;
	}
}
