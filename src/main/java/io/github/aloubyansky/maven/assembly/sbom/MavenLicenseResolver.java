package io.github.aloubyansky.maven.assembly.sbom;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.model.Model;
import org.cyclonedx.model.License;
import org.cyclonedx.model.LicenseChoice;
import org.cyclonedx.util.LicenseResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves CycloneDX {@link LicenseChoice} for Maven artifacts by reading
 * license declarations from each artifact's effective POM and mapping them
 * to SPDX license identifiers.
 *
 * <p>
 * The resolution strategy for each Maven license entry is:
 * </p>
 * <ol>
 * <li>Try resolving the license {@linkplain org.apache.maven.model.License#getName() name}
 * via the CycloneDX {@link LicenseResolver} (which attempts SPDX ID match,
 * name match, URL match, and fuzzy match against a curated mapping)</li>
 * <li>If the name does not resolve and a URL is available, try resolving the
 * {@linkplain org.apache.maven.model.License#getUrl() URL}</li>
 * <li>If neither resolves to an SPDX identifier, create a raw
 * {@link License} preserving the original name and URL</li>
 * </ol>
 *
 * <p>
 * Behavior when an artifact has no license information is controlled by the
 * {@code failOnMissingLicense} flag: when {@code true}, a
 * {@link LicenseResolutionException} is thrown; when {@code false} (default),
 * a warning is logged and {@code null} is returned.
 * </p>
 */
class MavenLicenseResolver {

    private static final Logger log = LoggerFactory.getLogger(MavenLicenseResolver.class);

    private final EffectiveModelResolver modelResolver;
    private final boolean failOnMissingLicense;
    private final Map<ArtifactCoords, LicenseChoice> cache = new HashMap<>();

    /**
     * Creates a license resolver backed by the given model resolver.
     *
     * @param modelResolver resolves effective POM models for artifacts
     * @param failOnMissingLicense if {@code true}, throws on artifacts with
     *        no license information; if {@code false},
     *        logs a warning and returns {@code null}
     */
    MavenLicenseResolver(EffectiveModelResolver modelResolver, boolean failOnMissingLicense) {
        this.modelResolver = modelResolver;
        this.failOnMissingLicense = failOnMissingLicense;
    }

    /**
     * Resolves the license information for the given Maven artifact.
     *
     * <p>
     * Builds the artifact's effective POM model, extracts its
     * {@code <licenses>} declarations, and maps each to a CycloneDX
     * {@link License} with an SPDX identifier where possible.
     * </p>
     *
     * @param groupId the Maven groupId
     * @param artifactId the Maven artifactId
     * @param version the artifact version
     * @return the resolved license information, or {@code null} if no
     *         licenses are declared in the effective model
     * @throws LicenseResolutionException if {@code failOnMissingLicense} is
     *         {@code true} and no license information is available
     */
    LicenseChoice resolveLicenses(String groupId, String artifactId, String version) {
        ArtifactCoords id = ArtifactCoords.of(groupId, artifactId, version);
        if (cache.containsKey(id)) {
            return cache.get(id);
        }

        LicenseChoice result = doResolveLicenses(id, groupId, artifactId, version);
        cache.put(id, result);
        return result;
    }

    /**
     * Performs the actual license resolution without caching.
     */
    private LicenseChoice doResolveLicenses(ArtifactCoords id,
            String groupId, String artifactId, String version) {
        Model model = modelResolver.resolveEffectiveModel(groupId, artifactId, version);
        if (model == null) {
            return handleMissingLicenses(groupId, artifactId, version,
                    "effective model could not be resolved");
        }

        List<org.apache.maven.model.License> mavenLicenses = model.getLicenses();
        if (mavenLicenses == null || mavenLicenses.isEmpty()) {
            return handleMissingLicenses(groupId, artifactId, version,
                    "no <licenses> declared in the effective POM");
        }

        return mapLicenses(mavenLicenses);
    }

    /**
     * Maps a list of Maven license declarations to a CycloneDX
     * {@link LicenseChoice} containing one {@link License} per entry.
     *
     * <p>
     * Each Maven license is resolved to an SPDX identifier via the
     * CycloneDX {@link LicenseResolver}. If SPDX resolution fails,
     * a raw license with the original name and URL is created.
     * </p>
     *
     * @param mavenLicenses the license entries from the effective POM
     * @return the combined license choice
     */
    private LicenseChoice mapLicenses(List<org.apache.maven.model.License> mavenLicenses) {
        LicenseChoice result = new LicenseChoice();
        for (org.apache.maven.model.License mavenLicense : mavenLicenses) {
            License resolved = resolveToSpdx(mavenLicense);
            if (resolved != null) {
                result.addLicense(resolved);
            } else {
                result.addLicense(createRawLicense(mavenLicense));
            }
        }
        return result;
    }

    /**
     * Attempts to resolve a single Maven license to a CycloneDX
     * {@link License} with an SPDX identifier, trying the license
     * name first and then the URL.
     *
     * @param mavenLicense the Maven license entry
     * @return a license with an SPDX {@code id} set, or {@code null}
     *         if no SPDX match was found
     */
    private License resolveToSpdx(org.apache.maven.model.License mavenLicense) {
        License resolved = tryResolve(mavenLicense.getName());
        if (resolved != null) {
            return resolved;
        }
        return tryResolve(mavenLicense.getUrl());
    }

    /**
     * Attempts to resolve the given string (a license name or URL) to a
     * CycloneDX {@link License} with an SPDX identifier.
     *
     * @param licenseString the string to resolve, or {@code null}
     * @return the resolved license, or {@code null} if the string is
     *         {@code null}, blank, or does not match any SPDX entry
     */
    private License tryResolve(String licenseString) {
        if (licenseString == null || licenseString.isBlank()) {
            return null;
        }
        LicenseChoice choice = LicenseResolver.resolve(licenseString, false);
        if (choice == null) {
            return null;
        }
        return extractSpdxLicense(choice);
    }

    /**
     * Extracts a single {@link License} with an SPDX {@code id} from the
     * resolved {@link LicenseChoice}.
     *
     * <p>
     * The CycloneDX {@link LicenseResolver} may return either a license
     * list (with the {@code id} field set) or an expression. This method
     * handles both forms, preferring the license list. If the result is an
     * expression, a license is created with the expression value as the
     * {@code id}.
     * </p>
     *
     * @param choice the resolved license choice
     * @return a license with an SPDX id, or {@code null} if the choice
     *         contains no usable license information
     */
    private License extractSpdxLicense(LicenseChoice choice) {
        if (choice.getLicenses() != null && !choice.getLicenses().isEmpty()) {
            License license = choice.getLicenses().get(0);
            if (license.getId() != null) {
                return license;
            }
        }
        if (choice.getExpression() != null && choice.getExpression().getValue() != null) {
            License license = new License();
            license.setId(choice.getExpression().getValue());
            return license;
        }
        return null;
    }

    /**
     * Creates a raw CycloneDX {@link License} from a Maven license entry
     * when SPDX resolution fails, preserving the original name and URL.
     *
     * @param mavenLicense the Maven license entry
     * @return a license with name and/or URL set (no SPDX id)
     */
    private License createRawLicense(org.apache.maven.model.License mavenLicense) {
        License license = new License();
        if (mavenLicense.getName() != null) {
            license.setName(mavenLicense.getName().trim());
        }
        if (mavenLicense.getUrl() != null) {
            license.setUrl(mavenLicense.getUrl().trim());
        }
        return license;
    }

    /**
     * Handles the case when no license information is available for an
     * artifact, either by throwing or logging depending on configuration.
     *
     * @param groupId the artifact groupId
     * @param artifactId the artifact artifactId
     * @param version the artifact version
     * @param reason a human-readable explanation of why licenses are missing
     * @return always {@code null} (when not throwing)
     * @throws LicenseResolutionException if {@code failOnMissingLicense} is {@code true}
     */
    private LicenseChoice handleMissingLicenses(String groupId, String artifactId,
            String version, String reason) {
        String gav = ArtifactCoords.of(groupId, artifactId, version).toGav();
        if (failOnMissingLicense) {
            throw new LicenseResolutionException(
                    "No license information for " + gav + ": " + reason);
        }
        log.warn("No license information for {}: {}", gav, reason);
        return null;
    }
}
