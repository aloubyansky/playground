package io.playground;

import java.util.HashMap;
import java.util.Map;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectRelease {

	public class Builder {

		private Builder() {
		}

		public Builder add(ProjectArtifact artifact) {
			artifacts.put(artifact.key(), artifact);
			return this;
		}

		public ProjectRelease build() {
			return ProjectRelease.this;
		}
	}

	public static Builder builder() {
		return new ProjectRelease().new Builder();
	}

	/*
	public static ProjectReleaseId id(Scm scm) {
		return new ProjectReleaseId
	}
	*/

	protected final Map<AppArtifactKey, ProjectArtifact> artifacts = new HashMap<>();

	private ProjectRelease() {
	}
}
