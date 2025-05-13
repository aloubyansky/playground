package io.playground.mvn.ext;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.codehaus.plexus.component.annotations.Component;

//your extension must be a "Plexus" component so mark it with the annotation
@Component(role = AbstractMavenLifecycleParticipant.class, hint = "playground-lifecycle-participant")
public class PlaygroundLifecycleParticipant extends AbstractMavenLifecycleParticipant {

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
	System.out.println("AFTER PROJECTS READ " + session.getCurrentProject().getArtifactId());
    }
}