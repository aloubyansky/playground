package io.playground;

import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectDependency {

	public static ProjectDependency create(ReleaseId releaseId, Artifact artifact) {
		return new ProjectDependency(releaseId, artifact);
	}

	protected final ReleaseId releaseId;
	protected final Artifact artifact;
	protected Artifact availableUpdate;
	private AppArtifactKey key;

	private ProjectDependency(ReleaseId releaseId, Artifact artifact) {
		this.releaseId = Objects.requireNonNull(releaseId);
		this.artifact = Objects.requireNonNull(artifact);
	}

	public ReleaseId releaseId() {
		return releaseId;
	}

	public Artifact artifact() {
		return artifact;
	}

	public boolean isUpdateAvailable() {
		return availableUpdate != null;
	}

	public Artifact availableUpdate() {
		return availableUpdate;
	}

	public AppArtifactKey key() {
		return key == null ? key = new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension()) : key;
	}

	@Override
	public String toString() {
		return artifact.toString();
	}
}
