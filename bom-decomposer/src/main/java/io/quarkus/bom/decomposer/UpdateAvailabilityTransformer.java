package io.quarkus.bom.decomposer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;

public class UpdateAvailabilityTransformer implements DecomposedBomTransformer {

	@Override
	public DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom)
			throws BomDecomposerException {
		Object[] params = { decomposedBom.bomArtifact() };
		decomposer.logger().debug("Transforming decomposed %s", params);
		decomposedBom.visit(new NoopDecomposedBomVisitor(true) {

			final List<ProjectRelease> releases = new ArrayList<>();
			Map<String, ReleaseId> versions = new HashMap<>();

			@Override
			public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {

				final List<ArtifactVersion> releaseVersions = new ArrayList<>();
				for(String versionStr : versions.keySet()) {
					releaseVersions.add(new DefaultArtifactVersion(versionStr));
				}
				Collections.sort(releaseVersions);

				for(ProjectRelease release : releases) {
					for(ProjectDependency dep : release.dependencies()) {
						// see if the dependency version can be derived from the project release tag/version
						final String releaseVersionStr = dep.releaseId().version().asString();
						final int depVersionIndex = releaseVersionStr.indexOf(dep.artifact().getVersion());
						if(depVersionIndex < 0 || releaseVersionStr.indexOf(dep.artifact().getVersion(), depVersionIndex + 1) > 0) {
							// give up
							continue;
						}
						for(int i = releaseVersions.size() - 1; i >= 0; --i) {
							final String versionStr = releaseVersions.get(i).toString();
							if(release.id().version().asString().equals(versionStr)) {
								break;
							}
							final String updatedDepVersion = versionStr.substring(depVersionIndex, versionStr.length() - (releaseVersionStr.length() - depVersionIndex - dep.artifact().getVersion().length()));
							final Artifact updatedArtifact = dep.artifact().setVersion(updatedDepVersion);
							if(isAvailable(decomposer, updatedArtifact)) {
								final ReleaseId updatedReleaseId = versions.get(versionStr);
								if(updatedReleaseId == null) {
									throw new BomDecomposerException("Failed to locate release ID for " + versionStr);
								}
								dep.availableUpdate = ProjectDependency.create(updatedReleaseId, updatedArtifact);
								break;
							}
						}
					}
				}
				releases.clear();
				versions.clear();
			}

			@Override
			public void visitProjectRelease(ProjectRelease release) {
				releases.add(release);
				versions.put(release.id().version().asString(), release.id());
			}
		});
		Object[] params1 = { decomposedBom.bomArtifact() };
		decomposer.logger().debug("Transformed decomposed BOM %s", params1);
		return decomposedBom;
	}

	private boolean isAvailable(BomDecomposer decomposer, Artifact artifact) {
		try {
			// we can't rely on artifact description here, unfortunately
			// since it may not fail if the artifact does not exist
			// so we are actually resolving the artifact
			decomposer.resolve(artifact);
			return true;
		} catch(BomDecomposerException e) {
			return false;
		}
	}
}
