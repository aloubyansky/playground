package org.acme;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import io.quarkus.bom.decomposer.maven.playground.Element;
import io.quarkus.bom.decomposer.maven.playground.ElementCatalog;
import io.quarkus.bom.decomposer.maven.playground.ElementCatalogBuilder;
import io.quarkus.bom.decomposer.maven.playground.ElementCatalogBuilder.MemberBuilder;
import io.quarkus.bom.decomposer.maven.playground.ElementCatalogBuilder.UnionBuilder;
import io.quarkus.bom.decomposer.maven.playground.Member;
import io.quarkus.bom.decomposer.maven.playground.UnionVersion;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.ExtensionCatalog;
import io.quarkus.registry.catalog.json.JsonCatalogMapperHelper;
import io.quarkus.registry.catalog.json.JsonCatalogMerger;
import io.quarkus.registry.catalog.json.JsonExtensionCatalog;

@Singleton
public class PlatformRegistry {

	@Inject
	MavenArtifactResolver resolver;
	
	private Map<String, ExtensionCatalog> platforms = new TreeMap<>();
	private ElementCatalog elementCatalog;

	public ExtensionCatalog registerPlatform(String groupId, String artifactId, String version) {
		final Artifact artifact = new DefaultArtifact(groupId, artifactId, version, "json", version);
		final ExtensionCatalog platformDescr = resolveCatalog(artifact, false);
    	platforms.put(key(platformDescr), platformDescr);
    	
    	final Set<String> processedUnions = new HashSet<>(4);
    	final ElementCatalogBuilder catalogBuilder = ElementCatalogBuilder.newInstance();
    	for(ExtensionCatalog c : platforms.values()) {
    		Map<Object, Object> platformStack = (Map<Object, Object>) c.getMetadata().get("platform-stack");
    		final String versionStr = (String) platformStack.get("version");
    		if(!processedUnions.add(versionStr)) {
    			continue;
    		}
    		final UnionBuilder union = catalogBuilder.newUnion(Integer.parseInt(versionStr));
    		final List<String> members = (List<String>) platformStack.get("members");
    		for(String memberCoordsStr : members) {
    			final ArtifactCoords memberCoords = ArtifactCoords.fromString(memberCoordsStr);
    			final ExtensionCatalog memberCatalog = resolveCatalog(new DefaultArtifact(memberCoords.getGroupId(), memberCoords.getArtifactId(), memberCoords.getClassifier(), memberCoords.getType(), memberCoords.getVersion()), true);
    			if(memberCatalog != null) {
    			    final MemberBuilder member = union.addMember(memberCatalog.getBom().getGroupId() + ":" + memberCatalog.getBom().getArtifactId(), memberCatalog.getBom().getVersion());
    			    memberCatalog.getExtensions().forEach(e -> member.addElement(e.getArtifact().getGroupId() + ":" + e.getArtifact().getArtifactId()));
    			}
    		}
    	}
    	elementCatalog = catalogBuilder.build();
    	
    	return platformDescr;
	}

	private ExtensionCatalog resolveCatalog(final Artifact artifact, boolean ignoreError) {
		final java.nio.file.Path jsonFile;
    	try {
			jsonFile = resolver.resolve(artifact).getArtifact().getFile().toPath();
		} catch (BootstrapMavenException e) {
			if(!ignoreError) {
			    throw new RuntimeException("Failed to resolver " + artifact, e);
			}
			return null;
		}
    	final ExtensionCatalog platformDescr;
    	try {
			platformDescr = JsonCatalogMapperHelper.deserialize(jsonFile, JsonExtensionCatalog.class);
		} catch (IOException e) {
			if(!ignoreError) {
			    throw new RuntimeException("Failed to deserialize platform descriptor " + jsonFile, e);
			}
			return null;
		}
		return platformDescr;
	}
	
	public ExtensionCatalog catalog() {
		return JsonCatalogMerger.merge(new ArrayList<>(platforms.values()));
	}
	
    public List<String> getBoms(Collection<String> elementKeys) {

        final Comparator<UnionVersion> comparator = UnionVersion::compareTo;
        final Map<UnionVersion, Map<Object, Member>> unionVersions = new TreeMap<>(comparator.reversed());
        for (Object elementKey : elementKeys) {
            final Element e = elementCatalog.get(elementKey);
            if (e == null) {
                throw new RuntimeException("Element " + elementKey + " not found in the catalog");
            }
            for (Member m : e.members()) {
                unionVersions.computeIfAbsent(m.unionVersion(), v -> new HashMap<>()).put(m.key(), m);
            }
        }

        for (Map<Object, Member> members : unionVersions.values()) {
            final Set<Object> memberElementKeys = new HashSet<>();
            members.values().forEach(m -> memberElementKeys.addAll(m.elementKeys()));
            if (memberElementKeys.containsAll(elementKeys)) {
            	final List<String> boms = new ArrayList<>(members.size());
            	members.values().forEach(m -> boms.add(m.key() + ":" + m.version()));
                return boms;
            }
        }
        return Collections.emptyList();
    }
	
	private static String key(ExtensionCatalog platformDescr) {
		return platformDescr.getBom().getGroupId() + ":" + platformDescr.getBom().getArtifactId();
	}
}
