package io.quarkus.bom.decomposer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectRelease {

	public class Builder {

		private Builder() {
		}

		private LinkedHashMap<AppArtifactKey, ProjectDependency> deps = new LinkedHashMap<>();

		public ReleaseId id() {
			return id;
		}

		public Builder add(Artifact artifact) {
			return add(ProjectDependency.create(id, artifact));
		}

		public Builder add(ProjectDependency dep) {
			final ProjectDependency existing = deps.put(dep.key(), dep);
			if (existing != null && !dep.artifact().getVersion().equals(existing.artifact().getVersion())) {
				throw new IllegalArgumentException(
						"Failed to add " + dep + " since the release already include " + existing);
			}
			return this;
		}

		public boolean includes(AppArtifactKey key) {
			return deps.containsKey(key);
		}

		public Collection<ProjectDependency> dependencies() {
			return deps.values();
		}

		public ProjectRelease build() {
			ProjectRelease.this.deps = new ArrayList<>(deps.values());
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
