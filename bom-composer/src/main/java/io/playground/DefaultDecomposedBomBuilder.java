package io.playground;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;

public class DefaultDecomposedBomBuilder implements DecomposedBomBuilder {

	private Artifact bomArtifact;
	private Map<ReleaseId, List<ProjectDependency>> releases = new HashMap<>();

	@Override
	public void bomArtifact(Artifact bomArtifact) {
		this.bomArtifact = bomArtifact;
	}

	@Override
	public void bomDependency(ReleaseId releaseId, Artifact artifact) throws BomDecomposerException {
		releases.computeIfAbsent(releaseId, t -> new ArrayList<>()).add(ProjectDependency.of(artifact));
	}

	@Override
	public DecomposedBom build() throws BomDecomposerException {
		final DecomposedBom.Builder bomBuilder = DecomposedBom.builder();
		bomBuilder.setArtifact(bomArtifact);
		for(Map.Entry<ReleaseId, List<ProjectDependency>> entry : releases.entrySet()) {
			bomBuilder.addRelease(ProjectRelease.create(entry.getKey(), entry.getValue()));
		}
		return bomBuilder.build();
	}

}
