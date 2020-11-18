package io.playground.registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.maven.ArtifactKey;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.PlatformCatalog;
import io.quarkus.registry.catalog.PlatformRelease;
import io.quarkus.registry.catalog.PlatformStream;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonCatalogMerger;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonPlatform;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.catalog.json.JsonPlatformRelease;
import io.quarkus.registry.catalog.json.JsonPlatformReleaseVersion;
import io.quarkus.registry.catalog.json.JsonPlatformStream;
import io.quarkus.registry.util.PlatformArtifacts;

@Singleton
public class PlatformRegistry {

	private static final PlatformStackInfo QUARKUS_1_PLATFORM;
	static {
		QUARKUS_1_PLATFORM = new PlatformStackInfo("io.quarkus", "1.13", "7", null, Arrays.asList(PlatformArtifacts
				.getCatalogArtifactForBom(ArtifactCoords.fromString("io.quarkus:quarkus-universe-bom:1.13.7.Final"))));
	}
	private static final boolean registerQuarkus1Platform = false;

	@Inject
	MavenArtifactResolver resolver;

	private Map<String, Map<String, ExtensionCatalog>> streamPlatformDescriptors = new HashMap<>();
	private Map<String, List<PlatformStackInfo>> stackInfoMap = new TreeMap<>();
	private Map<String, ExtensionCatalog> extensionCatalogs = new HashMap<>();
	private Set<String> recognizedQuarkusVersions = new HashSet<>();

	private JsonPlatformCatalog globalPlatformCatalog;
	private Map<String, JsonPlatformCatalog> platformCatalogs = new HashMap<>();

	{
		globalPlatformCatalog = new JsonPlatformCatalog();
		final Map<String, JsonPlatform> platforms = new HashMap<>();
		addQuarkus1Platforms(platforms);
	}

	private void addQuarkus1Platforms(final Map<String, JsonPlatform> platforms) {
		if(registerQuarkus1Platform) {
		    addPlatform(platforms, QUARKUS_1_PLATFORM);
		}
	}

	@SuppressWarnings("unchecked")
	public ExtensionCatalog registerPlatform(String groupId, String artifactId, String version) {
		final Artifact artifact = new DefaultArtifact(groupId, artifactId, version, "json", version);
		final ExtensionCatalog newPlatformDescr = resolveCatalog(artifact, false);

		final Map<Object, Object> newPlatformStack = (Map<Object, Object>) newPlatformDescr.getMetadata()
				.get("platform-release");
		if (newPlatformStack == null) {
			System.out.println("NO STACK INFO " + groupId + ":" + artifactId + ":" + version);
			return null;
		}
		final PlatformStackInfo newStackInfo = new PlatformStackInfo((String) newPlatformStack.get("platform-key"),
				(String) newPlatformStack.get("stream"), (String) newPlatformStack.get("version"), newPlatformDescr,
				((List<String>) newPlatformStack.get("members")).stream().map(ArtifactCoords::fromString)
						.collect(Collectors.toList()));

		final List<PlatformStackInfo> stackInfoList = stackInfoMap.computeIfAbsent(newStackInfo.stream,
				s -> new ArrayList<>());
		final Iterator<PlatformStackInfo> i = stackInfoList.iterator();
		while (i.hasNext()) {
			final PlatformStackInfo si = i.next();
			if (!si.origin.getBom().getKey().equals(newPlatformDescr.getBom().getKey())) {
				continue;
			}
			if (newStackInfo.containsMembers(si.members)) {
				i.remove();
				System.out.println("Deregistered " + si.origin.getId());
			}
		}
		stackInfoList.add(newStackInfo);
		System.out.println("Registered " + newStackInfo.origin.getId());

		final Map<String, ExtensionCatalog> platformDescriptors = streamPlatformDescriptors
				.computeIfAbsent(newStackInfo.stream, s -> new TreeMap<>()); // just to make a more consistent ordering
																				// on the web
		platformDescriptors.clear();
		final Set<String> processedUnions = new HashSet<>(4);
		for (PlatformStackInfo stackInfo : stackInfoList) {
			final ExtensionCatalog c = stackInfo.origin;
			platformDescriptors.put(stackInfo.coords().getKey().toString(), c);

			if (!processedUnions.add(stackInfo.stackVersion)) {
				continue;
			}
		}

		for (PlatformStackInfo stack : stackInfoList) {
			for (ArtifactCoords memberCoords : stack.members) {
				if (stack.coords().equals(memberCoords)) {
					continue;
				}
				ExtensionCatalog memberCatalog = platformDescriptors.get(memberCoords.getKey().toString());
				if (memberCatalog == null) {
					memberCatalog = resolveCatalog(
							new DefaultArtifact(memberCoords.getGroupId(), memberCoords.getArtifactId(),
									memberCoords.getClassifier(), memberCoords.getType(), memberCoords.getVersion()),
							true);
					if (memberCatalog != null) {
						platformDescriptors.put(memberCoords.getKey().toString(), memberCatalog);
					}
				}
			}
		}

		extensionCatalogs.put(newStackInfo.stream,
				JsonCatalogMerger.merge(new ArrayList<>(platformDescriptors.values())));

		globalPlatformCatalog = new JsonPlatformCatalog();
		platformCatalogs.clear();
		recognizedQuarkusVersions.clear();

		final Map<String, JsonPlatform> platforms = new HashMap<>();
		for (List<PlatformStackInfo> stream : stackInfoMap.values()) {
			for (PlatformStackInfo stackInfo : stream) {
				final ExtensionCatalog c = stackInfo.origin;
				platformDescriptors.put(stackInfo.coords().getKey().toString(), c);
				addPlatform(platforms, stackInfo);
			}
		}

		addQuarkus1Platforms(platforms);
		return newPlatformDescr;
	}

