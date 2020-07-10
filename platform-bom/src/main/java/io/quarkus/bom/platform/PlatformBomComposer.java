package io.quarkus.bom.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.DecomposedBomTransformer;
import io.quarkus.bom.decomposer.DecomposedBomVisitor;
import io.quarkus.bom.decomposer.DefaultMessageWriter;
import io.quarkus.bom.decomposer.MessageWriter;
import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseId;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class PlatformBomComposer implements DecomposedBomTransformer, DecomposedBomVisitor {

	public static DecomposedBom compose(PlatformBomConfig config) throws BomDecomposerException {
		return new PlatformBomComposer(config).platformBom();
	}

	private final DecomposedBom quarkusBom;
	private final MessageWriter logger = new DefaultMessageWriter();
	private MavenArtifactResolver resolver;

	private Collection<ReleaseVersion> quarkusVersions;
	private LinkedHashMap<String, ReleaseId> preferredVersions;

	private Map<AppArtifactKey, ProjectDependency> quarkusBomDeps = new HashMap<>();

	private Map<ReleaseOrigin, Map<ReleaseVersion, ProjectRelease.Builder>> externalReleaseDeps = new HashMap<>();

	private List<DecomposedBom> originalImportedBoms = new ArrayList<>();

	private final DecomposedBom platformBom;

	public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {

		this.quarkusBom = BomDecomposer.config()
				//.debug()
				.logger(logger)
				.mavenArtifactResolver(resolver())
				.bomArtifact(config.quarkusBom())
				.decompose();
		quarkusBom.releases().forEach(r -> {
			r.dependencies().forEach(d -> quarkusBomDeps.put(d.key(), d));
		});

		for(Artifact directDep : config.directDeps()) {
			final Iterable<Artifact> artifacts;
			if(directDep.getExtension().equals("pom")) {
				artifacts = managedDepsExcludingQuarkusBom(directDep);
			} else {
				artifacts = Collections.singleton(directDep);
			}
			BomDecomposer.config()
					.mavenArtifactResolver(resolver())
					//.debug()
					.logger(logger)
					.bomArtifact(directDep)
					.checkForUpdates()
					.artifacts(artifacts)
					.transform(this)
					.decompose();
		}

		platformBom = generatePlatformBom(config);
	}

	private DecomposedBom generatePlatformBom(PlatformBomConfig config) throws BomDecomposerException {
		final Map<ReleaseId, ProjectRelease.Builder> platformReleaseBuilders = new HashMap<>();
		for(ProjectDependency dep : quarkusBomDeps.values()) {
			platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
		}

		final Map<AppArtifactKey, ProjectDependency> extensionDeps = new HashMap<>();
		for(Map<ReleaseVersion, ProjectRelease.Builder> extReleaseBuilders : externalReleaseDeps.values()) {
			final List<ProjectRelease> releases = new ArrayList<>(extReleaseBuilders.size());
			extReleaseBuilders.values().forEach(b -> releases.add(b.build()));

			if(extReleaseBuilders.size() == 1) {
    		    mergeExtensionDeps(releases.get(0), extensionDeps);
				continue;
			}

			final LinkedHashMap<String, ReleaseId> preferredVersions = preferredVersions(releases);
			for (ProjectRelease release : releases) {
				for (Map.Entry<String, ReleaseId> preferred : preferredVersions.entrySet()) {
					if (release.id().equals(preferred.getValue())) {
						mergeExtensionDeps(release, extensionDeps);
						break;
					}
					for (ProjectDependency dep : release.dependencies()) {
						if (quarkusBomDeps.containsKey(dep.key())) {
							continue;
						}
						final String depVersion = dep.artifact().getVersion();
						if (!preferred.getKey().equals(depVersion)) {
							for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
								final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
								try {
									resolver().resolve(artifact);
									// logger.info(" EXISTS IN " + preferredVersion);
									dep = ProjectDependency.create(preferredVersion.getValue(), artifact);
									break;
								} catch (BootstrapMavenException e) {
								}
							}
						}
						addNonQuarkusDep(dep, extensionDeps);
					}
				}
			}
		}

		for(ProjectDependency dep : extensionDeps.values()) {
			platformReleaseBuilders.computeIfAbsent(dep.releaseId(), id -> ProjectRelease.builder(id)).add(dep);
		}
		final DecomposedBom.Builder platformBuilder = DecomposedBom.builder().bomArtifact(config.bomArtifact());
		for (ProjectRelease.Builder builder : platformReleaseBuilders.values()) {
			platformBuilder.addRelease(builder.build());
		}
		return platformBuilder.build();
	}

	private void mergeExtensionDeps(ProjectRelease release, Map<AppArtifactKey, ProjectDependency> extensionDeps) {
		for(ProjectDependency dep : release.dependencies()) {
			// the origin may have changed in the release of the dependency
			if(quarkusBomDeps.containsKey(dep.key())) {
				return;
			}
			addNonQuarkusDep(dep, extensionDeps);
		}
	}

	private void addNonQuarkusDep(ProjectDependency dep, Map<AppArtifactKey, ProjectDependency> extensionDeps) {
		final ProjectDependency currentDep = extensionDeps.get(dep.key());
		if(currentDep != null) {
			final ArtifactVersion currentVersion = new DefaultArtifactVersion(currentDep.artifact().getVersion());
			final ArtifactVersion newVersion = new DefaultArtifactVersion(dep.artifact().getVersion());
			if(currentVersion.compareTo(newVersion) < 0) {
				extensionDeps.put(dep.key(), dep);
			}
		} else {
		    extensionDeps.put(dep.key(), dep);
		}
	}

	public DecomposedBom platformBom() {
		return platformBom;
	}

	@Override
	public DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom)
			throws BomDecomposerException {
		originalImportedBoms.add(decomposedBom);
		decomposedBom.visit(this);
		return decomposedBom;
	}

	private Artifact extBom;

	@Override
	public void enterBom(Artifact bomArtifact) {
		extBom = bomArtifact;
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		preferredVersions = null;
		quarkusVersions = quarkusBom.releaseVersions(releaseOrigin);
		return true;
	}

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) throws BomDecomposerException {
		if(quarkusVersions.isEmpty()) {
			final ProjectRelease.Builder releaseBuilder = externalReleaseDeps
					.computeIfAbsent(release.id().origin(), id -> new HashMap<>())
					.computeIfAbsent(release.id().version(), id -> ProjectRelease.builder(release.id()));
			for(ProjectDependency dep : release.dependencies()) {
				releaseBuilder.add(dep);
			}
			return;
		}
		if(quarkusVersions.contains(release.id().version())) {
			for(ProjectDependency dep : release.dependencies()) {
				quarkusBomDeps.putIfAbsent(dep.key(), dep);
			}
			return;
		}
		//logger.error("CONFLICT: " + extBom + " includes " + release.id() + " while Quarkus includes " + quarkusVersions);
		final LinkedHashMap<String, ReleaseId> preferredVersions = this.preferredVersions == null ? this.preferredVersions = preferredVersions(quarkusBom.releases(release.id().origin())) : this.preferredVersions;
	    for(ProjectDependency dep : release.dependencies()) {
	    	final String depVersion = dep.artifact().getVersion();
			if (!preferredVersions.containsKey(depVersion)) {
				for (Map.Entry<String, ReleaseId> preferredVersion : preferredVersions.entrySet()) {
					final Artifact artifact = dep.artifact().setVersion(preferredVersion.getKey());
					try {
						resolver().resolve(artifact);
						//logger.info("  EXISTS IN " + preferredVersion);
						dep = ProjectDependency.create(preferredVersion.getValue(), artifact);
						break;
					} catch (BootstrapMavenException e) {
					}
				}
			}
			quarkusBomDeps.putIfAbsent(dep.key(), dep);
	    }
	}

	@Override
	public void leaveBom() throws BomDecomposerException {
		// TODO Auto-generated method stub

	}

	private LinkedHashMap<String,ReleaseId> preferredVersions(Collection<ProjectRelease> releases) {
		final TreeMap<ArtifactVersion, ReleaseId> treeMap = new TreeMap<>(Collections.reverseOrder());

		for (ProjectRelease release : releases) {
			final String releaseVersionStr = release.id().version().asString();
			for(ProjectDependency dep : release.dependencies()) {
				final int versionIndex = releaseVersionStr.indexOf(dep.artifact().getVersion());
				if(versionIndex < 0 || releaseVersionStr.indexOf(dep.artifact().getVersion(), versionIndex + 1) > 0) {
					// give up
					continue;
				}
				treeMap.put(new DefaultArtifactVersion(dep.artifact().getVersion()), release.id());
				break;
			}
		}
		final LinkedHashMap<String, ReleaseId> result = new LinkedHashMap<>(treeMap.size());
		for(Map.Entry<ArtifactVersion, ReleaseId> entry : treeMap.entrySet()) {
			result.put(entry.getKey().toString(), entry.getValue());
		}
		return result;
	}

	private Set<Artifact> managedDepsExcludingQuarkusBom(Artifact bom) throws BomDecomposerException {
		final Set<Artifact> result = new HashSet<>();
		final ArtifactDescriptorResult bomDescr = describe(bom);
		Artifact quarkusCore = null;
		for(Dependency dep : bomDescr.getManagedDependencies()) {
			final Artifact artifact = dep.getArtifact();
			result.add(artifact);
			if(quarkusCore == null && artifact.getArtifactId().equals("quarkus-core") && artifact.getGroupId().equals("io.quarkus")) {
				quarkusCore = artifact;
			}
		}
		if(quarkusCore != null) {
			logger.info("BOM " + bom + " imports " + quarkusCore);
			final ArtifactDescriptorResult quarkusBomDescr = describe(new DefaultArtifact("io.quarkus", "quarkus-bom", null, "pom", quarkusCore.getVersion()));
			for(Dependency quarkusBomDep : quarkusBomDescr.getManagedDependencies()) {
				result.remove(quarkusBomDep.getArtifact());
			}
		}
		return result;
	}

	private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
		try {
			return resolver().resolveDescriptor(artifact);
		} catch (BootstrapMavenException e) {
			throw new BomDecomposerException("Failed to describe " + artifact, e);
		}
	}

	private MavenArtifactResolver resolver() {
		try {
			return resolver == null ? resolver = MavenArtifactResolver.builder().build() : resolver;
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to initialize Maven artifact resolver", e);
		}
	}

	public static void main(String[] args) throws Exception {
		final Path pomDir = Paths.get(System.getProperty("user.home")).resolve("git")
				.resolve("quarkus-platform").resolve("bom").resolve("runtime");
		//PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.of(pomDir.resolve("pom.xml")));
		//PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.githubPom("quarkusio/quarkus-platform/master/bom/runtime/pom.xml"));
		PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.githubPom("quarkusio/quarkus-platform/1.5.2.Final/bom/runtime/pom.xml"));

		final DecomposedBom platformBom = compose(config);
		Path outputDir = Paths.get("target"); // pomDir
		PomUtils.toPom(platformBom, outputDir.resolve("platform-bom.xml"));

		final BomDiff bomDiff = BomDiff.config()
				//.compare(pomDir.resolve("pom.xml"))
				.compare(new DefaultArtifact("io.quarkus", "quarkus-universe-bom", null, "pom", "1.5.2.Final"))
				.to(outputDir.resolve("platform-bom.xml"));

		//BomDiffLogger.config().report(bomDiff);
		HtmlBomDiffReportGenerator.config(outputDir.resolve("platform-bom-diff.html")).report(bomDiff);

		platformBom.visit(DecomposedBomHtmlReportGenerator.builder(outputDir.resolve("platform-bom-releases.html"))
				.skipOriginsWithSingleRelease().build());
	}
}
