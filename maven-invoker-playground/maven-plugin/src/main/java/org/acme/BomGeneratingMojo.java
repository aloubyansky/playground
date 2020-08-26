package org.acme;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

@Mojo(name="generate-pom", defaultPhase = LifecyclePhase.INITIALIZE)
public class BomGeneratingMojo extends AbstractMojo {

	@Component
	MavenProject project;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		getLog().info("Generate POM");
		
		final Model model = project.getModel();
		
		final Dependency dep = new Dependency();
		dep.setGroupId("io.quarkus");
		dep.setArtifactId("quarkus-bootstrap-maven-resolver");
		dep.setVersion("1.7.0.Final");
		DependencyManagement dm = model.getDependencyManagement();
		if(dm == null) {
			dm = new DependencyManagement();
			model.setDependencyManagement(dm);
		}
		dm.addDependency(dep);

		final Path generatedPom = Paths.get(project.getBuild().getDirectory()).resolve("generated-pom.xml");
		try {
			persistModel(generatedPom, model);
		} catch (IOException e) {
			throw new MojoExecutionException("Failed to persist " + generatedPom, e);
		}
		project.setPomFile(generatedPom.toFile());
		getLog().info("Generated POM at " + generatedPom);
	}

    public static Model readModel(InputStream stream) throws IOException {
        try (InputStream is = stream) {
            return new MavenXpp3Reader().read(stream);
        } catch (XmlPullParserException e) {
            throw new IOException("Failed to parse POM", e);
        }
    }

    public static void persistModel(Path pomFile, Model model) throws IOException {
    	if(!Files.exists(pomFile.getParent())) {
    		Files.createDirectories(pomFile.getParent());
    	}
        final MavenXpp3Writer xpp3Writer = new MavenXpp3Writer();
        try (BufferedWriter pomFileWriter = Files.newBufferedWriter(pomFile)) {
            xpp3Writer.write(pomFileWriter, model);
        }
    }
}
