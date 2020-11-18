package io.playground.registry.maven.provider;

import java.util.Arrays;
import java.util.Collection;

import io.playground.registry.maven.RegistryConstants;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;

public class NonPlatformExtensionsProvider extends QuarkusVersionSpecificContentProvider {

	public NonPlatformExtensionsProvider(Collection<String> quarkusVersions) {
		super(quarkusVersions);
	}
	
	@Override
	protected String provideContent(ArtifactCoords coords) {
		final JsonExtensionCatalog catalog = new JsonExtensionCatalog();
		catalog.setId(coords.toString());
		
		final ArtifactCoords bom = ArtifactCoords.fromString("io.quarkus:quarkus-bom::pom:" + RegistryConstants.QUARKUS_VERSION);
		catalog.setBom(bom);
		catalog.setQuarkusCoreVersion(bom.getVersion());
		
		JsonExtension e = new JsonExtension();
		catalog.addExtension(e);
		e.setArtifact(ArtifactCoords.fromString("io.playground:playground-quarkus-extension::jar:1"));
		e.setName("Playgound Quarkus Extension");
		e.setOrigins(Arrays.asList(catalog));
		
		return JsonUtil.toJson(catalog);
	}
}
