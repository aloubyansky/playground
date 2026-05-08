package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.cyclonedx.model.Dependency;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.junit.jupiter.api.Test;

class BomBuilderTest {

    @Test
    void mainComponentHasCorrectCoordinates() {
        BomBuilder builder = new BomBuilder("com.example", "my-app", "2.0", "dist");
        Bom bom = builder.build();

        Component main = bom.getMetadata().getComponent();
        assertEquals(Component.Type.APPLICATION, main.getType());
        assertEquals("com.example", main.getGroup());
        assertEquals("my-app", main.getName());
        assertEquals("2.0", main.getVersion());
        assertEquals("pkg:maven/com.example/my-app@2.0", main.getPurl());
    }

    @Test
    void mavenArtifactCreatesLibraryComponent() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.apache", "commons-io", "2.15.1", "jar", null),
                "lib/commons-io-2.15.1.jar", "abc123", null);
        Bom bom = builder.build();

        assertEquals(1, bom.getComponents().size());
        Component comp = findByName(bom, "commons-io");
        assertNotNull(comp);
        assertEquals(Component.Type.LIBRARY, comp.getType());
        assertEquals("org.apache", comp.getGroup());
        assertEquals("2.15.1", comp.getVersion());
        assertEquals("pkg:maven/org.apache/commons-io@2.15.1?type=jar", comp.getPurl());
        assertEquals(1, comp.getHashes().size());
        assertEquals("abc123", comp.getHashes().get(0).getValue());

        assertNotNull(comp.getEvidence());
        assertEquals(1, comp.getEvidence().getOccurrences().size());
        assertEquals("lib/commons-io-2.15.1.jar",
                comp.getEvidence().getOccurrences().get(0).getLocation());
    }

    @Test
    void mavenArtifactWithClassifier() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "bar", "3.0", "jar", "sources"),
                null, null, null);
        Bom bom = builder.build();

        Component comp = findByName(bom, "bar");
        assertNotNull(comp);
        assertEquals("pkg:maven/org.foo/bar@3.0?type=jar&classifier=sources", comp.getPurl());
    }

    @Test
    void fileCreatesFileComponent() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addFile("conf/app.properties", "deadbeef");
        Bom bom = builder.build();

        assertEquals(1, bom.getComponents().size());
        Component comp = findByName(bom, "app.properties");
        assertNotNull(comp);
        assertEquals(Component.Type.FILE, comp.getType());
        assertTrue(comp.getPurl().startsWith("pkg:generic/app.properties"));
        assertTrue(comp.getPurl().contains("checksum=sha256:deadbeef"));

        assertNotNull(comp.getEvidence());
        assertEquals("conf/app.properties",
                comp.getEvidence().getOccurrences().get(0).getLocation());
    }

    @Test
    void dependencyGraphConnectsComponents() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.a", "lib-a", "1.0", "jar", null),
                "lib/lib-a-1.0.jar", null, null);
        builder.addMavenArtifact(
                new ArtifactCoords("org.b", "lib-b", "2.0", "jar", null),
                "lib/lib-b-2.0.jar", null, null);
        Map<ArtifactCoords, List<ArtifactCoords>> graph = new HashMap<>();
        graph.put(new ArtifactCoords("org.a", "lib-a", "1.0", "jar", null),
                List.of(new ArtifactCoords("org.b", "lib-b", "2.0", "jar", null)));
        graph.put(new ArtifactCoords("org.b", "lib-b", "2.0", "jar", null), List.of());
        builder.setDependencyGraph(graph);
        Bom bom = builder.build();

        String mainRef = bom.getMetadata().getComponent().getBomRef();
        String refA = "pkg:maven/org.a/lib-a@1.0?type=jar";
        String refB = "pkg:maven/org.b/lib-b@2.0?type=jar";

        Dependency mainDep = findDependency(bom, mainRef);
        assertNotNull(mainDep);
        List<String> mainChildren = mainDep.getDependencies().stream()
                .map(Dependency::getRef).toList();
        assertTrue(mainChildren.contains(refA), "main should depend on A");
        assertFalse(mainChildren.contains(refB), "B is transitive, not direct child of main");

        Dependency depA = findDependency(bom, refA);
        assertNotNull(depA);
        assertTrue(depA.getDependencies().stream()
                .anyMatch(d -> d.getRef().equals(refB)), "A should depend on B");
    }

    @Test
    void noOrphanComponents() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.a", "a", "1.0", "jar", null),
                "lib/a-1.0.jar", null, null);
        builder.addMavenArtifact(
                new ArtifactCoords("org.b", "b", "1.0", "jar", null),
                "lib/b-1.0.jar", null, null);
        builder.addFile("conf/app.cfg", "aabb");
        Map<ArtifactCoords, List<ArtifactCoords>> graph = new HashMap<>();
        graph.put(new ArtifactCoords("org.a", "a", "1.0", "jar", null),
                List.of(new ArtifactCoords("org.b", "b", "1.0", "jar", null)));
        graph.put(new ArtifactCoords("org.b", "b", "1.0", "jar", null), List.of());
        builder.setDependencyGraph(graph);
        Bom bom = builder.build();

        Set<String> allRefs = new HashSet<>();
        allRefs.add(bom.getMetadata().getComponent().getBomRef());
        for (Component c : bom.getComponents()) {
            allRefs.add(c.getBomRef());
        }

        Set<String> reachable = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(bom.getMetadata().getComponent().getBomRef());
        while (!queue.isEmpty()) {
            String ref = queue.poll();
            if (!reachable.add(ref))
                continue;
            Dependency dep = findDependency(bom, ref);
            if (dep != null && dep.getDependencies() != null) {
                for (Dependency child : dep.getDependencies()) {
                    queue.add(child.getRef());
                }
            }
        }

        assertEquals(allRefs, reachable, "All components should be reachable from main");
    }

    @Test
    void duplicateArtifactMergesOccurrences() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "jspecify", "1.0.0", "jar", null),
                "lib/jspecify-1.0.0.jar", "aaa", null);
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "jspecify", "1.0.0", "jar", null),
                "web/console.war/WEB-INF/lib/jspecify-1.0.0.jar", "aaa", null);
        Bom bom = builder.build();

        long jspecifyCount = bom.getComponents().stream()
                .filter(c -> "jspecify".equals(c.getName())).count();
        assertEquals(1, jspecifyCount, "should be one jspecify component, not two");
        Component comp = findByName(bom, "jspecify");
        assertNotNull(comp);
        assertEquals(2, comp.getEvidence().getOccurrences().size(),
                "should have two occurrences");
        assertEquals("lib/jspecify-1.0.0.jar",
                comp.getEvidence().getOccurrences().get(0).getLocation());
        assertEquals("web/console.war/WEB-INF/lib/jspecify-1.0.0.jar",
                comp.getEvidence().getOccurrences().get(1).getLocation());
    }

    @Test
    void explicitDependencyCreatesEdge() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        ArtifactCoords warId = new ArtifactCoords("org.a", "war", "1.0", "war", null);
        builder.addMavenArtifact(warId, "web/console.war/", "w1", null);
        builder.addNestedMavenArtifact(warId,
                new ArtifactCoords("org.b", "nested-lib", "2.0", "jar", null),
                "web/console.war/WEB-INF/lib/nested-lib-2.0.jar", "n1", null);
        builder.addExplicitDependency(warId, new ArtifactCoords("org.b", "nested-lib", "2.0", "jar", null));
        Bom bom = builder.build();

        String warRef = "pkg:maven/org.a/war@1.0?type=war";
        Component warComp = findByName(bom, "war");
        assertNotNull(warComp);
        assertNotNull(warComp.getComponents(), "WAR should have nested components");
        assertEquals(1, warComp.getComponents().size());
        Component nested = warComp.getComponents().get(0);
        assertEquals("nested-lib", nested.getName());
        assertEquals("web/console.war/WEB-INF/lib/nested-lib-2.0.jar",
                nested.getEvidence().getOccurrences().get(0).getLocation());

        assertNull(findByName(bom, "nested-lib"),
                "nested-lib should NOT appear as top-level component");

        Dependency warDep = findDependency(bom, warRef);
        assertNotNull(warDep, "WAR should have a dependency entry");
        assertTrue(warDep.getDependencies().stream()
                .anyMatch(d -> d.getRef().equals("pkg:maven/org.b/nested-lib@2.0?type=jar")),
                "WAR should depend on nested-lib");

        String mainRef = bom.getMetadata().getComponent().getBomRef();
        Dependency mainDep = findDependency(bom, mainRef);
        List<String> mainChildren = mainDep.getDependencies().stream()
                .map(Dependency::getRef).toList();
        assertTrue(mainChildren.contains(warRef), "main should list WAR as direct");
        assertFalse(mainChildren.contains("pkg:maven/org.b/nested-lib@2.0?type=jar"),
                "nested-lib should NOT be a direct child of main");
    }

    @Test
    void emptyBomHasOnlyMetadata() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        Bom bom = builder.build();

        assertNotNull(bom.getMetadata());
        assertTrue(bom.getComponents().isEmpty(), "should have no components beyond metadata");
        assertNotNull(bom.getSerialNumber());
    }

    @Test
    void metadataIncludesToolIdentity() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        Bom bom = builder.build();

        assertNotNull(bom.getMetadata().getToolChoice());
        List<Component> tools = bom.getMetadata().getToolChoice().getComponents();
        assertNotNull(tools);
        assertEquals(1, tools.size());
        assertEquals("assembly-cyclonedx-generator", tools.get(0).getName());
        assertEquals(Component.Type.APPLICATION, tools.get(0).getType());
    }

    @Test
    void mavenArtifactWithLicenses() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        LicenseChoice licenses = createLicenseChoice("Apache-2.0");
        builder.addMavenArtifact(
                new ArtifactCoords("org.apache", "commons-io", "2.15.1", "jar", null),
                "lib/commons-io-2.15.1.jar", "abc123", licenses);
        Bom bom = builder.build();

        Component comp = findByName(bom, "commons-io");
        assertNotNull(comp);
        assertNotNull(comp.getLicenseChoice());
        assertEquals(1, comp.getLicenseChoice().getLicenses().size());
        assertEquals("Apache-2.0", comp.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void duplicateArtifactPreservesFirstLicenses() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        LicenseChoice firstLicenses = createLicenseChoice("MIT");
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "bar", "1.0", "jar", null),
                "lib/bar-1.0.jar", "hash1", firstLicenses);
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "bar", "1.0", "jar", null),
                "other/bar-1.0.jar", "hash1", createLicenseChoice("GPL-3.0-only"));
        Bom bom = builder.build();

        Component comp = findByName(bom, "bar");
        assertNotNull(comp);
        assertEquals("MIT", comp.getLicenseChoice().getLicenses().get(0).getId(),
                "first-registered licenses should be preserved");
    }

    @Test
    void duplicateArtifactGetsLicensesIfFirstHadNone() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "bar", "1.0", "jar", null),
                "lib/bar-1.0.jar", "hash1", null);
        builder.addMavenArtifact(
                new ArtifactCoords("org.foo", "bar", "1.0", "jar", null),
                "other/bar-1.0.jar", "hash1", createLicenseChoice("MIT"));
        Bom bom = builder.build();

        Component comp = findByName(bom, "bar");
        assertNotNull(comp);
        assertNotNull(comp.getLicenseChoice(), "should get licenses from second registration");
        assertEquals("MIT", comp.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void nestedArtifactWithLicenses() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addMavenArtifact(
                new ArtifactCoords("org.a", "war", "1.0", "war", null),
                "web/console.war/", "w1", null);
        LicenseChoice licenses = createLicenseChoice("Apache-2.0");
        builder.addNestedMavenArtifact(
                new ArtifactCoords("org.a", "war", "1.0", "war", null),
                new ArtifactCoords("org.b", "nested-lib", "2.0", "jar", null),
                "web/console.war/WEB-INF/lib/nested-lib-2.0.jar", "n1", licenses);
        Bom bom = builder.build();

        Component war = findByName(bom, "war");
        assertNotNull(war);
        assertNotNull(war.getComponents());
        Component nested = war.getComponents().get(0);
        assertNotNull(nested.getLicenseChoice());
        assertEquals("Apache-2.0", nested.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void projectLicensesAppliedToMainComponent() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.setProjectLicenses(createLicenseChoice("Apache-2.0"));
        Bom bom = builder.build();

        Component main = bom.getMetadata().getComponent();
        assertNotNull(main.getLicenseChoice(), "main component should have licenses");
        assertEquals("Apache-2.0", main.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void projectLicensesAppliedToFileComponents() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.setProjectLicenses(createLicenseChoice("MIT"));
        builder.addFile("conf/app.properties", "deadbeef");
        builder.addFile("bin/start.sh", "cafebabe");
        Bom bom = builder.build();

        Component props = findByName(bom, "app.properties");
        assertNotNull(props);
        assertNotNull(props.getLicenseChoice(), "file component should inherit project license");
        assertEquals("MIT", props.getLicenseChoice().getLicenses().get(0).getId());

        Component script = findByName(bom, "start.sh");
        assertNotNull(script);
        assertEquals("MIT", script.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void fileComponentsHaveNoLicenseWhenProjectLicenseNotSet() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.addFile("conf/app.properties", "deadbeef");
        Bom bom = builder.build();

        Component props = findByName(bom, "app.properties");
        assertNotNull(props);
        assertNull(props.getLicenseChoice(),
                "file component should have no license when project license is not set");
    }

    @Test
    void toolMetadataIncludesLicenseAndHash() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.setToolLicenses(createLicenseChoice("Apache-2.0"));
        builder.setToolHash("abc123def456");
        Bom bom = builder.build();

        List<Component> tools = bom.getMetadata().getToolChoice().getComponents();
        assertEquals(1, tools.size());
        Component tool = tools.get(0);

        assertNotNull(tool.getLicenseChoice(), "tool should have license");
        assertEquals("Apache-2.0", tool.getLicenseChoice().getLicenses().get(0).getId());

        assertNotNull(tool.getHashes(), "tool should have hash");
        assertEquals(1, tool.getHashes().size());
        assertEquals("abc123def456", tool.getHashes().get(0).getValue());
    }

    @Test
    void mainComponentPurlIncludesArchiveTypeAndClassifier() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.setArchiveType("zip");
        builder.setClassifier("dist");
        Bom bom = builder.build();

        Component main = bom.getMetadata().getComponent();
        assertEquals("pkg:maven/com.example/app@1.0?type=zip&classifier=dist",
                main.getPurl());
        assertEquals(main.getPurl(), main.getBomRef());
    }

    @Test
    void mainComponentPurlWithArchiveTypeOnly() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        builder.setArchiveType("tar.gz");
        Bom bom = builder.build();

        Component main = bom.getMetadata().getComponent();
        assertEquals("pkg:maven/com.example/app@1.0?type=tar.gz", main.getPurl());
    }

    @Test
    void mainComponentPurlBareWhenNoArchiveType() {
        BomBuilder builder = new BomBuilder("com.example", "app", "1.0", "dist");
        Bom bom = builder.build();

        Component main = bom.getMetadata().getComponent();
        assertEquals("pkg:maven/com.example/app@1.0", main.getPurl());
    }

    private static LicenseChoice createLicenseChoice(String spdxId) {
        License license = new License();
        license.setId(spdxId);
        LicenseChoice choice = new LicenseChoice();
        choice.addLicense(license);
        return choice;
    }

    private static Component findByName(Bom bom, String name) {
        return bom.getComponents().stream()
                .filter(c -> name.equals(c.getName()))
                .findFirst().orElse(null);
    }

    private static Dependency findDependency(Bom bom, String ref) {
        if (bom.getDependencies() == null)
            return null;
        return bom.getDependencies().stream()
                .filter(d -> ref.equals(d.getRef()))
                .findFirst().orElse(null);
    }
}
