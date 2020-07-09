package io.quarkus.bom.platform;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;

public class PlatformBomConfig {

	private static interface PomResolver {
		Path projectPom();
		Model readModel(Path pom) throws IOException;
	}

	private static class FsPomResolver implements PomResolver {

		private final Path projectPom;

		FsPomResolver(Path projectPom) {
			if(!Files.exists(projectPom)) {
				throw new IllegalArgumentException("Path does not exist " + projectPom);
			}
			this.projectPom = projectPom;
		}

		@Override
		public Path projectPom() {
			return projectPom;
		}

		@Override
		public Model readModel(Path pom) throws IOException {
			if(Files.isDirectory(pom)) {
				pom = pom.resolve("pom.xml");
			}
			return Files.exists(pom) ? ModelUtils.readModel(pom) : null;
		}
	}

	private static class UrlPomResolver implements PomResolver {
		private final URL baseUrl;

		UrlPomResolver(URL baseUrl) {
			this.baseUrl = baseUrl;
		}

		@Override
		public Path projectPom() {
			return Paths.get(baseUrl.getPath());
		}

		@Override
		public Model readModel(Path pom) throws IOException {
			if(!pom.getFileName().toString().endsWith(".xml")) {
				pom = pom.resolve("pom.xml");
			}
			final URL url = new URL(baseUrl.getProtocol(), baseUrl.getHost(), baseUrl.getPort(), pom.toUri().getPath());
			try(InputStream stream = url.openStream()) {
				return ModelUtils.readModel(stream);
			} catch(IOException e) {
				return null;
			}
		}
	}

	public static PlatformBomConfig forGithubPom(String repoPath) {
		final StringBuilder buf = new StringBuilder();
		buf.append("https://raw.githubusercontent.com");
		if(repoPath.charAt(0) != '/') {
			buf.append("/");
		}
		buf.append(repoPath);
		final URL url;
		try {
			url = new URL(buf.toString());
		} catch (MalformedURLException e) {
			throw new IllegalStateException("Failed to create a github URL for " + repoPath, e);
		}
		return forPom(url);
	}

	public static PlatformBomConfig forPom(URL pom) {
		return build(new UrlPomResolver(pom));
	}

	public static PlatformBomConfig forPom(Path pom) {
		if(!Files.exists(pom)) {
			throw new IllegalArgumentException("Path does not exist " + pom);
		}
		return build(new FsPomResolver(pom));
	}

	private static PlatformBomConfig build(PomResolver pomResolver) {
		Path pom = pomResolver.projectPom();
		try {
		    final Model model = pomResolver.readModel(pom);
			final DependencyManagement dm = model.getDependencyManagement();
		    if(dm == null) {
		    	throw new Exception(pom + " does not include managed dependencies");
		    }

		    final Properties allProps = new Properties();
		    allProps.putAll(model.getProperties());
		    Parent parent = model.getParent();
			while (parent != null) {
				final String relativePath = parent.getRelativePath();
				if(relativePath == null || relativePath.isEmpty()) {
					break;
				}
				Path parentPom = pom.getParent().resolve(relativePath).normalize().toAbsolutePath();
				final Model parentModel = pomResolver.readModel(parentPom);
				if(parentModel == null) {
					break;
				}
				allProps.putAll(parentModel.getProperties());
				parent = parentModel.getParent();
				pom = parentPom;
			}
			return fromManagedDeps(new DefaultArtifact(ModelUtils.getGroupId(model),
					model.getArtifactId(), null, "pom", ModelUtils.getVersion(model)),
					dm.getDependencies(), allProps);
		} catch (Exception e) {
			throw new RuntimeException("Failed to initialize platform BOM config", e);
		}
	}

	private static PlatformBomConfig fromManagedDeps(Artifact bomArtifact, final List<Dependency> managedDeps, Properties props) {
		final PlatformBomConfig config = new PlatformBomConfig();
		config.bomArtifact = Objects.requireNonNull(bomArtifact);
		for(Dependency dep : managedDeps) {
			String version = dep.getVersion();
			if(version.startsWith("${") && version.endsWith("}")) {
				String prop = version.substring(2, version.length() - 1);
				String value = props.getProperty(prop);
				if(value != null) {
					version = value;
				}
			}
			final Artifact artifact = new DefaultArtifact(dep.getGroupId(), dep.getArtifactId(), dep.getClassifier(), dep.getType(), version);
			if(config.quarkusBom == null && artifact.getArtifactId().equals("quarkus-bom") && artifact.getGroupId().equals("io.quarkus")) {
				config.quarkusBom = artifact;
			} else {
			    config.directDeps.add(artifact);
			}
		}
		if(config.quarkusBom == null) {
			throw new RuntimeException("Failed to locate io.quarkus:quarkus-bom among the dependencies");
		}
		return config;
	}

	private Artifact bomArtifact;
	private Artifact quarkusBom;
	private List<Artifact> directDeps = new ArrayList<>();

	private PlatformBomConfig() {
	}

	public Artifact bomArtifact() {
		return bomArtifact;
	}

	public Artifact quarkusBom() {
		return quarkusBom;
	}

	public List<Artifact> directDeps() {
		return directDeps;
	}
}
