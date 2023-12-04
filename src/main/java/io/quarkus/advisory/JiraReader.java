package io.quarkus.advisory;

import com.atlassian.httpclient.api.Request;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousJiraRestClientFactory;
import io.quarkus.maven.dependency.ArtifactCoords;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class JiraReader {

    private static final String VULNERABLE_MAVEN_ARTIFACTS = "Vulnerable Maven artifacts:";
    private static final String VULNERABLE_MAVEN_ARTIFACT_PREFIX = "- ";

    private static final Set<String> DONE_RESOLUTIONS = Set.of("Done", "Done-Errata");

    public static Map<String, List<ArtifactCoords>> readVulnerableArtifacts(
            String jiraServer, String token, String project, String fixVersion) {

        JiraRestClient restClient = null;
        Map<String, List<ArtifactCoords>> vulnerableMapping = new HashMap<>();
        try {
            restClient = new AsynchronousJiraRestClientFactory().create(new URI(jiraServer),
                    new BearerHttpAuthenticationHandler(token));

            SearchResult searchResultsAll = restClient.getSearchClient()
                    .searchJql("project = " + project
                            + " AND fixVersion = '" + fixVersion + "' AND labels in (SecurityTracking)")
                    .claim();
            MessageWriter.info("Issues total: " + searchResultsAll.getTotal());
            for (Issue issue : searchResultsAll.getIssues()) {
                MessageWriter.info("Issue: " + issue.getKey());
                final List<String> cveIds = issue.getLabels().stream().filter(s -> s.startsWith("CVE-"))
                        .collect(Collectors.toList());
                var sj = new StringJoiner(",");
                for (String cveId : cveIds) {
                    sj.add(cveId);
                }
                MessageWriter.info("  CVEs: " + sj);
                var resolution = (issue.getResolution() != null ? issue.getResolution().getName() : "Unresolved");
                final boolean resolved = DONE_RESOLUTIONS.contains(resolution);
                if (issue.getDescription() == null) {
                    MessageWriter.info("  Description is missing");
                    continue;
                }
                final List<ArtifactCoords> vulnerableArtifacts = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new StringReader(issue.getDescription()))) {
                    String line = reader.readLine();
                    boolean log = false;
                    while (line != null) {
                        if (log) {
                            if (line.startsWith(VULNERABLE_MAVEN_ARTIFACT_PREFIX)) {
                                var coordsStr = line.substring(VULNERABLE_MAVEN_ARTIFACT_PREFIX.length()).trim();
                                var coordsParts = coordsStr.split(":");
                                if (coordsParts.length < 3 || coordsParts.length > 5) {
                                    MessageWriter.info("Failed to parse artifact coordinates " + coordsStr);
                                }
                                var groupId = coordsParts[0];
                                var artifactId = coordsParts[1];
                                var version = coordsParts[coordsParts.length - 1];
                                var classifier = coordsParts.length > 3 ? coordsParts[2] : ArtifactCoords.DEFAULT_CLASSIFIER;
                                var type = coordsParts.length > 4 ? coordsParts[3] : ArtifactCoords.TYPE_JAR;
                                var coords = ArtifactCoords.of(groupId, artifactId, classifier, type, version);
                                vulnerableArtifacts.add(coords);
                                MessageWriter.info("    " + coords);
                            } else {
                                break;
                            }
                        } else {
                            log = line.equals(VULNERABLE_MAVEN_ARTIFACTS);
                            if (log) {
                                MessageWriter.info("  " + line);
                            }
                        }
                        line = reader.readLine();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to read description of " + issue.getKey(), e);
                }

                if (!resolved) {
                    MessageWriter.info("  Skipping since the issue is resolved as '" + resolution + "'");
                } else if (vulnerableArtifacts.isEmpty()) {
                    MessageWriter.info("  Skipping since the issue description is missing vulnerable Maven artifacts");
                } else {
                    for (String cveId : cveIds) {
                        vulnerableMapping.put(cveId, vulnerableArtifacts);
                    }
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        } finally {
            if (restClient != null) {
                try {
                    restClient.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return vulnerableMapping;
    }

    public static class BearerHttpAuthenticationHandler implements AuthenticationHandler {

        private static final String AUTHORIZATION_HEADER = "Authorization";
        private final String token;

        public BearerHttpAuthenticationHandler(final String token) {
            this.token = token;
        }

        @Override
        public void configure(Request.Builder builder) {
            builder.setHeader(AUTHORIZATION_HEADER, "Bearer " + token);
        }
    }
}
