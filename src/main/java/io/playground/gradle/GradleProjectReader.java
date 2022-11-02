package io.playground.gradle;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildController;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradlePublication;
import org.gradle.tooling.model.gradle.ProjectPublications;

public class GradleProjectReader {

	public static class PublicationReader implements BuildAction<List<GradleModuleVersion>> {

		@Override
		public List<GradleModuleVersion> execute(BuildController controller) {
			GradleProject project = controller.getModel(GradleProject.class);
			List<GradleModuleVersion> publications = new ArrayList<>();
			getPublications(project, controller, publications);
			return publications;
		}
		
		private void getPublications(GradleProject project, BuildController controller, List<GradleModuleVersion> publications) {
			ProjectPublications pp = controller.getModel(project, ProjectPublications.class);
			for (GradlePublication pub : pp.getPublications()) {
				publications.add(pub.getId());
			}
			for(GradleProject child : project.getChildren()) {
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
	
	public static void main(String[] args) throws Exception {

		final long startTime = System.currentTimeMillis();
		//final String projectName = "kafka";
		//final String projectName = "micrometer";
		//final String projectName = "graphql-java";
		//final String projectName = "grpc-java";
		//final String projectName = "opentelemetry-java";
		final String projectName = "opentelemetry-java-instrumentation";
		final ProjectConnection connection = GradleConnector.newConnector()
				.forProjectDirectory(Paths.get(System.getProperty("user.home")).resolve("git").resolve(projectName).toFile())
				.connect();

		try {
			final PublicationReaderOutcome outcome = new PublicationReaderOutcome();
			connection.action(new PublicationReader())
			.withArguments("-PskipAndroid=true")
			.setStandardOutput(System.out)
			.run(outcome);
			
			List<GradleModuleVersion> publications = outcome.getPublications();
			log("PROJECT PUBLICATIONS:");
			publications.forEach(p -> log(p.getGroup() + ":" + p.getName() + ":" + p.getVersion()));
		} finally {
			connection.close();
		}

		log("done in " + (System.currentTimeMillis() - startTime));
	}

	private static void log(Object s) {
		System.out.println(s == null ? "null" : s);
	}
}
