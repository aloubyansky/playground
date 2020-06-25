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

public class VertxReleaseDetector implements ReleaseIdDetector {

	@Override
	public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
		if (!artifact.getGroupId().startsWith("io.vertx")) {
			return null;
		}
		if(artifact.getArtifactId().equals("vertx-docgen")) {
			return null;
		}
		return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("io.vertx"),
				ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
	}
}
