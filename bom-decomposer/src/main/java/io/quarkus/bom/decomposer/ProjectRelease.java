package io.quarkus.bom.decomposer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectRelease {

	public class Builder {

		private Builder() {
		}

		private Set<AppArtifactKey> depKeys;

		public Builder add(Artifact artifact) {
			return add(ProjectDependency.create(id, artifact));
		}

		public Builder add(ProjectDependency dep) {
			deps.add(dep);
			if(depKeys != null) {
				depKeys.add(dep.key());
			}
			return this;
		}

		public boolean includes(AppArtifactKey key) {
			if(depKeys == null) {
				final Set<AppArtifactKey> depKeys = new HashSet<>(deps.size());
				deps.forEach(d -> depKeys.add(d.key()));
				this.depKeys = depKeys;
			}
			return depKeys.contains(key);
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
