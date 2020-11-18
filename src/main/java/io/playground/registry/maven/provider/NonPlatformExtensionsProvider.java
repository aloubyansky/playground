package io.playground.registry.maven.provider;

import java.util.Arrays;

import io.playground.registry.PlatformRegistry;
import io.playground.registry.maven.RegistryConstants;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;

public class NonPlatformExtensionsProvider extends QuarkusVersionSpecificContentProvider {

	public NonPlatformExtensionsProvider(PlatformRegistry registry) {
		super(registry);
	}

	@Override
	protected String provideContent(ArtifactCoords coords) {
		
		final JsonExtensionCatalog catalog = new JsonExtensionCatalog();
		catalog.setId(coords.toString());

		final ArtifactCoords bom = ArtifactCoords
				.fromString("io.quarkus:quarkus-bom::pom:" + coords.getClassifier());
		catalog.setBom(bom);
		catalog.setQuarkusCoreVersion(bom.getVersion());

		JsonExtension e = new JsonExtension();
		catalog.addExtension(e);
		e.setArtifact(ArtifactCoords.fromString("io.playground:playground-quarkus-extension::jar:" + coords.getClassifier()));
		e.setName("Playgound Quarkus Extension");
		e.setOrigins(Arrays.asList(catalog));

		return JsonUtil.toJson(catalog);
	}
}
