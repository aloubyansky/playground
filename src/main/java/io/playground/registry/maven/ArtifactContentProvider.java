package io.playground.registry.maven;

import javax.ws.rs.core.UriInfo;

import io.quarkus.maven.ArtifactCoords;

public interface ArtifactContentProvider {

	String getType();
	
	String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo);
}
