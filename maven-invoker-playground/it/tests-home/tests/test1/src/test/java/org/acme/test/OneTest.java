package org.acme.test;

import static org.junit.jupiter.api.Assertions.fail;

import io.quarkus.bootstrap.model.AppArtifact;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.junit.jupiter.api.Test;

public class OneTest {

    @Test
    public void test() throws Exception {
        System.out.println("Hello from test");

        final BootstrapMavenContext ctx = new BootstrapMavenContext(BootstrapMavenContext.config().setWorkspaceDiscovery(true));
        final MavenArtifactResolver resolver = new MavenArtifactResolver(ctx);
        final AppArtifact artifact = ctx.getCurrentProjectArtifact("pom");
        System.out.println("Current project: " + artifact);

        for(Dependency dep : resolver.resolveDescriptor(new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getType(), artifact.getVersion())).getManagedDependencies()) {
            final Artifact a = dep.getArtifact();
            System.out.println(" - " + a);
        }
        //fail("FAILED");
    }
}