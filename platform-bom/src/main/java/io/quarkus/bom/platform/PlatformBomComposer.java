package io.quarkus.bom.platform;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
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
import io.quarkus.bom.decomposer.util.BomDiff;
import io.quarkus.bom.decomposer.util.BomDiff.VersionChange;
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

	private Map<ReleaseOrigin, Map<ReleaseVersion, ProjectRelease.Builder>> releaseBuilders = new HashMap<>();
	private Map<AppArtifactKey, ProjectDependency> extensionDeps = new HashMap<>();

	private final DecomposedBom platformBom;

	public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {

		this.quarkusBom = BomDecomposer.config()
				//.debug()
				.logger(logger)
				.mavenArtifactResolver(resolver())
				.bomArtifact(config.quarkusBom())
				.decompose();
		quarkusBom.releases().forEach(r -> releaseBuilder(r.id()));

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

		for(ProjectDependency dep : extensionDeps.values()) {
			releaseBuilder(dep.releaseId()).add(dep);
		}
		final DecomposedBom.Builder platformBuilder = DecomposedBom.builder().setArtifact(config.bomArtifact());
		for(Map<ReleaseVersion, ProjectRelease.Builder> builders : releaseBuilders.values()) {
			for(ProjectRelease.Builder builder : builders.values()) {
				platformBuilder.addRelease(builder.build());
			}
		}
		platformBom = platformBuilder.build();
	}

	public DecomposedBom platformBom() {
		return platformBom;
	}

	@Override
	public DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom)
			throws BomDecomposerException {
		decomposedBom.visit(this);
		return decomposedBom;
	}

	@Override
	public void enterBom(Artifact bomArtifact) {
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
	public void visitProjectRelease(ProjectRelease release) {
		if(quarkusVersions.isEmpty()) {
			for(ProjectDependency dep : release.dependencies()) {
				final ProjectDependency current = extensionDeps.get(dep.key());
				if(current != null) {
					final ArtifactVersion currentVersion = new DefaultArtifactVersion(current.artifact().getVersion());
					final ArtifactVersion newVersion = new DefaultArtifactVersion(dep.artifact().getVersion());
					if(currentVersion.compareTo(newVersion) < 0) {
						extensionDeps.put(dep.key(), dep);
					}
				} else {
					extensionDeps.put(dep.key(), dep);
				}
			}
			return;
		}
		if(quarkusVersions.contains(release.id().version())) {
			for(ProjectDependency dep : release.dependencies()) {
				final ProjectRelease.Builder releaseBuilder = releaseBuilder(release.id());
				if(!releaseBuilder.includes(dep.key())) {
					releaseBuilder.add(dep);
				}
			}
			return;
		}
		//logger.error("CONFLICT: " + bomArtifact + " includes " + release.id() + " while Quarkus includes " + quarkusVersions);
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
			final ProjectRelease.Builder releaseBuilder = releaseBuilder(dep.releaseId());
			if(!releaseBuilder.includes(dep.key())) {
				releaseBuilder.add(dep);
			}

	    }
	}

	@Override
	public void leaveBom() throws BomDecomposerException {
		// TODO Auto-generated method stub

	}

	private ProjectRelease.Builder releaseBuilder(ReleaseId releaseId) {
		return releaseBuilders.computeIfAbsent(releaseId.origin(), i -> new HashMap<>()).computeIfAbsent(releaseId.version(), id -> {
			final ProjectRelease.Builder releaseBuilder = ProjectRelease.builder(releaseId);
			final ProjectRelease release = quarkusBom.releaseOrNull(releaseId);
			if(release != null) {
				release.dependencies().forEach(releaseBuilder::add);
			}
			return releaseBuilder;
		});
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
		final DecomposedBom platformBom = compose(PlatformBomConfig.forPom(pomDir.resolve("pom.xml")));
		PomUtils.toPom(platformBom, pomDir.resolve("platform-bom.xml"));

		final BomDiff bomDiff = BomDiff.config().compare(pomDir.resolve("platform-bom.xml")).to(pomDir.resolve("pom.xml"));
		log("COMPARING " + bomDiff.mainBom() + " TO " + bomDiff.toBom());
		log("  main deps total " + bomDiff.mainBomSize());
		log("  to deps total   " + bomDiff.toBomSize());
		log("  matching total  " + bomDiff.matching().size());

		if(bomDiff.hasMissing()) {
			log("MISSING");
			for(Dependency d : bomDiff.missing()) {
				log(" - " + d.getArtifact());
			}
		}
		if(bomDiff.hasExtra()) {
			log("EXTRA");
			for(Dependency d : bomDiff.extra()) {
				log(" - " + d.getArtifact());
			}
		}
		if(bomDiff.hasDowngraded()) {
			log("DOWNGRADED");
			for(VersionChange d : bomDiff.downgraded()) {
				log(" - " + d.from().getArtifact() + " -> " + d.to().getArtifact().getVersion());
			}
		}
		if(bomDiff.hasUpgraded()) {
			log("UPGRADED");
			for(VersionChange d : bomDiff.upgraded()) {
				log(" - " + d.from().getArtifact() + " -> " + d.to().getArtifact().getVersion());
			}
		}

	}

	private static void log(Object o) {
		System.out.println(o == null ? "null" : o.toString());
	}
}
