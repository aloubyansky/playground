package io.quarkus.bom.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class PlatformBomConfig {

	public static PlatformBomConfig forPom(Path pom) {
		if(!Files.exists(pom)) {
			throw new IllegalArgumentException("Path does not exist " + pom);
		}
		try {
		    final Model model = ModelUtils.readModel(pom);
			final DependencyManagement dm = model.getDependencyManagement();
		    if(dm == null) {
		    	throw new Exception(pom + " does not include managed dependencies");
		    }
		    final MavenArtifactResolver resolver = MavenArtifactResolver.builder().build();
			return fromManagedDeps(dm.getDependencies(),
					resolver.resolveDescriptor(new DefaultArtifact(ModelUtils.getGroupId(model), model.getArtifactId(),
							null, "pom", ModelUtils.getVersion(model))));
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize platform BOM config", e);
		}
	}

	private static PlatformBomConfig fromManagedDeps(final List<Dependency> managedDeps, ArtifactDescriptorResult bomDescr) {

		final Map<AppArtifactKey, org.eclipse.aether.graph.Dependency> resolvedDeps = new HashMap<>();
		for(org.eclipse.aether.graph.Dependency dep : bomDescr.getManagedDependencies()) {
			final Artifact artifact = dep.getArtifact();
			resolvedDeps.put(new AppArtifactKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier(), artifact.getExtension()), dep);
		}

		final PlatformBomConfig config = new PlatformBomConfig();
		for(Dependency dep : managedDeps) {
			final org.eclipse.aether.graph.Dependency resolvedDep = resolvedDeps.get(new AppArtifactKey(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType()));
			if(resolvedDep == null) {
				throw new IllegalStateException("Failed to locate direct " + dep + " in resolved descriptor");
			}
			final Artifact artifact = resolvedDep.getArtifact();
			if("import".equals(dep.getScope())) {
				log("import " + artifact);
				config.importedBoms.add(artifact);
			} else {
				log("Direct dependency " + artifact);
				config.directDeps.add(artifact);
			}
		}
		return config;
	}

	private List<Artifact> importedBoms = new ArrayList<>();
	private List<Artifact> directDeps = new ArrayList<>();

	private PlatformBomConfig() {
	}

	public static void main(String[] args) throws Exception {
		forPom(Paths.get(System.getProperty("user.home")).resolve("git").resolve("quarkus-platform").resolve("bom")
				.resolve("runtime").resolve("pom.xml"));
	}

	private static void log(Object o) {
		System.out.println(o == null ? "null" : o.toString());
	}
}
