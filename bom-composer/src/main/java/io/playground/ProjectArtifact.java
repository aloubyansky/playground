package io.playground;

import org.eclipse.aether.artifact.Artifact;

import io.quarkus.bootstrap.model.AppArtifactKey;

public class ProjectArtifact {

	public class Builder {

		private Builder() {
		}

		public Builder artifact(Artifact artifact) {
			ProjectArtifact.this.artifact = artifact;
			key = Util.key(artifact);
			return this;
		}

    	public ProjectArtifact build() throws BomDecomposerException {
			if(artifact == null) {
				throw new BomDecomposerException("Artifact is null");
			}
			return ProjectArtifact.this;
		}
	}

	public static Builder builder() {
		return new ProjectArtifact().new Builder();
	}

	protected Artifact artifact;
	protected AppArtifactKey key;

	private ProjectArtifact() {
	}

	public AppArtifactKey key() {
		return key;
	}

	public Artifact artifact() {
		return artifact;
	}
}
