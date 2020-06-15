package io.playground;

import org.eclipse.aether.artifact.Artifact;

public interface DecomposedBomBuilder {

	void bomArtifact(Artifact bomArtifact);

	void bomDependency(ReleaseId releaseId, Artifact artifact) throws BomDecomposerException;

	DecomposedBom build() throws BomDecomposerException;
}
