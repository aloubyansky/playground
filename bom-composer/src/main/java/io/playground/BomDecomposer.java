package io.playground;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class BomDecomposer {

	public static BomDecomposerConfig config() {
		return new BomDecomposer().new BomDecomposerConfig();
	}

	public class BomDecomposerConfig {

		private BomDecomposerConfig() {
		}

		public BomDecomposerConfig debug() {
			debug = true;
			return this;
		}

		public BomDecomposerConfig mavenArtifactResolver(MavenArtifactResolver resolver) {
			mvnResolver = resolver;
			return this;
		}

		public BomDecomposerConfig bomArtifact(String groupId, String artifactId, String version) {
			bomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);
			return this;
		}

		public BomDecomposerConfig addReleaseDetector(ReleaseIdDetector releaseDetector) {
			releaseDetectors.add(releaseDetector);
			return this;
		}

		public DecomposedBom decompose() throws BomDecomposerException {
			return BomDecomposer.this.decompose();
		}
	}

	public static DecomposedBom decompose(String groupId, String artifactId, String version)
			throws BomDecomposerException {
		final BomDecomposer decomposer = new BomDecomposer();
		decomposer.bomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);
		return decomposer.decompose();
	}

	private boolean debug;
	private Artifact bomArtifact;
	private MavenArtifactResolver mvnResolver;
	private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
	private DecomposedBomBuilder decomposedBuilder;

	private MavenArtifactResolver artifactResolver() throws BomDecomposerException {
		try {
			return mvnResolver == null ? mvnResolver = new MavenArtifactResolver(new BootstrapMavenContext())
					: mvnResolver;
		} catch (AppModelResolverException e) {
			throw new BomDecomposerException("Failed to initialize Maven artifact resolver", e);
		}
	}

	private DecomposedBom decompose() throws BomDecomposerException {
		if (!"pom".equals(bomArtifact.getExtension())) {
			throw new BomDecomposerException("The BOM artifact " + bomArtifact + " is not of type 'pom'");
		}

		final ArtifactDescriptorResult descriptor = describe(bomArtifact);

		final DecomposedBomBuilder bomBuilder = decomposedBuilder == null ? new DefaultDecomposedBomBuilder() : decomposedBuilder;
		bomBuilder.bomArtifact(bomArtifact);
		for (Dependency dep : descriptor.getManagedDependencies()) {
			try {
				final ReleaseId releaseId = releaseId(dep.getArtifact());
				bomBuilder.bomDependency(releaseId, dep.getArtifact());
			} catch (BomDecomposerException e) {
				if (e.getCause() instanceof AppModelResolverException) {
					debug("Failed to resolve POM for %s", dep.getArtifact());
				} else {
					throw e;
				}
			}
		}

		return bomBuilder.build();
	}

	private boolean print;

	private void print(String msg) {
		if (print) {
			log(msg);
		}
	}

	private ReleaseId releaseId(Artifact artifact) throws BomDecomposerException {
		for (ReleaseIdDetector releaseDetector : releaseDetectors) {
			final ReleaseId releaseId = releaseDetector.detectReleaseId(this, artifact);
			if (releaseId != null) {
				return releaseId;
			}
		}
		Model model = model(artifact);
		Model tmp;
		while ((tmp = workspaceParent(model)) != null) {
			model = tmp;
		}
		return ReleaseIdFactory.forModel(model);
	}

	private Model workspaceParent(Model model) throws BomDecomposerException {
		if (model.getParent() == null) {
			return null;
		}

		final Model parentModel = model(Util.parentArtifact(model));

		if (Util.getScmOrigin(model) != null) {
			return Util.getScmOrigin(model).equals(Util.getScmOrigin(parentModel))
					&& Util.getScmTag(model).equals(Util.getScmTag(parentModel)) ? parentModel : null;
		}

		if (model.getParent().getRelativePath().isBlank()) {
			return null;
		}

		if (model.getVersion() == null || !"../pom.xml".equals(model.getParent().getRelativePath())
				|| ModelUtils.getGroupId(parentModel).equals(ModelUtils.getGroupId(model))
						&& ModelUtils.getVersion(parentModel).equals(ModelUtils.getVersion(model))) {
			return parentModel;
		}

		if (parentModel.getModules().isEmpty()) {
			return null;
		}
		for (String path : parentModel.getModules()) {
			final String dirName = Paths.get(path).getFileName().toString();
			if (model.getArtifactId().contains(dirName)) {
				return parentModel;
			}
		}
		return null;
	}

	private Model model(Artifact artifact) throws BomDecomposerException {
		return Util.model(resolve(Util.pom(artifact)).getFile());
	}

	private ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolveDescriptor(artifact);
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact descriptor for " + artifact, e);
		}
	}

	private Artifact resolve(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolve(artifact).getArtifact();
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact " + artifact, e);
		}
	}

	private void debug(String msg, Object... params) {
		if (debug) {
			log(msg, params);
		}
	}

	private void debug(String msg) {
		if (debug) {
			log(msg);
		}
	}

	private static void log(String msg, Object... params) {
		log(String.format(msg, params));
	}

	private static void log(Object msg) {
		System.out.println(msg);
	}

	public static void main(String[] args) throws Exception {
		BomDecomposer.config().debug().bomArtifact("io.quarkus", "quarkus-universe-bom-deployment", "1.5.1.Final")
				.addReleaseDetector(new ReleaseIdDetector() {
					@Override
					public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact)
							throws BomDecomposerException {
						if (!artifact.getGroupId().startsWith("org.apache.kafka")) {
							return null;
						}
						// Kafka is published from a Gradle project, so the POM is generated and
						// includes
						// neither parent info nor scm.
						// Some JAR artifacts do include kafka/kafka-xxx-version.properties that
						// includes
						// commitId and version. But for simplicity we are simply using the version of
						// the artifact here
						return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("org.apache.kafka"),
								ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
					}
				}).addReleaseDetector(new ReleaseIdDetector() {
					@Override
					public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact)
							throws BomDecomposerException {
						if (!artifact.getGroupId().startsWith("io.vertx")) {
							return null;
						}
						return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("io.vertx"),
								ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
					}
				}).addReleaseDetector(new ReleaseIdDetector() {
					@Override
					public ReleaseId detectReleaseId(BomDecomposer decomposer, Artifact artifact)
							throws BomDecomposerException {
						if (!artifact.getGroupId().startsWith("com.fasterxml.jackson")) {
							return null;
						}
						return ReleaseIdFactory.create(ReleaseOrigin.Factory.scmConnection("com.fasterxml.jackson"),
								ReleaseVersion.Factory.version(ModelUtils.getVersion(decomposer.model(artifact))));
					}
				}).decompose().visit(DecomposedBomHtmlReportGenerator.builder("target/releases.html").skipSingleReleases().build());
	}
}
