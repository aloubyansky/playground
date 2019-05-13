import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;

public class Main {
	
	public static void main(String[] args) throws Exception {
		
		final MavenArtifactResolver mvn = MavenArtifactResolver.builder()
				.setRepoHome(locate("repo"))
				.build();
		final ArtifactDescriptorResult descr = mvn.resolveDescriptor(new DefaultArtifact("io.playground", "app", "", "pom", "1.0"));
		final List<Dependency> manDeps = descr.getManagedDependencies();
		final List<String> list = new ArrayList<>(manDeps.size());
		for(Dependency dep : manDeps) {
			list.add(dep.getArtifact().toString());
		}
		Collections.sort(list);
		for(String s : list) {
			log(s);
		}
	}
	
	private static Path locate(String relativePath) {
		final URL resource = Thread.currentThread().getContextClassLoader().getResource(relativePath);
		if(resource == null) {
			throw new IllegalStateException("Failed to locate resource " + relativePath);
		}
		try {
			return Paths.get(resource.toURI());
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Failed to resolve local path for " + resource);
		}
	}
	
	protected static void log(Object msg) {
		System.out.println(msg);
	}
}