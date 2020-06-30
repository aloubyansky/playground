package org.acme.quarkus.sample;

import java.io.IOException;
import java.nio.file.Files;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

@Path("/hello")
public class HelloResource {
	
	@Inject
	MavenArtifactResolver resolver;
	
	@Inject
	BootstrapMavenContext mvnCtx;
	
    @GET
    @Path("/json")
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
    	
		Artifact artifact = new DefaultArtifact("io.quarkus", "quarkus-bom-descriptor-json", null, "json", "1.6.0.CR1");
    	try {
			artifact = resolver.resolve(artifact).getArtifact();
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to resolve " + artifact, e);
		}
    	
    	try {
			return Files.readString(artifact.getFile().toPath());
		} catch (IOException e) {
			throw new IllegalStateException("Failed to read " + artifact.getFile(), e);
		}
    }

    @GET
    @Path("/repo")
    @Produces(MediaType.TEXT_PLAIN)
    public String repo() {
    	try {
			return mvnCtx.getLocalRepo();
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to obtain the local repo path", e);
		}
    }
    
    @GET
    @Path("/user-settings")
    @Produces(MediaType.TEXT_PLAIN)
    public String userSettings() {
    	return mvnCtx.getUserSettings().toString();
    }
}