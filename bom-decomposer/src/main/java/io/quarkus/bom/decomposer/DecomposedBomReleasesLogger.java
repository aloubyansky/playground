package io.quarkus.bom.decomposer;

import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomReleasesLogger extends NoopDecomposedBomVisitor {


	public DecomposedBomReleasesLogger() {
		super();
	}

	public DecomposedBomReleasesLogger(boolean skipOriginsWithSingleRelease) {
		super(skipOriginsWithSingleRelease);
	}

	private int originCounter;
	private int releaseCounter;
	private int artifactCounter;

	@Override
	public void enterBom(Artifact bomArtifact) {
		log("Multi Module Project Releases Detected Among The Managed Dependencies of " + bomArtifact);
		if(skipOriginsWithSingleRelease) {
			log("(release origins with a single release were filtered out)");
		}
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		final boolean result = super.enterReleaseOrigin(releaseOrigin, versions);
		if (result) {
			++originCounter;
			log(releaseOrigin);
		}
		return result;
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) {
		++releaseCounter;
		log("  " + release.id().version());
		int artifactCounter = 0;
		for (ProjectDependency dep : release.dependencies()) {
			log("    " + ++artifactCounter + ") " + dep);
		}
		this.artifactCounter += artifactCounter;
	}

	@Override
	public void leaveBom() {
		log("TOTAL REPORTED");
		log("  Release origins:  " + originCounter);
		log("  Release versions: " + releaseCounter);
		log("  Artifacts:        " + artifactCounter);
	}

	private static void log(Object msg) {
		System.out.println(msg);
	}
}
