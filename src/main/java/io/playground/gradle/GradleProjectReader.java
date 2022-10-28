package io.playground.gradle;

import java.nio.file.Path;

import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.eclipse.EclipseExternalDependency;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.gradle.ProjectPublications;

public class GradleProjectReader {

	public static void main(String[] args) throws Exception {

		final long startTime = System.currentTimeMillis();
		final ProjectConnection connection = GradleConnector.newConnector()
				.forProjectDirectory(Path.of(System.getProperty("user.home")).resolve("git").resolve("kafka").toFile())
				.connect();

		try {
			EclipseProject project = connection.getModel(EclipseProject.class);
			log("Connected to " + project.getName());
			logSubprojects(project);
			
			final ProjectPublications publications = connection.getModel(ProjectPublications.class);
			logPublications(publications);
		} finally {
			connection.close();
		}

		log("done in " + (System.currentTimeMillis() - startTime));
	}

	private static void logPublications(ProjectPublications publications) {
		log("Publications: " + publications.getPublications().size());
		publications.getPublications().forEach(p -> {
			final GradleModuleVersion id = p.getId();
			log("- " + id.getGroup() + ":" + id.getName() + ":" + id.getVersion());
		});
	}

	private static void logSubprojects(EclipseProject project) {
		logSubprojects(project, 0);
	}

	private static void logSubprojects(EclipseProject project, int depth) {
		final StringBuilder sb = new StringBuilder();
		for (int i = 0; i < depth; ++i) {
			sb.append("  ");
		}
		String offset = sb.toString();
		log(sb.append(project.getName()));
		log(offset + "  classpath: ");
		offset += "  - ";
		for (EclipseExternalDependency d : project.getClasspath()) {
			final GradleModuleVersion module = d.getGradleModuleVersion();
			if (module != null) {
				log(offset + module.getGroup() + ":" + module.getName() + ":" + module.getVersion());
			} else {
				log(offset + module);
			}
		}
		project.getChildren().forEach(p -> logSubprojects(p, depth + 1));
	}

	private static void log(Object s) {
		System.out.println(s == null ? "null" : s);
	}
}
