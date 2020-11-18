package io.playground.registry.maven.provider;

import java.util.Collection;

import javax.ws.rs.core.UriInfo;

import io.playground.registry.maven.ArtifactContentProvider;
import io.playground.registry.maven.QerConfig;
import io.quarkus.maven.ArtifactCoords;

public abstract class QuarkusVersionSpecificContentProvider implements ArtifactContentProvider {

	private Collection<String> quarkusVersions;
	
	public QuarkusVersionSpecificContentProvider(Collection<String> quarkusVersions) {
		this.quarkusVersions = quarkusVersions;
	}
	
	@Override
	public String getType() {
		return "json";
	}

	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {
		
		if(!quarkusVersions.contains(coords.getClassifier())) {
			throw new RuntimeException("Unsupported Quarkus core version " + coords.getClassifier());
		}
		
		return provideContent(coords);
	}

	protected abstract String provideContent(ArtifactCoords coords);
}
