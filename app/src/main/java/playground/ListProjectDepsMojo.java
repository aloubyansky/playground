package playground;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(name="list-project-deps", defaultPhase = LifecyclePhase.NONE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ListProjectDepsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("PROJECT ARTIFACTS:");
		for(Artifact a : project.getArtifacts()) {
			getLog().info("- " + a);
		}
	}
}
