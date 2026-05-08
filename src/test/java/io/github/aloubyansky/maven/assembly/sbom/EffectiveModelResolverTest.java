package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.ModelBuilder;
import org.apache.maven.model.building.ModelBuildingResult;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link EffectiveModelResolver}.
 *
 * <p>
 * Each test exercises a specific aspect of the resolver's behavior:
 * successful model resolution with license metadata, caching semantics,
 * and graceful handling of resolution and model-building failures.
 * </p>
 *
 * <p>
 * The tests use Mockito to isolate the resolver from the Maven
 * repository infrastructure. The injected {@link ModelBuilder} and
 * {@link RepositorySystem} fields are set via reflection, mirroring the
 * pattern used in other test classes in this module.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class EffectiveModelResolverTest {

    private static final String GROUP_ID = "org.example";
    private static final String ARTIFACT_ID = "test-lib";
    private static final String VERSION = "1.0.0";

    private static final String ALT_GROUP_ID = "org.other";
    private static final String ALT_ARTIFACT_ID = "other-lib";
    private static final String ALT_VERSION = "2.0.0";

    @TempDir
    Path tempDir;

    @Mock
    ModelBuilder modelBuilder;

    @Mock
    RepositorySystem repoSystem;

    @Mock
    RepositorySystemSession repoSession;

    private EffectiveModelResolver resolver;

    /**
     * Creates a fresh {@link EffectiveModelResolver} instance, injects the
     * mocked {@link ModelBuilder} and {@link RepositorySystem} via
     * reflection, and initializes the resolver with an empty repository
     * list and the mocked session.
     */
    @BeforeEach
    void setUp() throws Exception {
        resolver = new EffectiveModelResolver();
        setField(resolver, "modelBuilder", modelBuilder);
        setField(resolver, "repoSystem", repoSystem);
        resolver.init(repoSession, List.of(), List.of());
    }

    /**
     * Verifies that when artifact resolution and model building both
     * succeed, the resolver returns the effective {@link Model} produced
     * by the {@link ModelBuilder}.
     *
     * <p>
     * The returned model is configured with a license entry to
     * confirm that inherited metadata is accessible through the
     * resolved model.
     * </p>
     */
    @Test
    void resolveEffectiveModel_returnsModelWithLicenses() throws Exception {
        Model expectedModel = createModelWithLicense("Apache-2.0");
        stubSuccessfulResolution(GROUP_ID, ARTIFACT_ID, VERSION, expectedModel);

        Model result = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNotNull(result, "resolved model should not be null");
        assertEquals(1, result.getLicenses().size(),
                "model should contain exactly one license");
        assertEquals("Apache-2.0", result.getLicenses().get(0).getName(),
                "license name should match the configured value");
    }

    /**
     * Verifies the caching behavior: when the same GAV coordinates are
     * resolved twice, the {@link ModelBuilder} is invoked only once.
     *
     * <p>
     * The second call should return the cached model without
     * triggering another artifact resolution or model-building
     * cycle.
     * </p>
     */
    @Test
    void resolveEffectiveModel_cachesPreviousResult() throws Exception {
        Model expectedModel = createModelWithLicense("MIT");
        stubSuccessfulResolution(GROUP_ID, ARTIFACT_ID, VERSION, expectedModel);

        Model first = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);
        Model second = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);

        assertSame(first, second, "second call should return the cached model instance");
        verify(modelBuilder, times(1)).build(any());
    }

    /**
     * Verifies that when {@link RepositorySystem#resolveArtifact} throws
     * an {@link ArtifactResolutionException}, the resolver returns
     * {@code null} instead of propagating the exception.
     *
     * <p>
     * This covers the case where the POM artifact is not available
     * in any configured repository.
     * </p>
     */
    @Test
    void resolveEffectiveModel_returnsNullOnResolutionFailure() throws Exception {
        ArtifactResult failedResult = new ArtifactResult(new ArtifactRequest());
        when(repoSystem.resolveArtifact(any(), any()))
                .thenThrow(new ArtifactResolutionException(List.of(failedResult),
                        "artifact not found"));

        Model result = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNull(result, "should return null when artifact resolution fails");
    }

    /**
     * Verifies that when the {@link ModelBuilder} throws a
     * {@link ModelBuildingException}, the resolver returns {@code null}
     * instead of propagating the exception.
     *
     * <p>
     * This covers the case where the POM file is available but
     * cannot be parsed or assembled into a valid effective model
     * (e.g., missing parent, malformed XML).
     * </p>
     */
    @Test
    void resolveEffectiveModel_returnsNullOnModelBuildingFailure() throws Exception {
        stubArtifactResolution(GROUP_ID, ARTIFACT_ID, VERSION);

        when(modelBuilder.build(any()))
                .thenThrow(new RuntimeException("simulated model building failure"));

        Model result = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNull(result, "should return null when model building fails");
    }

    /**
     * Verifies that the cache is keyed by the full GAV coordinates:
     * calling the resolver with two different GAVs should invoke the
     * {@link ModelBuilder} twice, once per unique coordinate set.
     */
    @Test
    void resolveEffectiveModel_differentCoordinatesNotCached() throws Exception {
        Model modelA = createModelWithLicense("Apache-2.0");
        Model modelB = createModelWithLicense("MIT");

        when(repoSystem.resolveArtifact(any(), any())).thenAnswer(invocation -> {
            ArtifactRequest req = invocation.getArgument(1);
            File pom = createPomFile(req.getArtifact().getArtifactId());
            ArtifactResult res = new ArtifactResult(req);
            res.setArtifact(req.getArtifact().setFile(pom));
            return res;
        });

        ModelBuildingResult buildResultA = mock(ModelBuildingResult.class);
        when(buildResultA.getEffectiveModel()).thenReturn(modelA);
        ModelBuildingResult buildResultB = mock(ModelBuildingResult.class);
        when(buildResultB.getEffectiveModel()).thenReturn(modelB);
        when(modelBuilder.build(any()))
                .thenReturn(buildResultA).thenReturn(buildResultB);

        Model resultA = resolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION);
        Model resultB = resolver.resolveEffectiveModel(ALT_GROUP_ID, ALT_ARTIFACT_ID, ALT_VERSION);

        assertNotNull(resultA, "first model should be resolved");
        assertNotNull(resultB, "second model should be resolved");
        assertNotSame(resultA, resultB,
                "different GAVs should produce different model instances");
        verify(modelBuilder, times(2)).build(any());
    }

    // --- helpers ---

    /**
     * Creates a Maven {@link Model} containing a single license with the
     * given name.
     *
     * @param licenseName the license name to set on the model
     * @return a model with one license entry
     */
    private Model createModelWithLicense(String licenseName) {
        Model model = new Model();
        License license = new License();
        license.setName(licenseName);
        model.addLicense(license);
        return model;
    }

    /**
     * Creates a minimal POM file in the temp directory for use as the
     * resolved artifact file.
     *
     * @param artifactId the artifactId to embed in the POM content
     * @return the created POM file
     */
    private File createPomFile(String artifactId) throws Exception {
        Path pomFile = tempDir.resolve(artifactId + ".pom");
        Files.writeString(pomFile,
                "<project><modelVersion>4.0.0</modelVersion>"
                        + "<groupId>org.example</groupId>"
                        + "<artifactId>" + artifactId + "</artifactId>"
                        + "<version>1.0.0</version></project>");
        return pomFile.toFile();
    }

    /**
     * Stubs the {@link RepositorySystem#resolveArtifact} call to return
     * an {@link ArtifactResult} whose artifact points to a temporary POM
     * file. Does not stub the {@link ModelBuilder}.
     *
     * @param groupId the expected groupId
     * @param artifactId the expected artifactId
     * @param version the expected version
     */
    private void stubArtifactResolution(String groupId, String artifactId,
            String version) throws Exception {
        File pomFile = createPomFile(artifactId);
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, "", "pom", version);
        org.eclipse.aether.artifact.Artifact withFile = artifact.setFile(pomFile);

        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        artifactResult.setArtifact(withFile);

        when(repoSystem.resolveArtifact(any(), any()))
                .thenReturn(artifactResult);
    }

    /**
     * Stubs both the artifact resolution and model building for a
     * complete successful resolution flow.
     *
     * @param groupId the expected groupId
     * @param artifactId the expected artifactId
     * @param version the expected version
     * @param model the effective model to return from the builder
     */
    private void stubSuccessfulResolution(String groupId, String artifactId,
            String version,
            Model model) throws Exception {
        File pomFile = createPomFile(artifactId);
        DefaultArtifact artifact = new DefaultArtifact(
                groupId, artifactId, "", "pom", version);
        org.eclipse.aether.artifact.Artifact withFile = artifact.setFile(pomFile);

        ArtifactResult artifactResult = new ArtifactResult(new ArtifactRequest());
        artifactResult.setArtifact(withFile);

        when(repoSystem.resolveArtifact(any(), any()))
                .thenReturn(artifactResult);

        ModelBuildingResult buildingResult = mock(ModelBuildingResult.class);
        when(buildingResult.getEffectiveModel()).thenReturn(model);

        when(modelBuilder.build(any())).thenReturn(buildingResult);
    }

    /**
     * Sets a private field on the target object via reflection, bypassing
     * the normal access controls.
     *
     * @param target the object whose field should be set
     * @param fieldName the name of the declared field
     * @param value the value to inject
     */
    private static void setField(Object target, String fieldName,
            Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
