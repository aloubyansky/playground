package io.quarkus.bom.platform;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
import io.quarkus.bom.decomposer.ProjectDependency;
import io.quarkus.bom.decomposer.ProjectRelease;
import io.quarkus.bom.decomposer.ReleaseOrigin;
import io.quarkus.bom.decomposer.ReleaseVersion;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class PlatformBomComposer implements DecomposedBomTransformer, DecomposedBomVisitor {

	public static DecomposedBom compose(PlatformBomConfig config) throws BomDecomposerException {
		return new PlatformBomComposer(config).platformBom();
	}

	private final DecomposedBom quarkusBom;
	private final DecomposedBom.Builder platformBuilder;
	private final MessageWriter logger = new DefaultMessageWriter();
	private MavenArtifactResolver resolver;

	private Artifact bomArtifact;
	
	private Collection<ReleaseVersion> quarkusVersions;
	private List<String> preferredVersions;

	public PlatformBomComposer(PlatformBomConfig config) throws BomDecomposerException {
		
		platformBuilder = DecomposedBom.builder().setArtifact(config.bomArtifact());
		this.quarkusBom = BomDecomposer.config()
				//.debug()
				.logger(logger)
				.mavenArtifactResolver(resolver())
				.bomArtifact(config.quarkusBom())
				.decompose();
		
		final List<DecomposedBom> decomposedBoms = new ArrayList<>();
		for(Artifact directDep : config.directDeps()) {
			final Iterable<Artifact> artifacts;
			if(directDep.getExtension().equals("pom")) {
				artifacts = managedDepsExcludingQuarkusBom(directDep);
			} else {
				artifacts = Collections.singleton(directDep);
			}
			final DecomposedBom decomposedBom = BomDecomposer.config()
					.mavenArtifactResolver(resolver())
					//.debug()
					.logger(logger)
					.bomArtifact(directDep)
					.checkForUpdates()
					.artifacts(artifacts)
					.transform(this)
					.decompose();
			decomposedBoms.add(decomposedBom);
			//decomposedBom.visit(DecomposedBomHtmlReportGenerator.builder("target/decomposed-" + directDep.getArtifactId() + ".html").build());
		}

	}
	
	public DecomposedBom platformBom() {
		return platformBuilder.build();
	}
	
	@Override
	public DecomposedBom transform(BomDecomposer decomposer, DecomposedBom decomposedBom)
			throws BomDecomposerException {
		decomposedBom.visit(this);
		return decomposedBom;
	}

	@Override
	public void enterBom(Artifact bomArtifact) {
		this.bomArtifact = bomArtifact;
	}

	@Override
	public boolean enterReleaseOrigin(ReleaseOrigin releaseOrigin, int versions) {
		preferredVersions = null;
		quarkusVersions = quarkusBom.releaseVersions(releaseOrigin);
		// we are interested in potential conflicts
		return !quarkusVersions.isEmpty();
	}

	@Override
	public void leaveReleaseOrigin(ReleaseOrigin releaseOrigin) throws BomDecomposerException {
	}

	@Override
	public void visitProjectRelease(ProjectRelease release) {
		if(quarkusVersions.contains(release.id().version())) {
			return;
		}
		logger.error("CONFLICT: " + bomArtifact + " includes " + release.id() + " while Quarkus includes " + quarkusVersions);
		final List<String> preferredVersions = this.preferredVersions == null ? preferredVersions(quarkusBom.releases(release.id().origin())) : this.preferredVersions;
	    for(ProjectDependency dep : release.dependencies()) {
	    	final String depVersion = dep.artifact().getVersion();
	    	if(preferredVersions.contains(depVersion)) {
	    		continue;
	    	}
	    	for(String preferredVersion : preferredVersions) {
	    		final Artifact artifact = dep.artifact().setVersion(preferredVersion);
	    		try {
					resolver().resolve(artifact);
					logger.info("  EXISTS IN " + preferredVersion);
					dep.setAvailableUpdate(ProjectDependency.create(dep.releaseId(), artifact));
					break;
				} catch (BootstrapMavenException e) {
					// probably does not exist, ignore
				}
	    	}
	    }
	}

	@Override
	public void leaveBom() throws BomDecomposerException {
		// TODO Auto-generated method stub
		
	}
	
	private List<String> preferredVersions(Collection<ProjectRelease> releases) {
		final List<ArtifactVersion> versions = new ArrayList<>(releases.size());
		for (ProjectRelease release : releases) {
			final String releaseVersionStr = release.id().version().asString();
			for(ProjectDependency dep : release.dependencies()) {
				final int versionIndex = releaseVersionStr.indexOf(dep.artifact().getVersion());
				if(versionIndex < 0 || releaseVersionStr.indexOf(dep.artifact().getVersion(), versionIndex + 1) > 0) {
					// give up
					continue;
				}
				versions.add(new DefaultArtifactVersion(dep.artifact().getVersion()));
				break;
			}
		}
		if(versions.size() > 1) {
		    Collections.sort(versions, Collections.reverseOrder());
		}
		final List<String> result = new ArrayList<>(releases.size());
		for(ArtifactVersion v : versions) {
			result.add(v.toString());
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
		compose(PlatformBomConfig.forPom(Paths.get(System.getProperty("user.home")).resolve("git")
				.resolve("quarkus-platform").resolve("bom").resolve("runtime").resolve("pom.xml")));
	}
}
