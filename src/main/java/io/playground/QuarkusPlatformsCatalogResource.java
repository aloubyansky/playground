package io.playground;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.platform.descriptor.resolver.json.DefaultQuarkusPlatformsCatalogRequestHandler;
import io.quarkus.platform.descriptor.resolver.json.request.QuarkusPlatformsCatalog;
import io.quarkus.platform.descriptor.resolver.json.request.QuarkusPlatformsJsonCatalogRequest;

@Path("/quarkus-platforms")
@Produces(MediaType.APPLICATION_JSON)
public class QuarkusPlatformsCatalogResource {

	@Inject
	BootstrapAppModelResolver artifactResolver;

	final DefaultQuarkusPlatformsCatalogRequestHandler handler = new DefaultQuarkusPlatformsCatalogRequestHandler();

    @GET
    public QuarkusPlatformsCatalog platforms() throws Exception {
    	return handler.resolve(QuarkusPlatformsJsonCatalogRequest.builder()
				.artifactResolver(artifactResolver)
				.build());
    }
    
    @GET
    @Path("{quarkusVersion}")
    public QuarkusPlatformsCatalog platformsForQuarkusCore(@PathParam("quarkusVersion") String quarkusVersion) throws Exception {
    	return handler.resolve(QuarkusPlatformsJsonCatalogRequest.builder()
    			.artifactResolver(artifactResolver)
				.quarkusCoreVersion(quarkusVersion)
				.build());
    }
}