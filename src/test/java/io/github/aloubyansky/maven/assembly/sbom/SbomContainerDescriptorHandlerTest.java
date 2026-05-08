package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Build;
import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipArchiver;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.cyclonedx.model.Bom;
import org.cyclonedx.model.Component;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionContext;
import org.eclipse.aether.collection.DependencySelector;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.Dependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SbomContainerDescriptorHandlerTest {

    @TempDir
    Path tempDir;

    @Mock
    MavenProject project;

    @Mock
    MavenSession session;

    @Mock
    RepositorySystem repoSystem;

    @Mock
    RepositorySystemSession repoSession;

    @Mock
    EffectiveModelResolver effectiveModelResolver;

    private SbomContainerDescriptorHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new SbomContainerDescriptorHandler();
        setField(handler, "project", project);
        setField(handler, "session", session);
        setField(handler, "repoSystem", repoSystem);
        setField(handler, "effectiveModelResolver", effectiveModelResolver);

        lenient().when(project.getGroupId()).thenReturn("com.example");
        lenient().when(project.getArtifactId()).thenReturn("test-app");
        lenient().when(project.getVersion()).thenReturn("1.0");
        Build build = new Build();
        build.setDirectory(tempDir.resolve("target").toString());
        lenient().when(project.getBuild()).thenReturn(build);
        lenient().when(effectiveModelResolver.resolveEffectiveModel(
                "com.example", "test-app", "1.0")).thenReturn(null);
        lenient().when(session.getRepositorySession()).thenReturn(repoSession);
        DependencySelector defaultSelector = mock(DependencySelector.class);
        lenient().when(defaultSelector.selectDependency(any())).thenReturn(true);
        lenient().when(defaultSelector.deriveChildSelector(any())).thenReturn(defaultSelector);
        lenient().when(repoSession.getDependencySelector()).thenReturn(defaultSelector);
        lenient().when(session.getProjects()).thenReturn(List.of());
    }

    @Test
    void isSelectedAlwaysReturnsTrue() throws Exception {
        FileInfo fileInfo = mock(FileInfo.class);
        assertTrue(handler.isSelected(fileInfo));
    }

    @Test
    void artifactMatchedByFilename() throws Exception {
        Path jarFile = createTestJar("foo-1.0.jar", "hello");
        Artifact artifact = createArtifact("org.example", "foo", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        ZipArchiver archiver = buildArchiver("base/lib/foo-1.0.jar", jarFile);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component lib = findComponent(bom, Component.Type.LIBRARY, "foo");
        assertNotNull(lib, "should find foo as LIBRARY");
        assertEquals("pkg:maven/org.example/foo@1.0?type=jar", lib.getPurl());
        assertEquals("lib/foo-1.0.jar",
                lib.getEvidence().getOccurrences().get(0).getLocation());
    }

    @Test
    void artifactMatchedByHash() throws Exception {
        Path jarFile = createTestJar("foo-1.0.jar", "unique-content-for-hash-match");
        Artifact artifact = createArtifact("org.example", "foo", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        ZipArchiver archiver = buildArchiver("base/lib/renamed.jar", jarFile);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component lib = findComponent(bom, Component.Type.LIBRARY, "foo");
        assertNotNull(lib, "should match by SHA-256 even with different filename");
    }

    @Test
    void unmatchedFileBecomesFileComponent() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("app.properties", "key=value");

        ZipArchiver archiver = buildArchiver("base/conf/app.properties", props);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component file = findComponent(bom, Component.Type.FILE, "app.properties");
        assertNotNull(file, "unmatched file should be FILE component");
        assertTrue(file.getPurl().startsWith("pkg:generic/app.properties"));
    }

    @Test
    void baseDirectoryStripped() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("app.properties", "key=value");

        ZipArchiver archiver = buildArchiver("myapp-1.0/conf/app.properties", props);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component file = findComponent(bom, Component.Type.FILE, "app.properties");
        assertNotNull(file);
        assertEquals("conf/app.properties",
                file.getEvidence().getOccurrences().get(0).getLocation());
    }

    @Test
    void bomAddedInsideArchive() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("app.properties", "data");

        ZipArchiver archiver = buildArchiver("base/conf/app.properties", props);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        assertNotNull(bom, "BOM should be added to the archive");
    }

    @Test
    void xmlFormatOutput() throws Exception {
        handler.setOutput("external");
        handler.setFormat("xml");
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("data.txt", "hello");

        Path archivePath = tempDir.resolve("test-xml.zip");
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(archivePath.toFile());
        archiver.addFile(props.toFile(), "base/data.txt");
        handler.finalizeArchiveCreation(archiver);
        archiver.createArchive();

        Path bomFile = tempDir.resolve("test-xml.zip.cdx.xml");
        assertTrue(Files.exists(bomFile));
        String content = Files.readString(bomFile);
        assertTrue(content.contains("<bom"));
    }

    @Test
    void unpackedArtifactDetectedByDigest() throws Exception {
        Path entryContent = createTestFile("entry.txt", "unpacked-entry-content");
        byte[] entryBytes = Files.readAllBytes(entryContent);

        Path jarFile = tempDir.resolve("mywar-1.0.war");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarFile))) {
            jos.putNextEntry(new JarEntry("WEB-INF/entry.txt"));
            jos.write(entryBytes);
            jos.closeEntry();
        }

        Artifact warArtifact = createArtifact("org.example", "mywar", "1.0", "war", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(warArtifact));

        ZipArchiver archiver = buildArchiver("base/web/entry.txt", entryContent);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component lib = findComponent(bom, Component.Type.LIBRARY, "mywar");
        assertNotNull(lib, "unpacked WAR should be detected as LIBRARY via digest match");
        assertTrue(lib.getPurl().contains("type=war"));
    }

    @Test
    void dependencyGraphIncluded() throws Exception {
        Path jarA = createTestJar("a-1.0.jar", "content-a");
        Path jarB = createTestJar("b-1.0.jar", "content-b");
        Artifact artifactA = createArtifact("org.x", "a", "1.0", "jar", jarA.toFile());
        Artifact artifactB = createArtifact("org.x", "b", "1.0", "jar", jarB.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifactA, artifactB));
        when(project.getDependencies()).thenReturn(List.of());

        org.eclipse.aether.artifact.Artifact aetherA = new org.eclipse.aether.artifact.DefaultArtifact("org.x", "a", "jar",
                "1.0");
        org.eclipse.aether.artifact.Artifact aetherB = new org.eclipse.aether.artifact.DefaultArtifact("org.x", "b", "jar",
                "1.0");
        Dependency depA = new Dependency(aetherA, "compile");
        Dependency depB = new Dependency(aetherB, "compile");

        when(repoSystem.collectDependencies(any(RepositorySystemSession.class), any())).thenAnswer(invocation -> {
            RepositorySystemSession sess = invocation.getArgument(0);
            DependencySelector selector = sess.getDependencySelector();

            var rootCtx = mockCollectionContext(
                    new org.eclipse.aether.artifact.DefaultArtifact(
                            "com.example", "test-app", "jar", "1.0"),
                    null);
            DependencySelector rootChild = selector.deriveChildSelector(rootCtx);
            rootChild.selectDependency(depA);

            var aCtx = mockCollectionContext(aetherA, depA);
            DependencySelector aChild = rootChild.deriveChildSelector(aCtx);
            aChild.selectDependency(depB);

            DefaultDependencyNode root = new DefaultDependencyNode((Dependency) null);
            root.setChildren(List.of());
            CollectResult result = new CollectResult(invocation.getArgument(1));
            result.setRoot(root);
            return result;
        });

        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(tempDir.resolve("out.zip").toFile());
        archiver.addFile(jarA.toFile(), "base/lib/a-1.0.jar");
        archiver.addFile(jarB.toFile(), "base/lib/b-1.0.jar");
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        String refA = "pkg:maven/org.x/a@1.0?type=jar";
        String refB = "pkg:maven/org.x/b@1.0?type=jar";
        var bomDepA = bom.getDependencies().stream()
                .filter(d -> refA.equals(d.getRef())).findFirst().orElse(null);
        assertNotNull(bomDepA);
        assertTrue(bomDepA.getDependencies().stream().anyMatch(d -> refB.equals(d.getRef())),
                "a should depend on b");
    }

    @Test
    void duplicateArtifactAtTwoLocationsMergesOccurrences() throws Exception {
        Path jarFile = createTestJar("jspecify-1.0.jar", "jspecify-content");
        Artifact artifact = createArtifact("org.jspecify", "jspecify", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(tempDir.resolve("test.zip").toFile());
        archiver.addFile(jarFile.toFile(), "base/lib/jspecify-1.0.jar");
        archiver.addFile(jarFile.toFile(), "base/web/console.war/WEB-INF/lib/jspecify-1.0.jar");
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        long libCount = bom.getComponents().stream()
                .filter(c -> c.getType() == Component.Type.LIBRARY && "jspecify".equals(c.getName()))
                .count();
        assertEquals(1, libCount, "same artifact should appear once");

        Component comp = findComponent(bom, Component.Type.LIBRARY, "jspecify");
        assertEquals(2, comp.getEvidence().getOccurrences().size(),
                "should have two occurrences");
    }

    @Test
    void unpackedWarNestedJarsBecomeLibraries() throws Exception {
        Path nestedJar = createTestJar("nested-lib-2.0.jar", "nested-content");
        Path htmlFile = createTestFile("index.html", "<html>hello</html>");

        Path warFile = tempDir.resolve("console-1.0.war");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(warFile))) {
            jos.putNextEntry(new JarEntry("WEB-INF/lib/nested-lib-2.0.jar"));
            jos.write(Files.readAllBytes(nestedJar));
            jos.closeEntry();
            jos.putNextEntry(new JarEntry("index.html"));
            jos.write(Files.readAllBytes(htmlFile));
            jos.closeEntry();
        }

        Artifact warArtifact = createArtifact("org.example", "console", "1.0", "war", warFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(warArtifact));

        MavenProject consoleProject = mock(MavenProject.class);
        when(consoleProject.getGroupId()).thenReturn("org.example");
        when(consoleProject.getArtifactId()).thenReturn("console");
        when(consoleProject.getVersion()).thenReturn("1.0");
        when(consoleProject.getPackaging()).thenReturn("war");

        Artifact nestedArtifact = createArtifact("org.nested", "nested-lib", "2.0",
                "jar", nestedJar.toFile());
        when(consoleProject.getArtifacts()).thenReturn(Set.of(nestedArtifact));
        when(session.getProjects()).thenReturn(List.of(consoleProject));

        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(tempDir.resolve("out.zip").toFile());
        archiver.addFile(nestedJar.toFile(),
                "base/web/console.war/WEB-INF/lib/nested-lib-2.0.jar");
        archiver.addFile(htmlFile.toFile(), "base/web/console.war/index.html");
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);

        Component war = findComponent(bom, Component.Type.LIBRARY, "console");
        assertNotNull(war, "WAR should be detected as LIBRARY");
        assertTrue(war.getPurl().contains("type=war"));
        assertEquals("web/console.war/",
                war.getEvidence().getOccurrences().get(0).getLocation(),
                "WAR occurrence should be the unpack location");

        assertNull(findComponent(bom, Component.Type.LIBRARY, "nested-lib"),
                "nested JAR should NOT appear as top-level component");
        assertNotNull(war.getComponents(), "WAR should have nested components");
        assertEquals(1, war.getComponents().size());
        Component nested = war.getComponents().get(0);
        assertEquals("nested-lib", nested.getName());
        assertEquals("pkg:maven/org.nested/nested-lib@2.0?type=jar", nested.getPurl());
        assertEquals("web/console.war/WEB-INF/lib/nested-lib-2.0.jar",
                nested.getEvidence().getOccurrences().get(0).getLocation(),
                "nested JAR occurrence should be relative to distribution root");

        Component html = findComponent(bom, Component.Type.FILE, "index.html");
        assertNull(html, "non-JAR nested files should not be FILE components");

        String warRef = war.getBomRef();
        var warDep = bom.getDependencies().stream()
                .filter(d -> warRef.equals(d.getRef())).findFirst().orElse(null);
        assertNotNull(warDep);
        assertTrue(warDep.getDependencies().stream()
                .anyMatch(d -> d.getRef().equals(nested.getBomRef())),
                "WAR should depend on nested-lib");
    }

    @Test
    void unpackedArtifactNestedJarIdentifiedByPomProperties() throws Exception {
        Path nestedJar = createJarWithPomProperties("jolokia-json-2.4.jar",
                "org.jolokia", "jolokia-json", "2.4", "jolokia-content");

        Path warFile = tempDir.resolve("external-1.0.war");
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(warFile))) {
            jos.putNextEntry(new JarEntry("WEB-INF/lib/jolokia-json-2.4.jar"));
            jos.write(Files.readAllBytes(nestedJar));
            jos.closeEntry();
        }

        Artifact warArtifact = createArtifact("org.ext", "external", "1.0",
                "war", warFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(warArtifact));

        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(tempDir.resolve("out.zip").toFile());
        archiver.addFile(nestedJar.toFile(),
                "base/web/external/WEB-INF/lib/jolokia-json-2.4.jar");
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);

        Component war = findComponent(bom, Component.Type.LIBRARY, "external");
        assertNotNull(war, "WAR should be detected as LIBRARY");
        assertEquals("web/external/",
                war.getEvidence().getOccurrences().get(0).getLocation(),
                "WAR occurrence should be the unpack location");

        assertNull(findComponent(bom, Component.Type.LIBRARY, "jolokia-json"),
                "nested JAR should NOT appear as top-level component");
        assertNotNull(war.getComponents(), "WAR should have nested components");
        assertEquals(1, war.getComponents().size());
        Component jolokia = war.getComponents().get(0);
        assertEquals("jolokia-json", jolokia.getName());
        assertEquals("pkg:maven/org.jolokia/jolokia-json@2.4?type=jar", jolokia.getPurl());
        assertEquals("web/external/WEB-INF/lib/jolokia-json-2.4.jar",
                jolokia.getEvidence().getOccurrences().get(0).getLocation(),
                "nested JAR occurrence should be relative to distribution root");

        String warRef = war.getBomRef();
        var warDep = bom.getDependencies().stream()
                .filter(d -> warRef.equals(d.getRef())).findFirst().orElse(null);
        assertNotNull(warDep);
        assertTrue(warDep.getDependencies().stream()
                .anyMatch(d -> d.getRef().equals(jolokia.getBomRef())),
                "WAR should depend on jolokia-json");
    }

    @Test
    void virtualFilesReportsOutputPath() {
        assertEquals(List.of("bom.cdx.json"), handler.getVirtualFiles());
    }

    @Test
    void virtualFilesEmptyWhenOutputExternal() {
        handler.setOutput("external");
        assertEquals(List.of(), handler.getVirtualFiles());
    }

    @Test
    void externalOutputWritesBomNextToArchive() throws Exception {
        handler.setOutput("external");
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("app.properties", "data");

        Path archivePath = tempDir.resolve("myapp-1.0-dist.zip");
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(archivePath.toFile());
        archiver.addFile(props.toFile(), "base/conf/app.properties");
        handler.finalizeArchiveCreation(archiver);
        archiver.createArchive();

        Path bomFile = tempDir.resolve("myapp-1.0-dist.zip.cdx.json");
        assertTrue(Files.exists(bomFile),
                "BOM should be written next to archive");
        String content = Files.readString(bomFile);
        assertTrue(content.contains("CycloneDX"));
        assertTrue(content.contains("SHA-256"),
                "external BOM should include archive hash");
    }

    @Test
    void outputAllWritesEmbeddedAndExternal() throws Exception {
        handler.setOutput("all");
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("both.properties", "data");

        Path archivePath = tempDir.resolve("both-test.zip");
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(archivePath.toFile());
        archiver.addFile(props.toFile(), "base/conf/both.properties");
        handler.finalizeArchiveCreation(archiver);
        archiver.createArchive();

        Path externalBom = tempDir.resolve("both-test.zip.cdx.json");
        assertTrue(Files.exists(externalBom),
                "external BOM should be written next to archive");
        String externalContent = Files.readString(externalBom);
        assertTrue(externalContent.contains("CycloneDX"));

        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(archivePath.toFile())) {
            var bomEntry = zf.stream()
                    .filter(e -> e.getName().endsWith(".cdx.json"))
                    .findFirst().orElse(null);
            assertNotNull(bomEntry, "BOM should also be embedded in the archive");
        }
    }

    @Test
    void virtualFilesReportedWhenOutputAll() {
        handler.setOutput("all");
        assertEquals(List.of("bom.cdx.json"), handler.getVirtualFiles());
    }

    @Test
    void mainComponentPurlIncludesArchiveType() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());
        Path props = createTestFile("data.txt", "hello");

        ZipArchiver archiver = buildArchiver("base/data.txt", props);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component main = bom.getMetadata().getComponent();
        assertTrue(main.getPurl().contains("type=zip"),
                "main PURL should include archive type: " + main.getPurl());
    }

    @Test
    void invalidOutputThrows() throws Exception {
        handler.setOutput("none");
        Path props = createTestFile("data.txt", "hello");

        ZipArchiver archiver = buildArchiver("base/data.txt", props);
        var ex = assertThrows(ArchiverException.class,
                () -> handler.finalizeArchiveCreation(archiver));
        assertTrue(ex.getMessage().contains("none"));
    }

    @Test
    void invalidFormatThrows() throws Exception {
        handler.setFormat("yaml");
        Path props = createTestFile("data.txt", "hello");

        ZipArchiver archiver = buildArchiver("base/data.txt", props);
        var ex = assertThrows(ArchiverException.class,
                () -> handler.finalizeArchiveCreation(archiver));
        assertTrue(ex.getMessage().contains("yaml"));
    }

    @Test
    void emptyArchiveProducesMinimalBom() throws Exception {
        handler.setOutput("external");
        when(project.getArtifacts()).thenReturn(Set.of());

        Path archivePath = tempDir.resolve("empty.zip");
        Path dummy = createTestFile("dummy.txt", "placeholder");
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(archivePath.toFile());
        archiver.addFile(dummy.toFile(), "base/dummy.txt");
        handler.finalizeArchiveCreation(archiver);
        archiver.createArchive();

        Path bomFile = tempDir.resolve("empty.zip.cdx.json");
        assertTrue(Files.exists(bomFile));
        String content = Files.readString(bomFile);
        assertTrue(content.contains("CycloneDX"));
        assertFalse(content.contains("\"type\" : \"library\""), "should have no library components");
    }

    @Test
    void licensesIncludedInBomComponents() throws Exception {
        Path jarFile = createTestJar("foo-1.0.jar", "hello");
        Artifact artifact = createArtifact("org.example", "foo", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        Model model = new Model();
        License license = new License();
        license.setName("The Apache Software License, Version 2.0");
        model.addLicense(license);
        when(effectiveModelResolver.resolveEffectiveModel("org.example", "foo", "1.0"))
                .thenReturn(model);

        ZipArchiver archiver = buildArchiver("base/lib/foo-1.0.jar", jarFile);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component lib = findComponent(bom, Component.Type.LIBRARY, "foo");
        assertNotNull(lib);
        assertNotNull(lib.getLicenseChoice(), "component should have license info");
        assertNotNull(lib.getLicenseChoice().getLicenses());
        assertFalse(lib.getLicenseChoice().getLicenses().isEmpty());
        assertEquals("Apache-2.0", lib.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void failOnMissingLicenseThrowsException() throws Exception {
        handler.setFailOnMissingLicense(true);
        Path jarFile = createTestJar("foo-1.0.jar", "hello");
        Artifact artifact = createArtifact("org.example", "foo", "1.0", "jar", jarFile.toFile());
        when(project.getArtifacts()).thenReturn(Set.of(artifact));

        Model projectModel = new Model();
        License projectLicense = new License();
        projectLicense.setName("Apache License, Version 2.0");
        projectModel.addLicense(projectLicense);
        when(effectiveModelResolver.resolveEffectiveModel("com.example", "test-app", "1.0"))
                .thenReturn(projectModel);

        Model artifactModel = new Model();
        when(effectiveModelResolver.resolveEffectiveModel("org.example", "foo", "1.0"))
                .thenReturn(artifactModel);

        ZipArchiver archiver = buildArchiver("base/lib/foo-1.0.jar", jarFile);
        var ex = assertThrows(ArchiverException.class,
                () -> handler.finalizeArchiveCreation(archiver));
        assertTrue(ex.getMessage().contains("org.example:foo:1.0"));
    }

    @Test
    void projectLicenseAppliedToMainComponent() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());

        Model projectModel = new Model();
        License projectLicense = new License();
        projectLicense.setName("Apache License, Version 2.0");
        projectModel.addLicense(projectLicense);
        when(effectiveModelResolver.resolveEffectiveModel("com.example", "test-app", "1.0"))
                .thenReturn(projectModel);

        handler.setOutput("external");
        Path archivePath = tempDir.resolve("main-lic.zip");
        Path dummy = createTestFile("dummy-lic.txt", "placeholder");
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(archivePath.toFile());
        archiver.addFile(dummy.toFile(), "base/dummy.txt");
        handler.finalizeArchiveCreation(archiver);
        archiver.createArchive();

        Path bomFile = tempDir.resolve("main-lic.zip.cdx.json");
        String json = Files.readString(bomFile);
        org.cyclonedx.parsers.JsonParser parser = new org.cyclonedx.parsers.JsonParser();
        Bom bom = parser.parse(json.getBytes(StandardCharsets.UTF_8));

        Component main = bom.getMetadata().getComponent();
        assertNotNull(main.getLicenseChoice(), "main component should have project license");
        assertEquals("Apache-2.0", main.getLicenseChoice().getLicenses().get(0).getId());
    }

    @Test
    void projectLicenseAppliedToFileComponents() throws Exception {
        when(project.getArtifacts()).thenReturn(Set.of());

        Model projectModel = new Model();
        License projectLicense = new License();
        projectLicense.setName("MIT License");
        projectModel.addLicense(projectLicense);
        when(effectiveModelResolver.resolveEffectiveModel("com.example", "test-app", "1.0"))
                .thenReturn(projectModel);

        Path props = createTestFile("app.properties", "key=value");
        ZipArchiver archiver = buildArchiver("base/conf/app.properties", props);
        handler.finalizeArchiveCreation(archiver);

        Bom bom = readBomFromArchiver(archiver);
        Component file = findComponent(bom, Component.Type.FILE, "app.properties");
        assertNotNull(file);
        assertNotNull(file.getLicenseChoice(), "file component should inherit project license");
        assertEquals("MIT", file.getLicenseChoice().getLicenses().get(0).getId());
    }

    // --- helpers ---

    private Path createJarWithPomProperties(String name, String groupId,
            String artifactId, String version,
            String content) throws Exception {
        Path jarPath = tempDir.resolve(name);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("data.txt"));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
            String propsPath = "META-INF/maven/" + groupId + "/" + artifactId
                    + "/pom.properties";
            jos.putNextEntry(new JarEntry(propsPath));
            String props = "groupId=" + groupId + "\n"
                    + "artifactId=" + artifactId + "\n"
                    + "version=" + version + "\n";
            jos.write(props.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createTestJar(String name, String content) throws Exception {
        Path jarPath = tempDir.resolve(name);
        try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jos.putNextEntry(new JarEntry("data.txt"));
            jos.write(content.getBytes(StandardCharsets.UTF_8));
            jos.closeEntry();
        }
        return jarPath;
    }

    private Path createTestFile(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private Artifact createArtifact(String groupId, String artifactId,
            String version, String type, File file) {
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, version, "compile", type, null,
                new DefaultArtifactHandler(type));
        artifact.setFile(file);
        return artifact;
    }

    private ZipArchiver buildArchiver(String archivePath, Path sourceFile) throws Exception {
        ZipArchiver archiver = new ZipArchiver();
        archiver.setDestFile(tempDir.resolve("test-archive.zip").toFile());
        archiver.addFile(sourceFile.toFile(), archivePath);
        return archiver;
    }

    private Bom readBomFromArchiver(ZipArchiver archiver) throws Exception {
        archiver.createArchive();
        File zipFile = archiver.getDestFile();
        try (java.util.zip.ZipFile zf = new java.util.zip.ZipFile(zipFile)) {
            var entry = zf.stream()
                    .filter(e -> e.getName().endsWith(".cdx.json") || e.getName().endsWith(".cdx.xml"))
                    .findFirst().orElse(null);
            if (entry == null)
                return null;
            String json = new String(zf.getInputStream(entry).readAllBytes(), StandardCharsets.UTF_8);
            org.cyclonedx.parsers.JsonParser parser = new org.cyclonedx.parsers.JsonParser();
            return parser.parse(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static Component findComponent(Bom bom, Component.Type type, String name) {
        if (bom == null || bom.getComponents() == null)
            return null;
        return bom.getComponents().stream()
                .filter(c -> c.getType() == type && name.equals(c.getName()))
                .findFirst().orElse(null);
    }

    private static DependencyCollectionContext mockCollectionContext(
            org.eclipse.aether.artifact.Artifact artifact, Dependency dependency) {
        DependencyCollectionContext ctx = mock(DependencyCollectionContext.class);
        lenient().when(ctx.getArtifact()).thenReturn(artifact);
        lenient().when(ctx.getDependency()).thenReturn(dependency);
        return ctx;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
