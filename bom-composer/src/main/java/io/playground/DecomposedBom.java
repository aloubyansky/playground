package io.playground;

import org.eclipse.aether.artifact.Artifact;

public class DecomposedBom {

	public class Builder {

		private Builder() {
		}

		public Builder setArtifact(Artifact bom) {
			bomArtifact = bom;
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

	private DecomposedBom() {
	}

	public Artifact artifact() {
		return bomArtifact;
	}
}
