package org.acme;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.assertj.core.api.Assertions;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.util.artifact.JavaScopes;

@Mojo(name="resolve-project-dependencies", defaultPhase = LifecyclePhase.PACKAGE, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class ResolveProjectDependenciesMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true)
	MavenProject project;
	
    @Component
    RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repos;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		final Set<String> projectArtifacts = project.getArtifacts().stream().map(a -> toGactv(a)).collect(Collectors.toSet());
		
		final Set<String> collectedDeps = new HashSet<>(projectArtifacts.size());
		var projectArtifact = new DefaultArtifact(project.getArtifact().getGroupId(), project.getArtifact().getArtifactId(), project.getArtifact().getClassifier(), project.getArtifact().getType(), project.getArtifact().getVersion());
		try {
			var root = repoSystem.collectDependencies(repoSession, new CollectRequest().setRoot(new Dependency(projectArtifact, JavaScopes.RUNTIME)).setRepositories(repos)).getRoot();
			collectCoords(root.getChildren(), collectedDeps);
		} catch (DependencyCollectionException e) {
			throw new MojoExecutionException("Failed to collect dependencies for ");
		}
		
		Assertions.assertThat(collectedDeps).isEqualTo(projectArtifacts);
	}

	private static void collectCoords(Collection<DependencyNode> nodes, Set<String> collectedCoords) {
		nodes.forEach(n -> {
			collectedCoords.add(toGactv(n.getArtifact()));
			collectCoords(n.getChildren(), collectedCoords);
		});
	}
	
	private static String toGactv(Artifact a) {
		return a.getGroupId() + ":" + a.getArtifactId() + ":" + ensureEmptyClassifier(a.getClassifier()) + ":" + a.getType() + ":" + a.getVersion();
	}

	private static String toGactv(org.eclipse.aether.artifact.Artifact a) {
		return a.getGroupId() + ":" + a.getArtifactId() + ":" + ensureEmptyClassifier(a.getClassifier()) + ":" + a.getExtension() + ":" + a.getVersion();
	}

	private static String ensureEmptyClassifier(String classifier) {
		return classifier == null ? "" : classifier;
	}
}
