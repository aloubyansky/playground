package io.playground.mvn.ext;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;

//your extension must be a "Plexus" component so mark it with the annotation
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "playground.participant")
public class BeerMavenLifecycleParticipant extends AbstractMavenLifecycleParticipant {

	@Override
	public void afterSessionStart(MavenSession session) throws MavenExecutionException {
		System.out.println("AFTER SESSION START");
	}

	@Override
	public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
		System.out.println("AFTER PROJECT READ " + session.getCurrentProject().getArtifactId());
	}
}