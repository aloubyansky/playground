package io.playground.mvn.ext;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name="playground", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class PlaygroundMavenMojo extends AbstractMojo {

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		System.out.println("PLAYGROUND MOJO");
	}
}
