package io.playground.registry;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("new-platform")
public class RegistryResource {
	
	@Inject
	PlatformRegistry registry;
	
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("{groupId}/{artifactId}/{version}")
    public String registryPlatform(@PathParam("groupId") String groupId, @PathParam("artifactId") String artifactId, @PathParam("version") String version) {
    	registry.registerPlatform(groupId, artifactId, version);
		return "Registered platform " + groupId + ":" + artifactId + ":" + version;
    }
}