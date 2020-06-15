package io.playground;

import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomReleasesLogger extends NoopDecomposedBomVisitor {

	int originsCounter;
	int releasesCounter;

	@Override
	public void enterBom(Artifact bomArtifact) {
		log("PROJECT RELEASES INCLUDED IN " + bomArtifact);
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		++originsCounter;
		log(releaseOrigin);
		return true;
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) {
		++releasesCounter;
		log("  " + release.id().version());
		int artifactCounter = 1;
		for (Artifact a : release.artifacts()) {
			log("    " + artifactCounter++ + ") " + a);
		}
	}

	@Override
	public void leaveBom() {
		log("TOTAL:");
		log("  Release origins: " + originsCounter);
		log("  Release versions: " + releasesCounter);
	}

	private static void log(Object msg) {
		System.out.println(msg);
	}
}
