package io.playground.gradle;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradlePublication;
import org.gradle.tooling.model.gradle.ProjectPublications;

import io.quarkus.bom.decomposer.maven.DependenciesToBuildReportGenerator;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;

public class GradleProjectReader {

	private static final boolean RESOLVE_MODULE_METADATA = false;
	private static final boolean JDK8 = false;
	private static final boolean REPORT_DEPS_TO_BUILD = true;

	public static void main(String[] args) throws Exception {

		final long startTime = System.currentTimeMillis();

		final PublishedProject project = getPublishedProject("kafka", "3.2.3");
		//final PublishedProject project = getPublishedProject("micrometer", "1.9.5");
		//final PublishedProject project = getPublishedProject("opentelemetry-java", "1.19.0");
		//final PublishedProject project = getPublishedProject("opentelemetry-java-instrumentation", "1.9.2-alpha");
		//final PublishedProject project = getPublishedProject("grpc-java", "1.50.2");
		//final PublishedProject project = getPublishedProject("graphql-java", "19.2.0");

		if (project.getBoms().isEmpty()) {
			log("BOMs: none");
		} else {
			log("BOMs:");
			for (ArtifactCoords c : project.getBoms()) {
				log("  " + c.toCompactCoords());
			}
		}
		if (project.getLibraries().isEmpty()) {
			log("Libraries: none");
		} else {
			log("Libraries:");
			for (ArtifactCoords c : project.getLibraries()) {
				log("  " + c.toCompactCoords());
			}
		}

		if(REPORT_DEPS_TO_BUILD) {
			reportDepsToBuild(project);
		}
		
		log("done in " + (System.currentTimeMillis() - startTime));
	}
	
	private static void reportDepsToBuild(PublishedProject project) {
        DependenciesToBuildReportGenerator.builder()
        .setTopLevelArtifactsToBuild(project.getAllArtifacts())
        .setIncludeNonManaged(true)
        .setLogCodeRepos(true)
        .setLogTrees(false)
        .setValidateCodeRepoTags(false)
        .setExcludeKeys(Set.of(
        		ArtifactKey.of("io.grpc", "grpc-authz", ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_JAR),
        		ArtifactKey.of("io.grpc", "protoc-gen-grpc-java", ArtifactCoords.DEFAULT_CLASSIFIER, ArtifactCoords.TYPE_JAR)))
        .setExcludeGroupIds(Set.of("org.junit.platform", "org.junit.jupiter"))
        .setResolver(getArtifactResolver())
        .build().generate();

	}

	private static PublishedProject getPublishedProject(String projectName, String version) {
		return toPublishedProject(readPublishedArtifacts(projectName), version);
	}

	private static PublishedProject toPublishedProject(List<GradleModuleVersion> moduleVersions, String version) {
		MavenArtifactResolver resolver = null;
		if (RESOLVE_MODULE_METADATA) {
			log("Resolving metadata of published modules");
			resolver = getArtifactResolver();
		}
		final PublishedProject publishedProject = new PublishedProject();
		for (GradleModuleVersion m : moduleVersions) {
			if (resolver != null) {
				final Path moduleJson;
				try {
					moduleJson = resolver.resolve(toModuleArtifact(m)).getArtifact().getFile().toPath();
				} catch (BootstrapMavenException e) {
					log("WARN: failed to resolve " + toModuleArtifact(m));
					continue;
				}
				final GradleModuleMetadata metadata = GradleModuleMetadata.deserialize(moduleJson);
				if (metadata.isBom()) {
					publishedProject.addBom(
							ArtifactCoords.pom(metadata.getGroupId(), metadata.getArtifactId(), alignVersion(metadata.getVersion(), version)));
				} else {
					publishedProject.addLibrary(
							ArtifactCoords.jar(metadata.getGroupId(), metadata.getArtifactId(), alignVersion(metadata.getVersion(), version)));
				}
			} else {
				if (m.getName().endsWith("-bom") || m.getName().startsWith("bom-") || m.getName().contains("-bom-")) {
					publishedProject.addBom(ArtifactCoords.pom(m.getGroup(), m.getName(), alignVersion(m.getVersion(), version)));
				} else {
					publishedProject.addLibrary(ArtifactCoords.jar(m.getGroup(), m.getName(), alignVersion(m.getVersion(), version)));
				}
			}
		}
		return publishedProject;
	}

	private static String alignVersion(String v, String version) {
		return !v.equals(version) && isSnapshot(v) ? version : v;
	}

	private static MavenArtifactResolver getArtifactResolver() {
		try {
			return MavenArtifactResolver.builder().setWorkspaceDiscovery(false).build();
		} catch (BootstrapMavenException e) {
			throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
		}
	}

	private static Artifact toModuleArtifact(GradleModuleVersion module) {
		return new DefaultArtifact(module.getGroup(), module.getName(), ArtifactCoords.DEFAULT_CLASSIFIER, "module",
				module.getVersion());
	}

	private static List<GradleModuleVersion> readPublishedArtifacts(final String projectName) {
		final File projectDir = Paths.get(System.getProperty("user.home")).resolve("git").resolve(projectName).toFile();
		log("Connecting to " + projectDir);
		final ProjectConnection connection = GradleConnector.newConnector()
				.forProjectDirectory(projectDir)
				//.useGradleVersion(projectName)
				.connect();
		log("Resolving module publications");
		try {
			final PublicationReaderOutcome outcome = new PublicationReaderOutcome();
			final BuildActionExecuter<List<GradleModuleVersion>> actionExecuter = connection
					.action(new PublicationReader()).withArguments("-PskipAndroid=true").setStandardOutput(System.out);
			if (JDK8) {
				actionExecuter.setJavaHome(new File("/home/aloubyansky/jdk/jdk1.8.0_261"));
			}
			actionExecuter.run(outcome);
			return outcome.getPublications();
		} finally {
			connection.close();
		}
	}

	public static class PublicationReader implements BuildAction<List<GradleModuleVersion>> {

		@Override
		public List<GradleModuleVersion> execute(BuildController controller) {
			GradleProject project = controller.getModel(GradleProject.class);
			List<GradleModuleVersion> publications = new ArrayList<>();
			getPublications(project, controller, publications);
			return publications;
		}

		private void getPublications(GradleProject project, BuildController controller,
				List<GradleModuleVersion> publications) {
			ProjectPublications pp = controller.getModel(project, ProjectPublications.class);
			for (GradlePublication pub : pp.getPublications()) {
				publications.add(pub.getId());
			}
			for (GradleProject child : project.getChildren()) {
				getPublications(child, controller, publications);
			}
		}
	}

	public static class PublicationReaderOutcome implements ResultHandler<List<GradleModuleVersion>> {

		private CompletableFuture<List<GradleModuleVersion>> publications = new CompletableFuture<>();

		public List<GradleModuleVersion> getPublications() {
			try {
				return publications.get();
			} catch (Exception e) {
				throw new RuntimeException("Failed to obtain publications", e);
			}
		}

		@Override
		public void onComplete(List<GradleModuleVersion> result) {
			publications.complete(result);
		}

		@Override
		public void onFailure(GradleConnectionException failure) {
			failure.printStackTrace();
		}
	}

	private static boolean isSnapshot(String v) {
		return v.endsWith("-SNAPSHOT");
	}

	private static void log(Object s) {
		System.out.println(s == null ? "null" : s);
	}
}
