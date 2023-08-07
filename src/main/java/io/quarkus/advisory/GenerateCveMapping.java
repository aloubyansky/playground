package io.quarkus.advisory;

import static io.quarkus.advisory.MessageWriter.info;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "cve-mapping", mixinStandardHelpOptions = true)
public class GenerateCveMapping implements Runnable {

    @CommandLine.Option(names = { "-t",
            "--jira-token" }, description = "The Personal Access Token for authenticating with the JIRA server, could also be set using environment variable JIRA_TOKEN")
    private String jiraToken;

    @CommandLine.Option(names = { "-s",
            "--jira-server" }, description = "The JIRA server to connect to", defaultValue = "https://issues.redhat.com")
    private String jiraServerURL;

    @CommandLine.Option(names = {
            "--jira-version" }, description = "The target fixVersion of the project in Jira", required = true)
    private String jiraVersion;

    @CommandLine.Option(names = { "--bom" }, description = "Quarkus platform BOM", required = true)
    private String bom;

    @CommandLine.Option(names = {
            "--output-file" }, description = "If specified, this parameter will cause the output to be written to the path specified, instead of writing to the console.")
    public File outputFile;

    @Override
    public void run() {

        final Map<String, List<ArtifactCoords>> vulnerableMapping = JiraReader.readVulnerableArtifacts(jiraServerURL,
                getJiraToken(), "QUARKUS", jiraVersion);

        var bomParts = bom.split(":");
        if (bomParts.length < 3) {
            throw new IllegalArgumentException("Failed to extract groupId:artifactId:version from " + bom);
        }
        final String bomGroupId = bomParts[0];
        final String bomArtifactId = bomParts[1];
        final String bomVersion = bomParts[bomParts.length - 1];
        final Map<ArtifactKey, ManifestComponent> components = ManifestProvider.getManifestComponents(bomGroupId, bomArtifactId,
                bomVersion);

        final Map<String, List<String>> cveMapping = new TreeMap<>();
        for (Map.Entry<String, List<ArtifactCoords>> vulnerableArtifacts : vulnerableMapping.entrySet()) {
            if (vulnerableArtifacts.getValue().isEmpty()) {
                continue;
            }
            final String cveId = vulnerableArtifacts.getKey();
            final List<String> purls = new ArrayList<>(vulnerableArtifacts.getValue().size());
            for (ArtifactCoords vulnerableArtifact : vulnerableArtifacts.getValue()) {
                var fixedComponent = components.get(vulnerableArtifact.getKey());
                List<String> fixedVersions = List.of();
                if (fixedComponent != null) {
                    fixedVersions = new ArrayList<>(fixedComponent.getVersions());
                    for (var va : vulnerableArtifacts.getValue()) {
                        fixedVersions.remove(va.getVersion());
                    }
                }
                if (!fixedVersions.isEmpty()) {
                    info("  Component " + vulnerableArtifact + " was replaced with " + fixedComponent.getKey() + ":"
                            + fixedVersions);
                    for (String v : fixedVersions) {
                        purls.add(toPurl(fixedComponent.getKey(), v).toString());
                    }
                } else if (fixedComponent == null) {
                    info("  Component " + vulnerableArtifact.toCompactCoords() + " was removed");
                }
            }
            if (!purls.isEmpty()) {
                Collections.sort(purls);
                cveMapping.put(cveId, purls);
            }
        }

        var root = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, List<String>> e : cveMapping.entrySet()) {
            var arr = JsonNodeFactory.instance.arrayNode();
            root.put(e.getKey(), arr);
            for (String purl : e.getValue()) {
                arr.add(purl);
            }
        }

        try (Writer writer = getResultWriter()) {
            SbomerResponse.getMapper().writeValue(writer, root);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Writer getResultWriter() throws IOException {
        if (outputFile != null) {
            if (outputFile.isDirectory()) {
                throw new IllegalArgumentException(outputFile + " appears to be a directory");
            }
            var parentDir = outputFile.getAbsoluteFile().getParentFile();
            if (parentDir != null) {
                parentDir.mkdirs();
            }
            info("Saving result in " + outputFile.getAbsolutePath());
            return Files.newBufferedWriter(outputFile.toPath());
        }
        return new OutputStreamWriter(System.out);
    }

    private static PackageURL toPurl(ArtifactKey key, String version) {
        final TreeMap<String, String> qualifiers = new TreeMap<>();
        qualifiers.put("type", key.getType());
        if (!key.getClassifier().isEmpty()) {
            qualifiers.put("classifier", key.getClassifier());
        }
        try {
            return new PackageURL(PackageURL.StandardTypes.MAVEN,
                    key.getGroupId(),
                    key.getArtifactId(),
                    version,
                    qualifiers, null);
        } catch (MalformedPackageURLException e) {
            throw new RuntimeException("Failed to generate Purl for " + key + ":" + version, e);
        }
    }

    private String getJiraToken() {
        if (jiraToken != null) {
            return jiraToken;
        }
        jiraToken = System.getenv("JIRA_TOKEN");
        if (jiraToken == null) {
            throw new IllegalArgumentException("Neither --jira-token nor environment variable JIRA_TOKEN have been set");
        }
        return jiraToken;
    }
}
