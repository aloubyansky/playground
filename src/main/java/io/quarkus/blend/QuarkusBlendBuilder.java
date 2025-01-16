package io.quarkus.blend;

import io.quarkus.bootstrap.BootstrapException;
import io.quarkus.bootstrap.app.AugmentResult;
import io.quarkus.bootstrap.app.CuratedApplication;
import io.quarkus.bootstrap.app.QuarkusBootstrap;
import io.quarkus.bootstrap.model.ApplicationModel;
import io.quarkus.bootstrap.resolver.AppModelResolverException;
import io.quarkus.bootstrap.resolver.BootstrapAppModelResolver;
import io.quarkus.bootstrap.resolver.maven.BootstrapMavenException;
import io.quarkus.bootstrap.resolver.maven.MavenArtifactResolver;
import io.quarkus.bootstrap.util.IoUtils;
import io.quarkus.bootstrap.workspace.ArtifactSources;
import io.quarkus.bootstrap.workspace.SourceDir;
import io.quarkus.bootstrap.workspace.WorkspaceModule;
import io.quarkus.bootstrap.workspace.WorkspaceModuleId;
import io.quarkus.maven.dependency.ArtifactCoords;
import io.quarkus.maven.dependency.ArtifactKey;
import io.quarkus.maven.dependency.Dependency;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Consumer;

public class QuarkusBlendBuilder {

    private final String DEFAULT_PLATFORM_GROUP_ID = "io.quarkus.platform";
    private final String DEFAULT_PLATFORM_ARTIFACT_ID = "quarkus-bom";

    private static ArtifactKey getKey(QuarkusPlatformMemberConfig member) {
        return ArtifactKey.ga(member.getGroupId(), member.getArtifactId());
    }

    private QuarkusPlatformMemberConfig coreConfig;
    private Map<ArtifactKey, QuarkusPlatformMemberConfig> platformMembers = new LinkedHashMap<>();
    private Path outputDir;
    private MavenArtifactResolver artifactResolver;
    private Properties buildProperties = new Properties();
    private Map<ArtifactKey, ArtifactDescriptorResult> bomDescriptors = Map.of();
    private List<Path> classesDirs = List.of();

    QuarkusBlendBuilder() {
        setDefaultBuildProperties();
    }

    private void setDefaultBuildProperties() {
        buildProperties.setProperty("quarkus.package.jar.type", "uber-jar");
    }

    public QuarkusBlend build() {
        Objects.requireNonNull(coreConfig, "The Quarkus version was not configured");
        Objects.requireNonNull(coreConfig.getVersion(), "The Quarkus version was not configured");
        return new QuarkusBlend(buildApplication().getResults());
    }

    private AugmentResult buildApplication() {
        final Path outputDir = getOutputDir();
        IoUtils.recursiveDelete(outputDir);
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        final QuarkusBootstrap.Builder builder = QuarkusBootstrap.builder()
                .setBaseClassLoader(Thread.currentThread().getContextClassLoader())
                .setExistingModel(resolveApplicationModel())
                .setApplicationRoot(classesDirs.isEmpty() ? outputDir : classesDirs.get(0))
                .setTargetDirectory(outputDir)
                .setIsolateDeployment(true)
                .setBuildSystemProperties(buildProperties);

        try (CuratedApplication curated = builder.build().bootstrap()) {
            return curated.createAugmentor().createProductionApplication();
        } catch (BootstrapException e) {
            throw new RuntimeException("Failed to build an application", e);
        }
    }

    private ApplicationModel resolveApplicationModel() {
        var appModule = initApplicationModule();
        final BootstrapAppModelResolver appModelResolver = new BootstrapAppModelResolver(getArtifactResolver())
                .setIncubatingModelResolver(true);
        try {
            return appModelResolver.resolveModel(appModule);
        } catch (AppModelResolverException e) {
            throw new RuntimeException(e);
        }
    }

    public QuarkusBlendBuilder addClasses(Path classesDir) {
        if(classesDirs.isEmpty()) {
            classesDirs = new ArrayList<>();
        } else if(classesDirs.size() == 1) {
            throw new RuntimeException("Only one classes directory is supported currently");
        }
        classesDirs.add(classesDir);
        return this;
    }

    public QuarkusBlendBuilder setOutputDir(Path outputDir) {
        this.outputDir = outputDir;
        return this;
    }

    public QuarkusBlendBuilder withQuarkus(Consumer<QuarkusPlatformMemberConfig> member) {
        coreConfig = newPlatformMember(DEFAULT_PLATFORM_ARTIFACT_ID, member);
        return this;
    }

    public QuarkusBlendBuilder withPlatformMember(String bomArtifactId, Consumer<QuarkusPlatformMemberConfig> member) {
        var memberConfig = newPlatformMember(bomArtifactId, member);
        platformMembers.put(getKey(memberConfig), memberConfig);
        return this;
    }

    private QuarkusPlatformMemberConfig newPlatformMember(String bomArtifactId, Consumer<QuarkusPlatformMemberConfig> member) {
        var memberConfig = new QuarkusPlatformMemberConfig();
        memberConfig.setGroupId(DEFAULT_PLATFORM_GROUP_ID);
        memberConfig.setArtifactId(bomArtifactId);
        member.accept(memberConfig);
        return memberConfig;
    }

