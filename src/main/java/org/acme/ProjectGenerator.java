package org.acme;

import io.quarkus.bootstrap.resolver.maven.workspace.ModelUtils;
import io.quarkus.maven.dependency.ArtifactCoords;
import org.apache.maven.model.Build;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.PluginManagement;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class ProjectGenerator {

    private static final String DEFAULT_PACKAGE = "org.acme";

    private static final String PLATFORM_GROUP_ID = "io.quarkus.platform";

    public static ProjectGenerator newInstance() {
        return new ProjectGenerator();
    }

    private boolean used;
    private Path projectDir;
    private boolean regenerate;
    private String defaultPackage = DEFAULT_PACKAGE;
    private List<ClassBuilder> classBuidlers = new ArrayList<>();
    private String quarkusVersion;
    private List<ArtifactCoords> quarkusExtensions = new ArrayList<>();
    private Properties applicationProps;

    private ProjectGenerator() {
    }

    public ProjectGenerator setProjectDirectory(Path projectDir) {
        ensureNotUsed();
        this.projectDir = projectDir;
        return this;
    }

    public ProjectGenerator setRegenerate(boolean regenerate) {
        this.regenerate = regenerate;
        return this;
    }

    public ProjectGenerator setQuarkusVersion(String quarkusVersion) {
        this.quarkusVersion = quarkusVersion;
        return this;
    }

    public ProjectGenerator addQuarkusExtension(String artifactId) {
        quarkusExtensions.add(ArtifactCoords.jar("io.quarkus", artifactId, null));
        return this;
    }

    public ProjectGenerator setApplicationProperty(String name, String value) {
        if(applicationProps == null) {
            applicationProps = new Properties();
        }
        applicationProps.setProperty(name, value);
        return this;
    }

    public void generate(ClassGenerator clsGen) throws Exception {
        ensureNotUsed();
        used = true;

        createProjectDir();

        var ctx = new ContextImpl();
        clsGen.generate(ctx);

        var srcDir = projectDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(srcDir);
        for(var cb : classBuidlers) {
            cb.generate(srcDir);
        }

        if(applicationProps != null) {
            var resourcesDir = projectDir.resolve("src").resolve("main").resolve("resources");
            Files.createDirectories(resourcesDir);
            try(BufferedWriter writer = Files.newBufferedWriter(resourcesDir.resolve("application.properties"))) {
                applicationProps.store(writer, "Application configuration");
            }
        }

        generatePom();
    }

    private void generatePom() throws IOException {
        var model = new Model();
        model.setModelVersion("4.0.0");
        model.setPackaging("jar");
        model.setGroupId(defaultPackage);
        model.setArtifactId("application");
        model.setVersion("1.0-SNAPSHOT");

        var projectProps = new Properties();
        model.setProperties(projectProps);
        projectProps.setProperty("project.build.sourceEncoding", "UTF-8");
        projectProps.setProperty("project.reporting.outputEncoding", "UTF-8");
        projectProps.setProperty("maven.compiler.release", "17");

        var build = new Build();
        model.setBuild(build);
        var pluginManagement = new PluginManagement();
        build.setPluginManagement(pluginManagement);

        projectProps.setProperty("compiler-plugin.version", "3.11.0");
        var plugin = new Plugin();
        plugin.setArtifactId("maven-compiler-plugin");
        plugin.setVersion("${compiler-plugin.version}");
        var config = new Xpp3Dom("configuration");
        plugin.setConfiguration(config);
        var compilerArgs = new Xpp3Dom("compilerArgs");
        config.addChild(compilerArgs);
        var arg = new Xpp3Dom("arg");
        compilerArgs.addChild(arg);
        arg.setValue("-parameters");

        pluginManagement.addPlugin(plugin);

        plugin = new Plugin();
        plugin.setGroupId(PLATFORM_GROUP_ID);
        plugin.setArtifactId("quarkus-maven-plugin");
        plugin.setVersion(quarkusVersion);
        plugin.setExtensions(true);
        build.addPlugin(plugin);
        var exec = new PluginExecution();
        exec.addGoal("build");
        exec.addGoal("generate-code");
        exec.addGoal("generate-code-tests");
        plugin.addExecution(exec);

        var depMan = new DependencyManagement();
        model.setDependencyManagement(depMan);
        var dep = new Dependency();
        dep.setGroupId(PLATFORM_GROUP_ID);
        dep.setArtifactId("quarkus-bom");
        dep.setVersion(quarkusVersion);
        dep.setScope("import");
        dep.setType("pom");
        depMan.addDependency(dep);

        for(var e : quarkusExtensions) {
            dep = new Dependency();
            dep.setGroupId(e.getGroupId());
            dep.setArtifactId(e.getArtifactId());
            if(!e.getClassifier().isEmpty()) {
                dep.setClassifier(e.getClassifier());
            }
            if(e.getVersion() != null) {
                dep.setVersion(e.getVersion());
            }
            model.addDependency(dep);
        }

        ModelUtils.persistModel(projectDir.resolve("pom.xml"), model);
    }

    private class ContextImpl implements Context {

        @Override
        public ClassBuilder newClassBuilder() {
            var cb = new ClassBuilder();
            cb.setPackageName(defaultPackage);
            classBuidlers.add(cb);
            return cb;
        }
    }

    private void createProjectDir() throws IOException {
        if(projectDir == null) {
            throw new IllegalArgumentException("Project directory has not been initialized");
        }
        if(Files.exists(projectDir)) {
            if(!Files.isDirectory(projectDir)) {
                throw new IllegalArgumentException(projectDir + " is not a directory");
            }
            if(regenerate) {
                try(DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir)) {
                    for(Path p : stream) {
                        Files.walkFileTree(p, new SimpleFileVisitor<>() {
                            @Override
                            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                    throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }

                            @Override
                            public FileVisitResult postVisitDirectory(Path file, IOException e)
                                    throws IOException {
                                Files.delete(file);
                                return FileVisitResult.CONTINUE;
                            }
                        });
                    }
                }
            } else {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(projectDir)) {
                    if (stream.iterator().hasNext()) {
                        throw new IllegalArgumentException(projectDir + " is not empty");
                    }
                }
            }
        }
        Files.createDirectories(projectDir);
    }

    private void ensureNotUsed() {
        if(used) {
            throw new RuntimeException("This generator instance has already been used");
        }
    }
}
