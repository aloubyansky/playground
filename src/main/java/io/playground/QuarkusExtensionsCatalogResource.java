package io.playground;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import io.quarkus.bootstrap.model.AppArtifactCoords;
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.descriptor.resolver.json.DefaultQuarkusExtensionsJsonCatalogRequestHandler;
import io.quarkus.platform.descriptor.resolver.json.request.QuarkusExtensionsJsonCatalogRequest;

@Path("/quarkus-extensions")
@Produces(MediaType.APPLICATION_JSON)
public class QuarkusExtensionsCatalogResource {

	@Inject
	BootstrapAppModelResolver artifactResolver;
	
	private DefaultQuarkusExtensionsJsonCatalogRequestHandler handler = new DefaultQuarkusExtensionsJsonCatalogRequestHandler();
	
    @GET
    @Path("{platformGroupId}/{platformArtifactId}/{platformVersion}")
    public QuarkusPlatformDescriptor extensionsCatalog(@PathParam("platformGroupId") String platformGroupId,
    		@PathParam("platformArtifactId") String platformArtifactId,
    		@PathParam("platformVersion") String platformVersion) throws Exception {
		return handler.resolve(QuarkusExtensionsJsonCatalogRequest.builder()
				.artifactResolver(artifactResolver)
				.platform(new AppArtifactCoords(platformGroupId, platformArtifactId, platformVersion))
				.build());
    }
}