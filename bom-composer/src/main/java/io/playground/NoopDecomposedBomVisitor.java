package io.playground;

import org.eclipse.aether.artifact.Artifact;

public class NoopDecomposedBomVisitor implements DecomposedBomVisitor {

	@Override
	public void enterBom(Artifact bomArtifact) {
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		return false;
	}

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) {
	}

	@Override
	public void leaveBom() {
	}
}
