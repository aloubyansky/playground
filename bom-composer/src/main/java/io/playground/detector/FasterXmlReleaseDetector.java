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

public class FasterXmlReleaseDetector implements ReleaseIdDetector {

	@Override
	public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
		if (!artifact.getGroupId().startsWith("com.fasterxml.jackson")) {
			return null;
		}
		return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("com.fasterxml.jackson"),
				ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
	}
}
