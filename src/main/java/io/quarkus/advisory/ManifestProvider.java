package io.quarkus.advisory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class ManifestProvider {

    public static Map<ArtifactKey, ManifestComponent> getManifestComponents(String namespace, String name, String version) {
        final URL url;
        try {
            url = new URL("https", "sbomer.pnc.engineering.redhat.com", 443,
                    "/api/v1alpha1/sboms?query=rootPurl=eq=%22pkg:maven/"
                            + namespace + "/"
                            + name + "@"
                            + version
                            + "?type=pom%22&sort=creationTime=desc=&pageIndex=0&pageSize=1");
            MessageWriter.info("Requesting manifest " + url);
        } catch (MalformedURLException e) {
            throw new RuntimeException("Failed to parse URL", e);
        }
        final SbomerResponse response;
        try {
            final URLConnection connection = url.openConnection();
            response = SbomerResponse.deserialize(connection.getInputStream());
        } catch (IOException e) {
            MessageWriter.info("Failed to connect to " + url + ": " + e.getLocalizedMessage());
            return null;
        }

        var sbom = response.getContent().get(0).getSbom();
        final Map<ArtifactKey, Collection<String>> components;
        ArrayNode arr = (ArrayNode) sbom.get("components");
        if (arr != null && !arr.isEmpty()) {
            components = new HashMap<>();
            for (JsonNode i : arr) {
                var purl = i.get("purl");
                if (purl != null) {
                    final PackageURL parsed;
                    try {
                        parsed = new PackageURL(purl.asText());
                    } catch (MalformedPackageURLException e) {
                        throw new RuntimeException("Failed to parse PURL " + purl.asText(), e);
                    }
                    if (PackageURL.StandardTypes.MAVEN.equals(parsed.getType())) {
                        var qualifiers = parsed.getQualifiers();
                        var type = qualifiers.get("type");
                        var classifier = qualifiers.get("classifier");
                        components
                                .computeIfAbsent(
                                        ArtifactKey.of(parsed.getNamespace(), parsed.getName(), classifier,
                                                type == null || type.isEmpty() ? ArtifactCoords.TYPE_JAR : type),
                                        k -> new HashSet<>(1))
                                .add(parsed.getVersion());
                    }
                }
            }
            final Map<ArtifactKey, ManifestComponent> result = new HashMap<>(components.size());
            for (Map.Entry<ArtifactKey, Collection<String>> e : components.entrySet()) {
                result.put(e.getKey(), ManifestComponent.of(e.getKey(), e.getValue()));
            }
            return result;
        }
        return Map.of();
    }
}
