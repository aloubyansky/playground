package io.playground.registry.maven.provider;

import javax.ws.rs.core.UriInfo;

import io.playground.registry.PlatformRegistry;
import io.playground.registry.maven.ArtifactContentProvider;
import io.playground.registry.maven.QerConfig;
import io.quarkus.maven.ArtifactCoords;

public class PlatformsProvider implements ArtifactContentProvider {

	private final PlatformRegistry registry;

	public PlatformsProvider(PlatformRegistry registry) {
		this.registry = registry;
	}

	@Override
	public String getType() {
		return "json";
	}

	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {
		return JsonUtil
				.toJson(registry.platformCatalog(coords.getClassifier().isEmpty() ? null : coords.getClassifier()));
	}
}
