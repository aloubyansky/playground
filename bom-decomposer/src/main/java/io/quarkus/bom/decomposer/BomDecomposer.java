package io.quarkus.bom.decomposer;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

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

		public BomDecomposerConfig logger(MessageWriter messageWriter) {
			logger = messageWriter;
			return this;
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

		public BomDecomposerConfig checkForUpdates() {
			return transform(new UpdateAvailabilityTransformer());
		}

		public BomDecomposerConfig transform(DecomposedBomTransformer bomTransformer) {
			transformer = bomTransformer;
			return this;
		}

		public DecomposedBom decompose() throws BomDecomposerException {
			ServiceLoader.load(ReleaseIdDetector.class, Thread.currentThread().getContextClassLoader()).forEach(d -> {
				BomDecomposer.this.logger().debug("Loaded release detector " + d);
				releaseDetectors.add(d);
			});
			return BomDecomposer.this.decompose();
		}
	}

	public static DecomposedBom decompose(String groupId, String artifactId, String version)
			throws BomDecomposerException {
		final BomDecomposer decomposer = new BomDecomposer();
		decomposer.bomArtifact = new DefaultArtifact(groupId, artifactId, "", "pom", version);
		return decomposer.decompose();
	}

	private BomDecomposer() {
	}

	private MessageWriter logger;
	private boolean debug;
	private Artifact bomArtifact;
	private MavenArtifactResolver mvnResolver;
	private List<ReleaseIdDetector> releaseDetectors = new ArrayList<>();
	private DecomposedBomBuilder decomposedBuilder;
	private DecomposedBomTransformer transformer;

	private MavenArtifactResolver artifactResolver() throws BomDecomposerException {
		try {
			return mvnResolver == null ? mvnResolver = new MavenArtifactResolver(new BootstrapMavenContext(
					BootstrapMavenContext.config().setArtifactTransferLogging(debug)))
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
				bomBuilder.bomDependency(releaseId(dep.getArtifact()), dep.getArtifact());
			} catch (BomDecomposerException e) {
				if (e.getCause() instanceof AppModelResolverException) {
					// there are plenty of BOMs that include artifacts that don't exist
					Object[] params = { dep.getArtifact() };
					logger().debug("Failed to resolve POM for %s", params);
				} else {
					throw e;
				}
			}
		}

		return transformer == null ? bomBuilder.build() : transformer.transform(this, bomBuilder.build());
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

	public MessageWriter logger() {
		return logger == null ? logger = new DefaultMessageWriter().setDebugEnabled(debug) : logger;
	}
	
	public Model model(Artifact artifact) throws BomDecomposerException {
		return Util.model(resolve(Util.pom(artifact)).getFile());
	}

	public ArtifactDescriptorResult describe(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolveDescriptor(artifact);
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact descriptor for " + artifact, e);
		}
	}

	public Artifact resolve(Artifact artifact) throws BomDecomposerException {
		try {
			return artifactResolver().resolve(artifact).getArtifact();
		} catch (Exception e) {
			throw new BomDecomposerException("Failed to resolve artifact " + artifact, e);
		}
	}

	public static void main(String[] args) throws Exception {
		BomDecomposer.config()
				.debug()
				.bomArtifact("io.quarkus", "quarkus-bom", "999-SNAPSHOT")
				.checkForUpdates()
				.decompose()
				.visit(DecomposedBomHtmlReportGenerator.builder("target/releases.html")
						.skipOriginsWithSingleRelease()
						.build());
	}
}
