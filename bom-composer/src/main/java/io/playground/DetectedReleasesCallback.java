package io.playground;

import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;

/**
 * Callback that receives events on detected releases and their content
 */
public interface DetectedReleasesCallback {

	/**
	 * Called only once at the beginning of the processing to communicate the BOM artifact
	 * that is being analyzed.
	 *
	 * @param bomArtifact  BOM that is being analyzed
	 */
	void startBom(Artifact bomArtifact);

	/**
	 * Called for every new detected release origin.
	 * This callback method will be followed up by one or more
	 * {@link #startReleaseVersion(ReleaseVersion, Collection)} invocations for the detected
	 * release versions from this origin.
	 *
	 * @param releaseOrigin  new detected release origin
	 */
	void startReleaseOrigin(ReleaseOrigin releaseOrigin);

	void endReleaseOrigin(ReleaseOrigin releaseOrigin);

	/**
	 * Called for every new release version.
	 *
	 * @param releaseVersion  release version
	 * @param artifacts  artifacts included in the release version
	 */
	void startReleaseVersion(ReleaseVersion releaseVersion, Collection<Artifact> artifacts);

	void endReleaseVersion(ReleaseVersion releaseVersion);

	/**
	 * Called after the last processed release version in the BOM.
	 */
	void endBom();
}
