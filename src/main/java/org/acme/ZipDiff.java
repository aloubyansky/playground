package org.acme;

import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.paths.PathTree;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.artifact.JavaScopes;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Array;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;

public class ZipDiff {

    public static void main(String[] args) throws Exception {
/*
        var resolver = MavenArtifactResolver.builder()
                .setWorkspaceDiscovery(false)
                .build();
        var artifact = new DefaultArtifact("io.quarkus",
                "quarkus-extension-maven-plugin",
                "jar", "3.2.9.Final-redhat-00002");
//        resolver.resolvePluginDependencies(artifact);

        var bom = new DefaultArtifact("com.redhat.quarkus.platform", "quarkus-bom", "pom", "3.2.9.Final-redhat-00003");
        var constraints = resolver.resolveDescriptor(bom).getManagedDependencies();
*/
/*
        var request = new CollectRequest()
                .setRootArtifact(new DefaultArtifact("some", "root", "pom", "1.0"))
                .setDependencies(List.of(
                        new Dependency(
                                artifact,
                                JavaScopes.RUNTIME,
                                false,
                                List.of())))
                //.setManagedDependencies(constraints)
                .setRepositories(resolver.getMavenContext().getRemotePluginRepositories());
*/
/*
        var request = new CollectRequest()
                .setRoot(
                        new Dependency(
                                artifact,
                                JavaScopes.RUNTIME,
                                false,
                                List.of()))
                .setManagedDependencies(constraints)
                .setRepositories(resolver.getMavenContext().getRemotePluginRepositories());

        resolver.getSystem().resolveDependencies(resolver.getSession(), new DependencyRequest().setCollectRequest(request));
*/
        zipDiff();
/*
        final String zip1Path = "/home/aloubyansky/playground/bacon/deliverables-resolve-only/quarkus-platform-3.2.9.CR3/rh-quarkus-platform-3.2.9.CR3-maven-repository.zip";
        //final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-resolve-only/quarkus-platform-3.2.6.CR1/rh-quarkus-platform-3.2.6.CR1-maven-repository.zip";
        //final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-bom/quarkus-platform-3.2.6.CR1/rh-quarkus-platform-3.2.6.CR1-maven-repository.zip";
        final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-bom/quarkus-platform-3.2.9.CR3/rh-quarkus-platform-3.2.9.CR3-maven-repository.zip";

        var zip = getArtifactsForZip(zip2Path);
        var txt = getArtifactsFromTxt("/home/aloubyansky/playground/bacon/deliverables-bom/quarkus-platform-3.2.9.CR3/collected-artifacts.txt");
        var txtOnly = new ArrayList<ArtifactCoords>();
        for(var a : txt) {
            if(!zip.remove(a)) {
                txtOnly.add(a);
            }
        }

        if(!txtOnly.isEmpty()) {
            log("Found only in TXT ");
            for(var p : txtOnly) {
                log(p);
            }
        }
        if(!zip.isEmpty()) {
            log("Found only in ZIP");
            for(var p : zip) {
                log(p);
            }
        }

 */
    }

    private static void zipDiff() {
        final String zip1Path = "/home/aloubyansky/playground/bacon/deliverables-resolve-only/quarkus-platform-3.2.9.CR3/rh-quarkus-platform-3.2.9.CR3-maven-repository.zip";
        //final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-resolve-only/quarkus-platform-3.2.6.CR1/rh-quarkus-platform-3.2.6.CR1-maven-repository.zip";
        //final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-bom/quarkus-platform-3.2.6.CR1/rh-quarkus-platform-3.2.6.CR1-maven-repository.zip";
        final String zip2Path = "/home/aloubyansky/playground/bacon/deliverables-bom/quarkus-platform-3.2.9.CR3/rh-quarkus-platform-3.2.9.CR3-maven-repository.zip";

        var zip1 = getPaths(zip1Path);
        var zip2 = getPaths(zip2Path);

        var zip1Only = new ArrayList<String>();
        for(var p : zip1) {
            if(!zip2.remove(p)) {
                zip1Only.add(p);
            }
        }

        if(!zip1Only.isEmpty()) {
            log("Found only in " + zip1Path);
            Collections.sort(zip1Only);
            for(var p : zip1Only) {
                log(p);
            }
        }
        if(!zip2.isEmpty()) {
            log("Found only in " + zip2Path);
            var zip2Only = new ArrayList<>(zip2);
            Collections.sort(zip2Only);
            for(var p : zip2Only) {
                log(p);
            }
        }
    }

    private static Set<String> getPaths(String zip) {
        var p = Path.of(zip);
        final Set<String> paths = new HashSet<>();
        PathTree.ofDirectoryOrArchive(p).walk(visit -> paths.add(visit.getRelativePath("/")));
        return paths;
    }

    private static List<ArtifactCoords> getArtifactsFromTxt(String path) {
        var result = new ArrayList<ArtifactCoords>();
        try(BufferedReader reader = Files.newBufferedReader(Path.of(path))) {
            String line = reader.readLine();
            while(line != null) {
                ArtifactCoords c = ArtifactCoords.fromString(line);
                if(c.getVersion().contains("redhat")) {
                    result.add(c);
                }
                line = reader.readLine();
            }
        } catch(IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    private static Set<ArtifactCoords> getArtifactsForZip(String zip) {
        var p = Path.of(zip);
        final Set<ArtifactCoords> result = new HashSet<>();
        PathTree.ofDirectoryOrArchive(p).walk(visit -> {
            if(visit.getPath().getNameCount() < 4 || Files.isDirectory(visit.getPath())) {
                return;
            }
            var name = visit.getPath().getFileName().toString();
            if(name.endsWith(".md5") || name.endsWith(".sha1")) {
                return;
            }
            var version = visit.getPath().getParent().getFileName().toString();
            var i = name.indexOf(version);
            var artifactId = name.substring(0, i - 1);
            final String classifier;
            if(name.charAt(i + version.length()) == '.') {
                classifier = ArtifactCoords.DEFAULT_CLASSIFIER;
            } else {
                classifier = name.substring(i + version.length() + 1, name.lastIndexOf('.'));
                if(classifier.equals("sources")) {
                    return;
                }
            }
            var type = name.substring(artifactId.length() + 1 + version.length() + 1 + (classifier.isEmpty() ? 0 : classifier.length() + 1));
            var groupId = new StringJoiner(".");
            for(int j = 2; j < visit.getPath().getNameCount() - 3; ++j) {
                groupId.add(visit.getPath().getName(j).toString());
            }
            result.add(ArtifactCoords.of(groupId.toString(), artifactId, classifier, type, version));
        });
        return result;
    }

    private static void log(Object o) {
        System.out.println(o);
    }
}
