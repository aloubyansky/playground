package io.playground.registry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
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
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonCatalogMerger;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonPlatform;
import io.quarkus.registry.catalog.json.JsonPlatformCatalog;
import io.quarkus.registry.union.ElementCatalog;
import io.quarkus.registry.union.ElementCatalogBuilder;
import io.quarkus.registry.union.ElementCatalogBuilder.MemberBuilder;
import io.quarkus.registry.union.ElementCatalogBuilder.UnionBuilder;

@Singleton
public class PlatformRegistry {

	@Inject
	MavenArtifactResolver resolver;

	private Map<String, Map<String, ExtensionCatalog>> streamPlatformDescriptors = new HashMap<>();
	private Map<String, List<PlatformStackInfo>> stackInfoMap = new TreeMap<>();
	private Map<String, ElementCatalog> elementCatalogs = new HashMap<>();
	private JsonPlatformCatalog platformCatalog;
	private Map<String, ExtensionCatalog> extensionCatalogs = new HashMap<>();

	@SuppressWarnings("unchecked")
	public ExtensionCatalog registerPlatform(String groupId, String artifactId, String version) {
		final Artifact artifact = new DefaultArtifact(groupId, artifactId, version, "json", version);
		final ExtensionCatalog newPlatformDescr = resolveCatalog(artifact, false);

		final Map<Object, Object> newPlatformStack = (Map<Object, Object>) newPlatformDescr.getMetadata()
				.get("platform-stack");
		final PlatformStackInfo newStackInfo = new PlatformStackInfo((String) newPlatformStack.get("stream"), (String) newPlatformStack.get("version"),
				newPlatformDescr, ((List<String>) newPlatformStack.get("members")).stream()
						.map(ArtifactCoords::fromString).collect(Collectors.toList()));

		final List<PlatformStackInfo> stackInfoList = stackInfoMap.computeIfAbsent(newStackInfo.stream, s -> new ArrayList<>());
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

		final Map<String, ExtensionCatalog> platformDescriptors = streamPlatformDescriptors.computeIfAbsent(newStackInfo.stream, s -> new TreeMap<>()); // just to make a more consistent ordering on the web
		platformDescriptors.clear();
		platformCatalog = new JsonPlatformCatalog();
		final Set<String> processedUnions = new HashSet<>(4);
		final ElementCatalogBuilder catalogBuilder = ElementCatalogBuilder.newInstance();
		for (PlatformStackInfo stackInfo : stackInfoList) {
			final ExtensionCatalog c = stackInfo.origin;
			platformDescriptors.put(stackInfo.coords().getKey().toString(), c);

			final JsonPlatform p = new JsonPlatform();
			p.setBom(c.getBom());
			p.setQuarkusCoreVersion(c.getQuarkusCoreVersion());
			platformCatalog.addPlatform(p);
			if (c.getBom().getArtifactId().equals("quarkus-bom")) {
				platformCatalog.setDefaultPlatform(c.getBom());
			}

			if (!processedUnions.add(stackInfo.stackVersion)) {
				continue;
			}
			stackInfo.unionBuilder = catalogBuilder.newUnion(Integer.parseInt(stackInfo.stackVersion));

			addMember(stackInfo.unionBuilder, c);
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
					if(memberCatalog != null) {
					    platformDescriptors.put(memberCoords.getKey().toString(), memberCatalog);
					}
				}
				if (memberCatalog != null) {
					addMember(stack.unionBuilder, memberCatalog);
				}
			}
		}
		
		elementCatalogs.put(newStackInfo.stream, catalogBuilder.build());
		extensionCatalogs.put(newStackInfo.stream, JsonCatalogMerger.merge(new ArrayList<>(platformDescriptors.values())));

		return newPlatformDescr;
	}

	private static void addMember(final UnionBuilder union, ExtensionCatalog member) {
		if(union != null) {
			final MemberBuilder builder = union.addMember(
					member.getBom().getGroupId() + ":" + member.getBom().getArtifactId(), member.getBom().getVersion());
			member.getExtensions()
			.forEach(e -> builder.addElement(e.getArtifact().getGroupId() + ":" + e.getArtifact().getArtifactId()));
		}
	}

	public PlatformCatalog platformCatalog() {
		return platformCatalog;
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
		elementKeys.add("io.quarkus:quarkus-core"); // this is necessary to pick up the latest quarkus core present in the union
		return ElementCatalogBuilder.getBoms(elementCatalogs.get(stream), elementKeys);
	}

	private static class PlatformStackInfo {
		final String stream;
		final String stackVersion;
		final ExtensionCatalog origin;
		final List<ArtifactCoords> members;
		UnionBuilder unionBuilder;
		private ArtifactCoords coords;
		private Set<ArtifactKey> memberGAs;

		PlatformStackInfo(String stream, String stackVersion, ExtensionCatalog origin, List<ArtifactCoords> members) {
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
