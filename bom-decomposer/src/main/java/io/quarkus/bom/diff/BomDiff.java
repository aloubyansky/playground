package io.quarkus.bom.diff;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import io.quarkus.bootstrap.model.AppArtifactKey;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenContext;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.resolver.maven.workspace.LocalProject;

public class BomDiff {

	public static Config config() {
		return new Config();
	}

	public static class Config {

		private Artifact mainBom;
		private String mainSource;
		private Artifact toBom;
		private String toSource;
		private List<Dependency> mainDeps;
		private List<Dependency> toDeps;

		private MavenArtifactResolver resolver;

		private Config() {
		}

		public Config resolver(MavenArtifactResolver resolver) {
			this.resolver = resolver;
			return this;
		}

		public Config compare(Artifact bomArtifact) {
			final ArtifactDescriptorResult descr = descriptor(defaultResolver(), bomArtifact);
			mainBom = descr.getArtifact();
			mainSource = bomArtifact.toString();
			mainDeps = descr.getManagedDependencies();
			return this;
		}

		public Config compare(Path bomPath) {
			final ArtifactDescriptorResult descr = descriptor(bomPath);
			mainBom = descr.getArtifact();
			mainSource = bomPath.toString();
			mainDeps = descr.getManagedDependencies();
			return this;
		}

		public BomDiff to(String groupId, String artifactId, String version) {
			return to(new DefaultArtifact(groupId, artifactId, null, "pom", version));
		}

		public BomDiff to(Artifact bomArtifact) {
			final ArtifactDescriptorResult descr = descriptor(defaultResolver(), bomArtifact);
			toBom = descr.getArtifact();
			toSource = bomArtifact.toString();
			toDeps = descr.getManagedDependencies();
			return diff();
		}

		public BomDiff to(Path bomPath) {
			final ArtifactDescriptorResult descr = descriptor(bomPath);
			toBom = descr.getArtifact();
			toSource = bomPath.toString();
			toDeps = descr.getManagedDependencies();
			return diff();
		}

		private ArtifactDescriptorResult descriptor(Path pom) {
			BootstrapMavenContext mvnCtx;
			try {
				mvnCtx = new BootstrapMavenContext(BootstrapMavenContext.config().setCurrentProject(pom.toString()));
			} catch (BootstrapMavenException e) {
				throw new RuntimeException("Failed to initialize bootstrap Maven context", e);
			}
			final LocalProject bomProject = mvnCtx.getCurrentProject();
			final MavenArtifactResolver resolver;
			try {
				resolver = new MavenArtifactResolver(mvnCtx);
			} catch (BootstrapMavenException e) {
				throw new RuntimeException("Failed to initialize Maven artifact resolver for " + pom, e);
			}
			return descriptor(resolver, new DefaultArtifact(bomProject.getGroupId(), bomProject.getArtifactId(), null, "pom", bomProject.getVersion()));
		}

		private ArtifactDescriptorResult descriptor(MavenArtifactResolver resolver, Artifact artifact) {
			try {
				return resolver.resolveDescriptor(artifact);
			} catch (BootstrapMavenException e) {
				throw new RuntimeException("Failed to resolve " + artifact + " descriptor", e);
			}
		}

		private MavenArtifactResolver defaultResolver() {
			if (resolver == null) {
				try {
					resolver = MavenArtifactResolver.builder().build();
				} catch (BootstrapMavenException e) {
					throw new RuntimeException("Failed to initialize Maven artifact resolver", e);
				}
			}
			return resolver;
		}

		private BomDiff diff() {
			return new BomDiff(this);
		}
	}

	public static class VersionChange {
		private final Dependency from;
		private final Dependency to;

		private VersionChange(Dependency from, Dependency to) {
			this.from = from;
			this.to = to;
		}

		public Dependency from() {
			return from;
		}

		public Dependency to() {
			return to;
		}
	}

