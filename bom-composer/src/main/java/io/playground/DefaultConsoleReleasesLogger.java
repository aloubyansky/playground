package io.playground;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;

public class DefaultConsoleReleasesLogger implements DetectedReleasesCallback {

	int originsCounter;
	int releasesCounter;

	@Override
	public void startBom(Artifact bomArtifact) {
		log("PROJECT RELEASES INCLUDED IN " + bomArtifact);
	}

	@Override
	public void startReleaseOrigin(ReleaseOrigin releaseOrigin) {
		++originsCounter;
		log(releaseOrigin);
	}

	@Override
	public void startReleaseVersion(ReleaseVersion releaseVersion, Collection<Artifact> artifacts) {
		++releasesCounter;
		log("  " + releaseVersion);
		int artifactCounter = 1;
		for (Artifact a : artifacts) {
			log("    " + artifactCounter++ + ") " + a);
		}
	}

	@Override
	public void endBom() {
		log("TOTAL:");
		log("  Release origins: " + originsCounter);
		log("  Release versions: " + releasesCounter);
	}

	private static void log(Object msg) {
		System.out.println(msg);
	}

	@Override
	public void endReleaseOrigin(ReleaseOrigin releaseOrigin) {
	}

	@Override
	public void endReleaseVersion(ReleaseVersion releaseVersion) {
	}
}
