package io.playground.detector;

import org.eclipse.aether.artifact.Artifact;

import io.playground.BomDecomposer;
import io.playground.BomDecomposerException;
import io.playground.ReleaseId;
import io.playground.ReleaseIdDetector;
import io.playground.ReleaseIdFactory;
import io.playground.ReleaseOrigin;
import io.playground.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class KafkaReleaseDetector implements ReleaseIdDetector {

	@Override
	public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
		if (!artifact.getGroupId().startsWith("org.apache.kafka")) {
			return null;
		}
		// Kafka is published from a Gradle project, so the POM is generated and
		// includes
		// neither parent info nor scm.
		// Some JAR artifacts do include kafka/kafka-xxx-version.properties that
		// includes
		// commitId and version. But for simplicity we are simply using the version of
		// the artifact here
		return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("org.apache.kafka"),
				ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
	}
}
