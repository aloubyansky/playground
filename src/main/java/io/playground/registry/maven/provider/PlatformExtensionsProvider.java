package io.playground.registry.maven.provider;

import java.util.Arrays;

import javax.ws.rs.core.UriInfo;

import io.playground.registry.maven.ArtifactContentProvider;
import io.playground.registry.maven.QerConfig;
import io.playground.registry.maven.RegistryConstants;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.Constants;

public class PlatformExtensionsProvider implements ArtifactContentProvider {

	@Override
	public String getType() {
		return "json";
	}

	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {
		final JsonExtensionCatalog catalog = new JsonExtensionCatalog();
		catalog.setId(new ArtifactCoords(coords.getGroupId(), coords.getArtifactId(), coords.getClassifier(), "json", coords.getClassifier()).toString());
		catalog.setPlatform(true);
		
		final ArtifactCoords bom = new ArtifactCoords(coords.getGroupId(), coords.getArtifactId().substring(0, coords.getArtifactId().length() - Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX.length()), null, "pom", coords.getClassifier());
		catalog.setBom(bom);
		catalog.setQuarkusCoreVersion(RegistryConstants.QUARKUS_VERSION);
		
		JsonExtension e = new JsonExtension();
		catalog.addExtension(e);
		e.setArtifact(ArtifactCoords.fromString("io.playground:PLAYGROUND-QUARKUS-PLATFORM-EXTENSION::jar:1"));
		e.setName("Playgound Quarkus Platform Extension");
		e.setOrigins(Arrays.asList(catalog));
		
		return JsonUtil.toJson(catalog);
	}

}
