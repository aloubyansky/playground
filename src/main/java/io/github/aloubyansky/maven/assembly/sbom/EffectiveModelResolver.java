package io.github.aloubyansky.maven.assembly.sbom;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.Repository;
import org.apache.maven.model.building.DefaultModelBuildingRequest;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelSource;
import org.apache.maven.model.resolution.InvalidRepositoryException;
import org.apache.maven.model.resolution.ModelResolver;
import org.apache.maven.model.resolution.UnresolvableModelException;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves effective POM models for Maven artifacts, providing access to
 * inherited metadata such as licenses and dependency management.
 *
 * <p>
 * Resolved models are cached so that repeated queries for the same
 * artifact incur only a single model-building cost.
 * </p>
 *
 * <p>
 * The resolver must be {@linkplain #init initialized} with a repository
 * session before use.
 * </p>
 */
@Named
@Singleton
public class EffectiveModelResolver {

    private static final Logger log = LoggerFactory.getLogger(EffectiveModelResolver.class);

    @Inject
    private ModelBuilder modelBuilder;

    @Inject
    private RepositorySystem repoSystem;

    private RepositorySystemSession repoSession;
    private List<RemoteRepository> remoteRepositories;
    private Map<ArtifactCoords, Model> reactorModels;
    private final Map<ArtifactCoords, Model> cache = new ConcurrentHashMap<>();

    /**
     * Initializes the resolver for the current Maven session context.
     *
     * <p>
     * Must be called once before any calls to
     * {@link #resolveEffectiveModel}. Clears any cached models from
     * a previous invocation.
     * </p>
     *
     * @param repoSession the repository system session
     * @param remoteRepositories the remote repositories to search
     * @param reactorProjects the reactor projects from the current session
     */
    public void init(RepositorySystemSession repoSession,
            List<RemoteRepository> remoteRepositories,
            List<MavenProject> reactorProjects) {
        this.repoSession = repoSession;
        this.remoteRepositories = remoteRepositories;
        this.reactorModels = indexReactorModels(reactorProjects);
        cache.clear();
    }

    /**
     * Indexes reactor projects by their artifact coordinates.
     */
    private static Map<ArtifactCoords, Model> indexReactorModels(List<MavenProject> projects) {
        if (projects == null || projects.isEmpty()) {
            return Map.of();
        }
        Map<ArtifactCoords, Model> index = new HashMap<>(projects.size());
        for (MavenProject p : projects) {
            index.put(ArtifactCoords.of(p), p.getModel());
        }
        return index;
    }

    /**
     * Resolves the effective POM model for the given Maven artifact
     * coordinates.
     *
     * <p>
     * Results are cached by artifact id. Subsequent calls with the
     * same coordinates return the cached model without rebuilding.
     * </p>
     *
     * @param groupId the Maven groupId
     * @param artifactId the Maven artifactId
     * @param version the artifact version
     * @return the effective model, or {@code null} if the POM cannot
     *         be resolved or built
     */
    public Model resolveEffectiveModel(String groupId, String artifactId, String version) {
        ArtifactCoords id = ArtifactCoords.of(groupId, artifactId, version);
        Model cached = cache.get(id);
        if (cached != null) {
            return cached;
        }

        Model model = reactorModels.get(id);
        if (model == null) {
            File pomFile = resolvePomFile(groupId, artifactId, version);
            if (pomFile != null) {
                model = buildEffectiveModel(pomFile, id.toGav());
            }
        }
        if (model != null) {
            cache.put(id, model);
        }
        return model;
    }

    /**
     * Resolves the POM file for the given artifact from the Maven repository.
     *
     * @return the resolved POM file, or {@code null} if resolution fails
     */
    private File resolvePomFile(String groupId, String artifactId, String version) {
        try {
            DefaultArtifact pomArtifact = new DefaultArtifact(
                    groupId, artifactId, "", "pom", version);
            ArtifactRequest request = new ArtifactRequest(
                    pomArtifact, remoteRepositories, null);
            ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
            return result.getArtifact().getFile();
        } catch (Exception e) {
            log.debug("Failed to resolve POM for {}:{}:{}", groupId, artifactId, version, e);
            return null;
        }
    }

    /**
     * Builds the effective model from the given POM file.
     *
     * @param pomFile the POM file to build from
     * @param gav the GAV string for logging
     * @return the effective model, or {@code null} if building fails
     */
    private Model buildEffectiveModel(File pomFile, String gav) {
        try {
            DefaultModelBuildingRequest request = new DefaultModelBuildingRequest();
            request.setModelSource(new FileModelSource(pomFile));
            request.setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL);
            request.setProcessPlugins(false);
            Properties sysProps = new Properties();
            sysProps.putAll(System.getProperties());
            request.setSystemProperties(sysProps);
            request.setModelResolver(new LocalRepoModelResolver());
            return modelBuilder.build(request).getEffectiveModel();
        } catch (Exception e) {
            log.warn("Failed to build effective model for {}", gav, e);
            return null;
        }
    }

    /**
     * A {@link ModelResolver} that resolves parent and imported POM
     * models from the Maven repository.
     *
     * <p>
     * POM-declared repositories are intentionally ignored; the
     * resolver relies on the session's configured repositories.
     * </p>
     */
    private class LocalRepoModelResolver implements ModelResolver {

        private final List<RemoteRepository> repositories;

        LocalRepoModelResolver() {
            this.repositories = new ArrayList<>(remoteRepositories);
        }

        private LocalRepoModelResolver(List<RemoteRepository> repositories) {
            this.repositories = new ArrayList<>(repositories);
        }

        @Override
        public ModelSource resolveModel(String groupId, String artifactId, String version)
                throws UnresolvableModelException {
            try {
                DefaultArtifact pomArtifact = new DefaultArtifact(
                        groupId, artifactId, "", "pom", version);
                ArtifactRequest request = new ArtifactRequest(
                        pomArtifact, repositories, null);
                ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
                return new FileModelSource(result.getArtifact().getFile());
            } catch (Exception e) {
                throw new UnresolvableModelException(
                        "Failed to resolve POM: " + groupId + ":" + artifactId + ":" + version,
                        groupId, artifactId, version, e);
            }
        }

        @Override
        public ModelSource resolveModel(Parent parent) throws UnresolvableModelException {
            return resolveModel(parent.getGroupId(), parent.getArtifactId(), parent.getVersion());
        }

        @Override
        public ModelSource resolveModel(org.apache.maven.model.Dependency dependency)
                throws UnresolvableModelException {
            return resolveModel(
                    dependency.getGroupId(), dependency.getArtifactId(), dependency.getVersion());
        }

        @Override
        public void addRepository(Repository repository) throws InvalidRepositoryException {
            addRepository(repository, false);
        }

        @Override
        public void addRepository(Repository repository, boolean replace)
                throws InvalidRepositoryException {
        }

        @Override
        public ModelResolver newCopy() {
            return new LocalRepoModelResolver(repositories);
        }
    }
}
