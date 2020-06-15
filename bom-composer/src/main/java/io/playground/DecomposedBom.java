package io.playground;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;

public class DecomposedBom {

	public class Builder {

		private Builder() {
		}

		public Builder setArtifact(Artifact bom) {
			bomArtifact = bom;
			return this;
		}

		public Builder addRelease(ProjectRelease release) {
			releases.computeIfAbsent(release.id().origin(), t -> new HashMap<>()).put(release.id().version(), release);
			return this;
		}

		public DecomposedBom build() {
			return DecomposedBom.this;
		}
	}

	public static Builder builder() {
		return new DecomposedBom().new Builder();
	}

	protected Artifact bomArtifact;
	protected Map<ReleaseOrigin, Map<ReleaseVersion, ProjectRelease>> releases = new HashMap<>();

	private DecomposedBom() {
	}

	public Artifact bomArtifact() {
		return bomArtifact;
	}

	public void visit(DecomposedBomVisitor visitor) throws BomDecomposerException {
		visitor.enterBom(bomArtifact);
		List<ReleaseOrigin> origins = new ArrayList<>(releases.keySet());
		for (ReleaseOrigin origin : origins) {
			final Collection<ProjectRelease> releaseVersions = releases.get(origin).values();
			if (visitor.enterReleaseOrigin(origin, releaseVersions.size())) {
				for (ProjectRelease v : releaseVersions) {
					visitor.visitProjectRelease(v);
				}
				visitor.leaveReleaseOrigin(origin);
			}
		}
		visitor.leaveBom();

	}
}
