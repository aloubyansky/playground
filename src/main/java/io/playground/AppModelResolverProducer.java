package io.playground;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;

import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

@ApplicationScoped
public class AppModelResolverProducer {

	@Produces
	public BootstrapAppModelResolver resolver() {
		try {
			return new BootstrapAppModelResolver(MavenArtifactResolver.builder().build());
		} catch (BootstrapMavenException e) {
			throw new IllegalStateException("Failed to initialize maven artifact resolver", e);
		}
	}
}
