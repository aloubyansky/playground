package io.quarkus.bom.platform;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class BomComposer {

	public static void compose(PlatformBomConfig config) throws BomDecomposerException {
		try {
			new BomComposer(config).compose();
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to compose the platform BOM", e);
		}
	}

	private final PlatformBomConfig config;
	private MavenArtifactResolver resolver;

	private BomComposer(PlatformBomConfig config) {
		this.config = Objects.requireNonNull(config);
	}

	private void compose() throws Exception {
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
					.debug()
					.bomArtifact(directDep)
					.checkForUpdates()
					.artifacts(artifacts)
					.decompose();
			decomposedBoms.add(decomposedBom);
			decomposedBom.visit(DecomposedBomHtmlReportGenerator.builder("target/decomposed-" + directDep.getArtifactId() + ".html").build());
		}

		final DecomposedBom quarkusDecomposedBom = BomDecomposer.config()
				.debug()
				.mavenArtifactResolver(resolver())
				.bomArtifact(config.quarkusBom())
				.decompose();

		log("done");
	}

	private Set<Artifact> managedDepsExcludingQuarkusBom(Artifact bom) throws Exception {
		final Set<Artifact> result = new HashSet<>();
		final ArtifactDescriptorResult bomDescr = resolver().resolveDescriptor(bom);
		Artifact quarkusCore = null;
		for(Dependency dep : bomDescr.getManagedDependencies()) {
			final Artifact artifact = dep.getArtifact();
			result.add(artifact);
			if(quarkusCore == null && artifact.getArtifactId().equals("quarkus-core") && artifact.getGroupId().equals("io.quarkus")) {
				quarkusCore = artifact;
			}
		}
		if(quarkusCore != null) {
			log("BOM " + bom + " imports " + quarkusCore);
			final ArtifactDescriptorResult quarkusBomDescr = resolver().resolveDescriptor(new DefaultArtifact("io.quarkus", "quarkus-bom", null, "pom", quarkusCore.getVersion()));
			for(Dependency quarkusBomDep : quarkusBomDescr.getManagedDependencies()) {
				result.remove(quarkusBomDep.getArtifact());
			}
		}
		return result;
	}

	private MavenArtifactResolver resolver() {
		try {
			return resolver == null ? resolver = MavenArtifactResolver.builder().build() : resolver;
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to initialize Maven artifact resolver", e);
		}
	}

	private static void log(Object o) {
		System.out.println(o == null ? "null" : o.toString());
	}

	public static void main(String[] args) throws Exception {
		compose(PlatformBomConfig.forPom(Paths.get(System.getProperty("user.home")).resolve("git")
				.resolve("quarkus-platform").resolve("bom").resolve("runtime").resolve("pom.xml")));
	}
}