	private void addPlatform(final Map<String, JsonPlatform> platforms, PlatformStackInfo stackInfo) {
		final JsonPlatform p = platforms.computeIfAbsent(stackInfo.platformKey, k -> {
			final JsonPlatform pl = new JsonPlatform();
			pl.setPlatformKey(stackInfo.platformKey);
			globalPlatformCatalog.addPlatform(pl);
			return pl;
		});

		addPlatformRelease(p, stackInfo);
		recognizedQuarkusVersions.add(stackInfo.origin.getQuarkusCoreVersion());
		final JsonPlatformCatalog versionCatalog = platformCatalogs.computeIfAbsent(stackInfo.origin.getQuarkusCoreVersion(), v -> new JsonPlatformCatalog());
		JsonPlatform versionPlatform = (JsonPlatform) versionCatalog.getPlatform(p.getPlatformKey());
		if(versionPlatform == null) {
			versionPlatform = new JsonPlatform();
			versionPlatform.setPlatformKey(p.getPlatformKey());
			versionCatalog.addPlatform(versionPlatform);
		}
		addPlatformRelease(versionPlatform, stackInfo);
	}

	private void addPlatformRelease(final JsonPlatform p, PlatformStackInfo stackInfo) {
		JsonPlatformStream s = null;
		if (p.getStreams().isEmpty()) {
			p.setStreams(new ArrayList<>());
		}
		for (PlatformStream st : p.getStreams()) {
			if (st.getId().equals(stackInfo.stream)) {
				s = (JsonPlatformStream) st;
				break;
			}
		}
		if (s == null) {
			s = new JsonPlatformStream();
			s.setId(stackInfo.stream);
			p.addStream(s);
		}

		JsonPlatformRelease r = null;
		if (s.getReleases().isEmpty()) {
			s.setReleases(new ArrayList<>());
		}
		for (PlatformRelease rel : s.getReleases()) {
			if (rel.getVersion().equals(JsonPlatformReleaseVersion.fromString(stackInfo.stackVersion))) {
				r = (JsonPlatformRelease) rel;
				break;
			}
		}
		if (r == null) {
			r = new JsonPlatformRelease();
			r.setVersion(JsonPlatformReleaseVersion.fromString(stackInfo.stackVersion));
			r.setQuarkusCoreVersion(stackInfo.origin.getQuarkusCoreVersion());
			r.setUpstreamQuarkusCoreVersion(stackInfo.origin.getUpstreamQuarkusCoreVersion());
			r.setMemberBoms(stackInfo.members);
			s.addRelease(r);
		}
	}

	public PlatformCatalog platformCatalog(String quarkusVersion) {
		if (quarkusVersion == null) {
			return globalPlatformCatalog;
		}
		return platformCatalogs.get(quarkusVersion);
	}

	public Collection<String> recognizedQuarkusVersions() {
		return recognizedQuarkusVersions;
	}

	private ExtensionCatalog resolveCatalog(final Artifact artifact, boolean ignoreError) {
		final java.nio.file.Path jsonFile;
		try {
			jsonFile = resolver.resolve(artifact).getArtifact().getFile().toPath();
		} catch (BootstrapMavenException e) {
			if (!ignoreError) {
				throw new RuntimeException("Failed to resolver " + artifact, e);
			}
			return null;
		}
		final ExtensionCatalog platformDescr;
		try {
			platformDescr = JsonCatalogMapperHelper.deserialize(jsonFile, JsonExtensionCatalog.class);
		} catch (IOException e) {
			if (!ignoreError) {
				throw new RuntimeException("Failed to deserialize platform descriptor " + jsonFile, e);
			}
			return null;
		}
		return platformDescr;
	}

	public Collection<String> streams() {
		return stackInfoMap.keySet();
	}

	public ExtensionCatalog catalog(String stream) {
		return extensionCatalogs.get(stream);
	}

	public List<ArtifactCoords> getBoms(String stream, Collection<String> elementKeys) {
		elementKeys.add("io.quarkus:quarkus-core"); // this is necessary to pick up the latest quarkus core present in
													// the union
		// return
		// ElementCatalogBuilder.getMembersForElements(elementCatalogs.get(stream),
		// elementKeys).stream().map(Member::getInstance).collect(Collections.singletonList(null));
		return Collections.emptyList();
	}

	private static class PlatformStackInfo {
		final String platformKey;
		final String stream;
		final String stackVersion;
		final ExtensionCatalog origin;
		final List<ArtifactCoords> members;
		private ArtifactCoords coords;
		private Set<ArtifactKey> memberGAs;

		PlatformStackInfo(String platformKey, String stream, String stackVersion, ExtensionCatalog origin,
				List<ArtifactCoords> members) {
			this.platformKey = platformKey;
			this.stream = stream;
			this.stackVersion = stackVersion;
			this.origin = origin;
			this.members = members;
		}

		ArtifactCoords coords() {
			return coords == null ? coords = ArtifactCoords.fromString(origin.getId()) : coords;
		}

		private Set<ArtifactKey> memberGAs() {
			if (memberGAs == null) {
				memberGAs = new HashSet<>(members.size());
				for (ArtifactCoords c : members) {
					memberGAs.add(new ArtifactKey(c.getGroupId(), c.getArtifactId()));
				}
			}
			return memberGAs;
		}

		boolean containsMembers(List<ArtifactCoords> otherMembers) {
			final Set<ArtifactKey> memberGAs = memberGAs();
			for (ArtifactCoords c : otherMembers) {
				if (!memberGAs.contains(new ArtifactKey(c.getGroupId(), c.getArtifactId()))) {
					return false;
				}
			}
			return true;
		}
	}
}
