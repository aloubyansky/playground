package io.github.aloubyansky.maven.assembly.sbom;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.util.List;

import org.apache.maven.model.License;
import org.apache.maven.model.Model;
import org.cyclonedx.model.LicenseChoice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link MavenLicenseResolver}.
 *
 * <p>
 * Each test constructs a Maven {@link Model} with specific license
 * declarations, mocks the {@link EffectiveModelResolver} to return that
 * model, and verifies that the resolver correctly maps Maven licenses
 * to CycloneDX {@link LicenseChoice} instances with the expected SPDX
 * identifiers (or raw name/URL fallbacks).
 * </p>
 *
 * <p>
 * The tests cover the full resolution strategy documented in
 * {@link MavenLicenseResolver}: SPDX resolution by name, fallback to
 * URL, raw license creation, missing-license handling (both lenient
 * and strict), and multi-license aggregation.
 * </p>
 */
@ExtendWith(MockitoExtension.class)
class MavenLicenseResolverTest {

    private static final String GROUP_ID = "org.example";
    private static final String ARTIFACT_ID = "test-lib";
    private static final String VERSION = "1.0.0";

    @Mock
    EffectiveModelResolver effectiveModelResolver;

    /**
     * Verifies that a well-known Apache license name is mapped to the
     * SPDX identifier "Apache-2.0" via the CycloneDX
     * {@link org.cyclonedx.util.LicenseResolver}.
     *
     * <p>
     * The name "The Apache Software License, Version 2.0" is a
     * commonly used variant that the CycloneDX resolver recognizes
     * and normalizes to the canonical SPDX ID.
     * </p>
     */
    @Test
    void resolveLicenses_apacheLicenseMappedToSpdx() {
        Model model = new Model();
        License license = new License();
        license.setName("The Apache Software License, Version 2.0");
        model.addLicense(license);

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNotNull(result, "LicenseChoice should not be null for a resolvable license");
        List<org.cyclonedx.model.License> licenses = result.getLicenses();
        assertNotNull(licenses, "license list should not be null");
        assertEquals(1, licenses.size(), "should contain exactly one license");
        assertEquals("Apache-2.0", licenses.get(0).getId(),
                "should resolve to SPDX ID Apache-2.0");
    }

    /**
     * Verifies that when the license name does not match any SPDX entry
     * but the URL does, the resolver falls back to URL-based resolution.
     *
     * <p>
     * The URL "https://opensource.org/licenses/MIT" is recognized by
     * the CycloneDX resolver and maps to the SPDX identifier "MIT".
     * </p>
     */
    @Test
    void resolveLicenses_urlFallbackWhenNameFails() {
        Model model = new Model();
        License license = new License();
        license.setName("My Custom License");
        license.setUrl("https://opensource.org/licenses/MIT");
        model.addLicense(license);

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNotNull(result, "LicenseChoice should not be null when URL resolves");
        List<org.cyclonedx.model.License> licenses = result.getLicenses();
        assertNotNull(licenses, "license list should not be null");
        assertEquals(1, licenses.size(), "should contain exactly one license");
        assertEquals("MIT", licenses.get(0).getId(),
                "should resolve to SPDX ID MIT via URL fallback");
    }

    /**
     * Verifies that when neither the license name nor the URL resolves
     * to an SPDX identifier, a raw {@link org.cyclonedx.model.License}
     * is created preserving the original name and URL without an SPDX id.
     */
    @Test
    void resolveLicenses_rawLicenseWhenNoSpdxMatch() {
        Model model = new Model();
        License license = new License();
        license.setName("Proprietary License XYZ");
        license.setUrl("https://example.com/license");
        model.addLicense(license);

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNotNull(result, "LicenseChoice should not be null for a raw license");
        List<org.cyclonedx.model.License> licenses = result.getLicenses();
        assertNotNull(licenses, "license list should not be null");
        assertEquals(1, licenses.size(), "should contain exactly one license");

        org.cyclonedx.model.License resolved = licenses.get(0);
        assertEquals("Proprietary License XYZ", resolved.getName(),
                "should preserve original license name");
        assertEquals("https://example.com/license", resolved.getUrl(),
                "should preserve original license URL");
        assertNull(resolved.getId(), "SPDX id should be null for unrecognized licenses");
    }

    /**
     * Verifies that when the effective model contains an empty licenses
     * list and {@code failOnMissingLicense} is {@code false}, the
     * resolver returns {@code null} rather than throwing.
     */
    @Test
    void resolveLicenses_returnsNullWhenNoLicenses() {
        Model model = new Model();
        // model has no licenses added — getLicenses() returns empty list

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNull(result, "should return null when model has no licenses and failOnMissingLicense is false");
    }

    /**
     * Verifies that when the effective model has no licenses and
     * {@code failOnMissingLicense} is {@code true}, an
     * {@link ArchiverException} is thrown with a message containing the
     * artifact's GAV coordinates.
     */
    @Test
    void resolveLicenses_failsWhenConfigured() {
        Model model = new Model();

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, true);

        LicenseResolutionException ex = assertThrows(LicenseResolutionException.class,
                () -> resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION),
                "should throw when failOnMissingLicense is true and model has no licenses");

        String expectedGav = GROUP_ID + ":" + ARTIFACT_ID + ":" + VERSION;
        assertTrue(ex.getMessage().contains(expectedGav),
                "exception message should contain the artifact GAV: " + ex.getMessage());
    }

    /**
     * Verifies that when a model declares multiple licenses, each is
     * independently resolved and the resulting {@link LicenseChoice}
     * contains all of them with their respective SPDX identifiers.
     */
    @Test
    void resolveLicenses_multipleLicensesCombined() {
        Model model = new Model();

        License apache = new License();
        apache.setName("Apache License, Version 2.0");
        model.addLicense(apache);

        License mit = new License();
        mit.setName("MIT License");
        model.addLicense(mit);

        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(model);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNotNull(result, "LicenseChoice should not be null for multi-license model");
        List<org.cyclonedx.model.License> licenses = result.getLicenses();
        assertNotNull(licenses, "license list should not be null");
        assertEquals(2, licenses.size(), "should contain exactly two licenses");

        assertTrue(licenses.stream().anyMatch(l -> "Apache-2.0".equals(l.getId())),
                "should contain Apache-2.0 SPDX ID");
        assertTrue(licenses.stream().anyMatch(l -> "MIT".equals(l.getId())),
                "should contain MIT SPDX ID");
    }

    /**
     * Verifies that when the {@link EffectiveModelResolver} returns
     * {@code null} (i.e., the POM could not be resolved) and
     * {@code failOnMissingLicense} is {@code false}, the resolver
     * returns {@code null} gracefully.
     */
    @Test
    void resolveLicenses_returnsNullWhenModelNotResolvable() {
        when(effectiveModelResolver.resolveEffectiveModel(GROUP_ID, ARTIFACT_ID, VERSION))
                .thenReturn(null);

        MavenLicenseResolver resolver = new MavenLicenseResolver(effectiveModelResolver, false);
        LicenseChoice result = resolver.resolveLicenses(GROUP_ID, ARTIFACT_ID, VERSION);

        assertNull(result,
                "should return null when effective model is not resolvable and failOnMissingLicense is false");
    }
}