	private final Artifact mainBom;
	private final String mainSource;
	private final Artifact toBom;
	private final String toSource;
	private final int mainSize;
	private final int toSize;
	private final List<Dependency> missing;
	private final List<Dependency> extra;
	private final List<Dependency> matching;
	private final List<VersionChange> upgraded;
	private final List<VersionChange> downgraded;

	private BomDiff(Config config) {
		mainBom = config.mainBom;
		mainSource = config.mainSource;
		toBom = config.toBom;
		toSource = config.toSource;
		mainSize = config.mainDeps.size();
		toSize = config.toDeps.size();
		final Map<AppArtifactKey, Dependency> mainDeps = toMap(config.mainDeps);
		final Map<AppArtifactKey, Dependency> toDeps = toMap(config.toDeps);

		final Map<String, Dependency> missing = new HashMap<>();
		final Map<String, Dependency> extra = new HashMap<>();
		final Map<String, Dependency> matching = new HashMap<>();
		final Map<String, VersionChange> upgraded = new HashMap<>();
		final Map<String, VersionChange> downgraded = new HashMap<>();

		for(Map.Entry<AppArtifactKey, Dependency> main : mainDeps.entrySet()) {
			final Dependency toDep = toDeps.remove(main.getKey());
			if(toDep == null) {
				extra.put(main.getKey().toString(), main.getValue());
			} else if(main.getValue().getArtifact().getVersion().equals(toDep.getArtifact().getVersion())) {
				matching.put(main.getKey().toString(), main.getValue());
			} else if(new DefaultArtifactVersion(main.getValue().getArtifact().getVersion()).compareTo(new DefaultArtifactVersion(toDep.getArtifact().getVersion())) > 0) {
				upgraded.put(main.getKey().toString(), new VersionChange(toDep, main.getValue()));
			} else {
				downgraded.put(main.getKey().toString(), new VersionChange(toDep, main.getValue()));
			}
		}
		toDeps.entrySet().forEach(d -> missing.put(d.getKey().toString(), d.getValue()));

		this.missing = ordered(missing);
		this.extra = ordered(extra);
		this.matching = ordered(matching);
		this.upgraded = ordered(upgraded);
		this.downgraded = ordered(downgraded);
	}

	private <T> List<T> ordered(Map<String, T> map) {
		if(map.isEmpty()) {
			return Collections.emptyList();
		}
		final List<String> keys = new ArrayList<>(map.keySet());
		Collections.sort(keys);
		final List<T> list = new ArrayList<>(map.size());
		keys.forEach(k -> list.add(map.get(k)));
		return list;
	}

	public Artifact mainBom() {
		return mainBom;
	}

	public String mainSource() {
		return mainSource;
	}

	public Artifact toBom() {
		return toBom;
	}

	public String toSource() {
		return toSource;
	}

	public int mainBomSize() {
		return mainSize;
	}

	public int toBomSize() {
		return toSize;
	}

	public boolean hasMissing() {
		return !missing.isEmpty();
	}

	public List<Dependency> missing() {
		return missing;
	}

	public boolean hasExtra() {
		return !extra.isEmpty();
	}

	public List<Dependency> extra() {
		return extra;
	}

	public boolean hasMatching() {
		return !matching.isEmpty();
	}

	public List<Dependency> matching() {
		return matching;
	}

	public boolean hasUpgraded() {
		return !upgraded.isEmpty();
	}

	public List<VersionChange> upgraded() {
		return upgraded;
	}

	public boolean hasDowngraded() {
		return !downgraded.isEmpty();
	}

	public List<VersionChange> downgraded() {
		return downgraded;
	}

	private static Map<AppArtifactKey, Dependency> toMap(List<Dependency> deps) {
		final Map<AppArtifactKey, Dependency> map = new HashMap<>(deps.size());
		deps.forEach(d -> map.put(key(d), d));
		return map;
	}

	private static AppArtifactKey key(Dependency dep) {
		return new AppArtifactKey(dep.getArtifact().getGroupId(), dep.getArtifact().getArtifactId(), dep.getArtifact().getClassifier(), dep.getArtifact().getExtension());
	}
}
