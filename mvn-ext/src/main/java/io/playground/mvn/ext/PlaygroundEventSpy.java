package io.playground.mvn.ext;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.bootstrap.resolver.maven.workspace.WorkspaceLoader;
import io.quarkus.maven.dependency.ArtifactCoords;
import org.apache.maven.eventspy.AbstractEventSpy;
import org.apache.maven.eventspy.EventSpy;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.model.Model;
import org.apache.maven.model.Profile;
import org.codehaus.plexus.component.annotations.Component;

import java.nio.file.Files;
import java.nio.file.Path;

@Component(role = EventSpy.class, hint = "playground")
public class PlaygroundEventSpy extends AbstractEventSpy {

	@Override
	public void onEvent(Object event) throws Exception {
		if(event instanceof MavenExecutionRequest buildRequest) {
			System.out.println("EVENT " + buildRequest.getPom());

			var currentProject = new BootstrapMavenContext(BootstrapMavenContext.config()).getCurrentProject();

				final Model aggregatorModel = new Model();
				aggregatorModel.setModelVersion("4.0.0");
				aggregatorModel.setGroupId(currentProject.getGroupId());
				aggregatorModel.setArtifactId("playground-parent");
				aggregatorModel.setVersion(currentProject.getVersion());
				aggregatorModel.setPackaging(ArtifactCoords.TYPE_POM);

				final Path aggregatorPom = currentProject.getDir().resolve(".playground").resolve("playground-aggregator").resolve("pom.xml");
				aggregatorModel.addModule(aggregatorPom.getParent().relativize(currentProject.getDir()).toString());

			final Model deploymentModel = new Model();
			deploymentModel.setModelVersion("4.0.0");
			deploymentModel.setGroupId(currentProject.getGroupId());
			deploymentModel.setArtifactId(currentProject.getArtifactId() + "-deployment");
			deploymentModel.setVersion(currentProject.getVersion());
			deploymentModel.setPackaging(ArtifactCoords.TYPE_POM);

			final Path baseDir = aggregatorPom.getParent();
			final Path deploymentPom = baseDir.resolve(deploymentModel.getArtifactId()).resolve("pom.xml");
			Files.createDirectories(deploymentPom.getParent());
			ModelUtils.persistModel(deploymentPom, deploymentModel);

			aggregatorModel.addModule(baseDir.relativize(deploymentPom.getParent()).toString());
			Files.createDirectories(aggregatorPom.getParent());
				ModelUtils.persistModel(aggregatorPom, aggregatorModel);

				buildRequest.setPom(aggregatorPom.toFile());
				buildRequest.setBaseDirectory(aggregatorPom.getParent().toFile());

		}
	}
}