    public QuarkusBlendBuilder setBuildProperty(String name, String value) {
        buildProperties.setProperty(name, value);
        return this;
    }

    public QuarkusBlendBuilder setArtifactResolver(MavenArtifactResolver artifactResolver) {
        this.artifactResolver = artifactResolver;
        return this;
    }

    private Path getOutputDir() {
        if(outputDir == null) {
            outputDir = Path.of("");
        }
        return outputDir;
    }

    private WorkspaceModule initApplicationModule() {
        var module = WorkspaceModule.builder()
                .setModuleId(WorkspaceModuleId.of("io.quarkus.blend", "quarkus-blend-app", "1.0"))
                .setBuildDir(getOutputDir());
        // import the BOMs
        importPlatformBoms(module);
        // add dependencies
        addDependencies(module);
        // add the classes directories
        addClassesDirs(module);
        return module.build();
    }

    private void addClassesDirs(WorkspaceModule.Mutable module) {
        if(classesDirs.isEmpty()) {
            return;
        }
        if(classesDirs.size() == 1) {
            var classesDir = classesDirs.get(0);
            module.addArtifactSources(ArtifactSources.main(SourceDir.of(classesDir, classesDir), SourceDir.of(classesDir, classesDir)));
        } else {
            throw new IllegalArgumentException("Only one classes directory is supported at the moment");
        }
    }

    private void importPlatformBoms(WorkspaceModule.Mutable module) {
        importPlatformBom(module, coreConfig);
        for(var bom : platformMembers.values()) {
            importPlatformBom(module, bom);
        }
    }

    private void importPlatformBom(WorkspaceModule.Mutable module, QuarkusPlatformMemberConfig bom) {
        module.addDependencyConstraint(Dependency.pomImport(bom.getGroupId(), bom.getArtifactId(), getMemberVersion(bom)));
    }

    private String getMemberVersion(QuarkusPlatformMemberConfig bom) {
        return bom.getVersion() == null ? coreConfig.getVersion() : bom.getVersion();
    }

    private void addDependencies(WorkspaceModule.Mutable module) {
        addDependencies(module, coreConfig);
        for(var bom : platformMembers.values()) {
            addDependencies(module, bom);
        }
    }

    private void addDependencies(WorkspaceModule.Mutable module, QuarkusPlatformMemberConfig member) {
        for(var ext : member.getExtensions()) {
            addDependency(module, member, ext);
        }
    }

    private void addDependency(WorkspaceModule.Mutable module, QuarkusPlatformMemberConfig member, QuarkusExtensionConfig extension) {
        module.addDependency(toModuleDependency(member, extension));
    }

    private Dependency toModuleDependency(QuarkusPlatformMemberConfig member, QuarkusExtensionConfig extension) {
        if(extension.getGroupId() == null) {
            var depConstr = getBomConstraint(member, extension).getArtifact();
            return Dependency.of(depConstr.getGroupId(), depConstr.getArtifactId(), depConstr.getVersion());
        }
        return extension.getVersion() == null ? Dependency.of(extension.getGroupId(), extension.getArtifactId())
                : Dependency.of(extension.getGroupId(), extension.getArtifactId(), extension.getVersion());
    }

    private org.eclipse.aether.graph.Dependency getBomConstraint(QuarkusPlatformMemberConfig member, QuarkusExtensionConfig extension) {
        var bomDescr = getBomDescriptor(member);
        if(bomDescr.getManagedDependencies() == null || bomDescr.getManagedDependencies().isEmpty()) {
            throw new RuntimeException(bomDescr.getArtifact() + " could not be resolved or does not include any dependency constraints");
        }
        for(var constr : bomDescr.getManagedDependencies()) {
            Artifact a = constr.getArtifact();
            if(a.getArtifactId().equals(extension.getArtifactId())) {
                return constr;
            }
        }
        throw new IllegalArgumentException("Failed to locate a dependency constraint for " + extension.getArtifactId() + " in " + bomDescr.getArtifact());
    }

    private ArtifactDescriptorResult getBomDescriptor(QuarkusPlatformMemberConfig member) {
        var memberKey = getKey(member);
        var bomDescr = bomDescriptors.get(memberKey);
        if(bomDescr == null) {
            var bomArtifact = new DefaultArtifact(member.getGroupId(), member.getArtifactId(), ArtifactCoords.TYPE_POM, getMemberVersion(member));
            try {
                bomDescr = getArtifactResolver().resolveDescriptor(bomArtifact);
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to resolve artifact descriptor for " + bomArtifact, e);
            }
            if(bomDescriptors.isEmpty()) {
                bomDescriptors = new HashMap<>();
            }
            bomDescriptors.put(memberKey, bomDescr);
        }
        return bomDescr;
    }

    private MavenArtifactResolver getArtifactResolver() {
        if(artifactResolver == null) {
            try {
                artifactResolver = MavenArtifactResolver.builder()
                        .setWorkspaceDiscovery(false)
                        .build();
            } catch (BootstrapMavenException e) {
                throw new RuntimeException("Failed to initialize the Maven artifact resolver", e);
            }
        }
        return artifactResolver;
    }
}
