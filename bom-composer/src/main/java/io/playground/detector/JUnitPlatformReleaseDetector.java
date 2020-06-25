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

public class JUnitPlatformReleaseDetector implements ReleaseIdDetector {

	@Override
	public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact) throws BomDecomposerException {
		if(!artifact.getGroupId().startsWith("org.junit")) {
			return null;
		}
		if(artifact.getGroupId().startsWith("org.junit.platform")) {
			return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("org.junit.platform"),
					ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
		}
		return null;
	}
}
