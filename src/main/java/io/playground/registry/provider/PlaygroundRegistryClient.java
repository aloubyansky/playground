package io.playground.registry.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.quarkus.devtools.messagewriter.MessageWriter;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.RegistryResolutionException;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformCatalog;
import io.quarkus.registry.catalog.json.JsonExtension;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonPlatform;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.catalog.json.JsonPlatformRelease;
import io.quarkus.registry.catalog.json.JsonPlatformReleaseVersion;
import io.quarkus.registry.catalog.json.JsonPlatformStream;
import io.quarkus.registry.client.RegistryClient;
import io.quarkus.registry.client.spi.RegistryClientEnvironment;
import io.quarkus.registry.config.RegistryConfig;

public class PlaygroundRegistryClient implements RegistryClient {

	private static final ArtifactCoords PLAYGROUND_BOM = ArtifactCoords.fromString("io.playground:playground-quarkus-bom:1.0");

	private final MessageWriter log;
	private final RegistryConfig config;

	public PlaygroundRegistryClient(RegistryClientEnvironment env, RegistryConfig config) {
		this.config = Objects.requireNonNull(config);
		this.log = env.log();
		log.info("PlaygroundRegistryClient for " + config.getId());
	}

	@Override
	public ExtensionCatalog resolveNonPlatformExtensions(String quarkusVersion) throws RegistryResolutionException {
		log.info("PlaygroundRegistryClient.resolveNonPlatformExtensions quarkusVersion=" + quarkusVersion);
		return null;
	}

	@Override
	public ExtensionCatalog resolvePlatformExtensions(ArtifactCoords platformCoords)
			throws RegistryResolutionException {
		log.info("PlaygroundRegistryClient.resolvePlatformExtensions " + platformCoords);

		final String quarkusVersion = platformCoords.getClassifier().isEmpty() ? "999-SNAPSHOT" : platformCoords.getClassifier();

		final JsonExtensionCatalog catalog = new JsonExtensionCatalog();
		catalog.setId(platformCoords.toString());
		catalog.setBom(PLAYGROUND_BOM);
		catalog.setPlatform(true);
		catalog.setQuarkusCoreVersion(quarkusVersion);

		final JsonExtension extension = new JsonExtension();
		catalog.addExtension(extension);
		extension.setArtifact(ArtifactCoords.fromString("io.playground:playground-extension:1.0"));
		extension.setOrigins(Arrays.asList(catalog));

		Map<String, Object> props = new HashMap<>();
		props.put("maven-plugin-groupId", "io.quarkus");
		props.put("maven-plugin-artifactId", "quarkus-maven-plugin");
		props.put("maven-plugin-version", quarkusVersion);
		props.put("compiler-plugin-version", "3.8.1");
		props.put("surefire-plugin-version", "3.0.0-M5");

		catalog.setMetadata(Collections.singletonMap("project", Collections.singletonMap("properties", props)));
		return catalog;
	}

	@Override
	public PlatformCatalog resolvePlatforms(String quarkusVersion) throws RegistryResolutionException {
		log.info("PlaygroundRegistryClient.resolvePlatforms quarkusVersion=" + quarkusVersion);

		final ArtifactCoords playgroundBom = PLAYGROUND_BOM;

		final JsonPlatformCatalog catalog = new JsonPlatformCatalog();
		final JsonPlatform platform = new JsonPlatform();
		catalog.addPlatform(platform);

		final JsonPlatformStream stream = new JsonPlatformStream();
		platform.setStreams(Collections.singletonList(stream));
		stream.setId(playgroundBom.getVersion());

		final JsonPlatformRelease release = new JsonPlatformRelease();
		release.setVersion(JsonPlatformReleaseVersion.fromString(playgroundBom.getVersion()));
		release.setQuarkusCoreVersion(quarkusVersion == null ? "999-SNAPSHOT" : quarkusVersion);
		release.setMemberBoms(Collections.singletonList(playgroundBom));
		return catalog;
	}

	@Override
	public RegistryConfig resolveRegistryConfig() throws RegistryResolutionException {
		return config;
	}

	@Override
	public void clearCache() throws RegistryResolutionException {
	}
}
