package io.playground.registry.maven.provider;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import javax.ws.rs.core.UriInfo;

import io.playground.registry.maven.ArtifactContentProvider;
import io.playground.registry.maven.QerConfig;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.config.json.JsonRegistryConfig;
import io.quarkus.registry.config.json.JsonRegistryDescriptorConfig;
import io.quarkus.registry.config.json.JsonRegistryNonPlatformExtensionsConfig;
import io.quarkus.registry.config.json.JsonRegistryPlatformsConfig;
import io.quarkus.registry.config.json.RegistriesConfigMapperHelper;

public class RegistryDescriptorProvider implements ArtifactContentProvider {

	@Override
	public String getType() {
		return "json";
	}

	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {		
		final JsonRegistryConfig config = new JsonRegistryConfig();
		
		final JsonRegistryDescriptorConfig descriptor = new JsonRegistryDescriptorConfig();
		config.setDescriptor(descriptor);
		descriptor.setArtifact(new ArtifactCoords(qerConfig.groupId(), qerConfig.descriptor(), null, "json", qerConfig.version()));
		
		final JsonRegistryPlatformsConfig platforms = new JsonRegistryPlatformsConfig();
		config.setPlatforms(platforms);
		//platforms.setExtensionCatalogsIncluded(true);
		platforms.setArtifact(new ArtifactCoords(qerConfig.groupId(), qerConfig.platforms(), null, "json", qerConfig.version()));

		final JsonRegistryNonPlatformExtensionsConfig nonPlatforms = new JsonRegistryNonPlatformExtensionsConfig();
		config.setNonPlatformExtensions(nonPlatforms);
		nonPlatforms.setArtifact(new ArtifactCoords(qerConfig.groupId(), qerConfig.nonPlatformExtensions(), null, "json", qerConfig.version()));
		nonPlatforms.setDisabled(true);

		return toJson(config);
	}
	
	static String toJson(Object config) {
		final StringWriter writer = new StringWriter();
		try(BufferedWriter b = new BufferedWriter(writer)) {
			RegistriesConfigMapperHelper.toJson(config, b);
		} catch (IOException e) {
			throw new RuntimeException("Failed to serialize descriptor", e);
		}
		return writer.getBuffer().toString();
	}
 }
