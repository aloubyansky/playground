package io.playground;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

public class ProjectRelease {

	public class Builder {

		private Builder() {
		}

		public Builder add(Artifact artifact) {
			return add(ProjectDependency.of(artifact));
		}

		public Builder add(ProjectDependency dep) {
			deps.add(dep);
			return this;
		}

		public ProjectRelease build() {
			return ProjectRelease.this;
		}
	}

	public static Builder builder(ReleaseId id) {
		return new ProjectRelease(id).new Builder();
	}

	public static ProjectRelease create(ReleaseId id, List<ProjectDependency> deps) {
		return new ProjectRelease(id, deps);
	}

	protected final ReleaseId id;
	protected List<ProjectDependency> deps = new ArrayList<>();

	private ProjectRelease(ReleaseId id) {
		this(id, null);
	}

	private ProjectRelease(ReleaseId id, List<ProjectDependency> deps) {
		this.id = id;
		this.deps = deps == null ? new ArrayList<>() : deps;
	}

	public ReleaseId id() {
		return id;
	}

	public List<ProjectDependency> dependencies() {
		return deps;
	}
}
