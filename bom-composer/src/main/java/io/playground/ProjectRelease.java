package io.playground;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

public class ProjectRelease {

	public class Builder {

		private Builder() {
		}

		public Builder add(Artifact artifact) {
			artifacts.add(artifact);
			return this;
		}

		public ProjectRelease build() {
			return ProjectRelease.this;
		}
	}

	public static Builder builder(ReleaseId id) {
		return new ProjectRelease(id).new Builder();
	}

	public static ProjectRelease create(ReleaseId id, List<Artifact> artifacts) {
		return new ProjectRelease(id, artifacts);
	}

	protected final ReleaseId id;
	protected List<Artifact> artifacts = new ArrayList<>();

	private ProjectRelease(ReleaseId id) {
		this(id, null);
	}

	private ProjectRelease(ReleaseId id, List<Artifact> artifacts) {
		this.id = id;
		this.artifacts = artifacts == null ? new ArrayList<>() : artifacts;
	}

	public ReleaseId id() {
		return id;
	}

	public List<Artifact> artifacts() {
		return artifacts;
	}
}
