package io.playground;

import java.util.Objects;

import org.eclipse.aether.artifact.Artifact;

public class ProjectDependency {

	public static ProjectDependency of(Artifact artifact) {
		return new ProjectDependency(artifact);
	}

	protected final Artifact artifact;
	protected Artifact availableUpdate;

	private ProjectDependency(Artifact artifact) {
		this.artifact = Objects.requireNonNull(artifact);
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

	@Override
	public String toString() {
		return artifact.toString();
	}
}
