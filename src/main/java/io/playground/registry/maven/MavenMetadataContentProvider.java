package io.playground.registry.maven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.ws.rs.core.UriInfo;

import org.apache.maven.artifact.repository.metadata.Metadata;
import org.apache.maven.artifact.repository.metadata.Snapshot;
import org.apache.maven.artifact.repository.metadata.SnapshotVersion;
import org.apache.maven.artifact.repository.metadata.Versioning;
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Writer;

import io.playground.registry.PlatformRegistry;
import io.quarkus.maven.ArtifactCoords;

public class MavenMetadataContentProvider implements ArtifactContentProvider {

	private static final MetadataXpp3Writer METADATA_WRITER = new MetadataXpp3Writer();
	private static final List<String> EMPTY_CLASSIFIER = Arrays.asList("");

	private final PlatformRegistry registry;

	public MavenMetadataContentProvider(PlatformRegistry registry) {
		this.registry = registry;
	}

	@Override
	public String getType() {
		return "maven-metadata.xml";
	}

	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {

		final Metadata metadata = new Metadata();
		metadata.setModelVersion("1.1.0");
		metadata.setGroupId(coords.getGroupId());
		metadata.setArtifactId(coords.getArtifactId());
		metadata.setVersion(coords.getVersion());

		final Versioning versioning = new Versioning();
		metadata.setVersioning(versioning);
		versioning.updateTimestamp();

		Snapshot snapshot = new Snapshot();
		versioning.setSnapshot(snapshot);
		snapshot.setTimestamp(
				versioning.getLastUpdated().substring(0, 8) + "." + versioning.getLastUpdated().substring(8));
		snapshot.setBuildNumber(1);

		final String baseVersion = coords.getVersion().substring(0, coords.getVersion().length() - "SNAPSHOT".length());
		addSnapshotVersion(versioning, snapshot, baseVersion, EMPTY_CLASSIFIER, "pom");
		addSnapshotVersion(versioning, snapshot, baseVersion, EMPTY_CLASSIFIER, "json");
		addSnapshotVersion(versioning, snapshot, baseVersion, registry.recognizedQuarkusVersions(), "json");

		final StringWriter stringWriter = new StringWriter();
		try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
			METADATA_WRITER.write(writer, metadata);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to generate pom.xml for " + coords, e);
		}
		return stringWriter.getBuffer().toString();
	}

	private static void addSnapshotVersion(Versioning versioning, Snapshot snapshot, final String baseVersion,
			Collection<String> classifiers, String extension) {
		final String version = baseVersion + snapshot.getTimestamp() + "-" + snapshot.getBuildNumber();
		for (String classifier : classifiers) {
			final SnapshotVersion sv = new SnapshotVersion();
			sv.setClassifier(classifier);
			sv.setExtension(extension);
			sv.setVersion(version);
			sv.setUpdated(versioning.getLastUpdated());
			versioning.addSnapshotVersion(sv);
		}
	}
}
