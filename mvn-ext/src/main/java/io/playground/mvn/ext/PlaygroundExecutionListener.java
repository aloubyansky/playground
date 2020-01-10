package io.playground.mvn.ext;

import org.apache.maven.execution.AbstractExecutionListener;
import org.apache.maven.execution.ExecutionEvent;
import org.codehaus.plexus.component.annotations.Component;

@Component(role = AbstractExecutionListener.class, hint = "playground-listener")
public class PlaygroundExecutionListener extends AbstractExecutionListener {

	@Override
	public void mojoStarted(ExecutionEvent event) {
		System.out.println("MOJO STARTED " + event.getMojoExecution().getArtifactId());
	}
}
