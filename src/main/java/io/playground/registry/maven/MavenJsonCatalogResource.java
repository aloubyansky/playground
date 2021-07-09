package io.playground.registry.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriInfo;

import io.playground.registry.PlatformRegistry;
import io.playground.registry.maven.provider.NonPlatformExtensionsProvider;
import io.playground.registry.maven.provider.PlatformExtensionsProvider;
import io.playground.registry.maven.provider.PlatformsProvider;
import io.playground.registry.maven.provider.RegistryDescriptorProvider;
import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.Constants;

@Path("maven")
public class MavenJsonCatalogResource {

	private static final String SUFFIX_MD5 = ".md5";

	private static final String SUFFIX_SHA1 = ".sha1";

	private static final String SNAPSHOT_SUFFIX = "-SNAPSHOT";

	private static final String MAVEN_METADATA_XML = "maven-metadata.xml";

	@Inject
	QerConfig qerConfig;

	@Inject
	PlatformRegistry registry;

	private List<ArtifactKind> artifactKinds;

	@PostConstruct
	public void init() {
		final PomContentProvider pomProvider = new PomContentProvider();
		final MavenMetadataContentProvider metadataProvider = new MavenMetadataContentProvider(registry);
		final MavenMetadataContentProvider quarkusVersionMetadataProvider = new MavenMetadataContentProvider(registry);

		artifactKinds = new ArrayList<>(4);

		// Non-platforms extensions
		artifactKinds.add(new ArtifactKind((coords) -> {
			return coords.getArtifactId().equals(qerConfig.nonPlatformExtensions());
		}, pomProvider, new NonPlatformExtensionsProvider(registry), quarkusVersionMetadataProvider));

		// Recommended platforms
		artifactKinds.add(new ArtifactKind((coords) -> {
			return coords.getArtifactId().equals(qerConfig.platforms());
		}, pomProvider, new PlatformsProvider(registry), quarkusVersionMetadataProvider));

		// Recommended platforms per Quarkus core version
		artifactKinds.add(new ArtifactKind((coords) -> {
			return coords.getArtifactId().equals(qerConfig.descriptor());
		}, pomProvider, new RegistryDescriptorProvider(), metadataProvider));

		artifactKinds.add(new ArtifactKind((coords) -> {
			return coords.getArtifactId().endsWith(Constants.PLATFORM_DESCRIPTOR_ARTIFACT_ID_SUFFIX);
		}, pomProvider, new PlatformExtensionsProvider(), metadataProvider));
	}

	@GET
	@Produces(MediaType.TEXT_PLAIN)
	@Path("{var:.+}")
	public String get(@PathParam("var") List<PathSegment> pathSegmentList, @Context UriInfo uriInfo) {

		// determine the coords of the requested artifact
		final ArtifactCoords parsedCoords = parseCoords(pathSegmentList);

		// make sure the groupId is recognized by this repo
		final String supportedGroupId = qerConfig.groupId();
		if (!parsedCoords.getGroupId().equals(supportedGroupId)) {
			throw artifactNotFoundException(parsedCoords);
		}

		final String checksumSuffix = getChecksumSuffix(pathSegmentList, parsedCoords);

		// get the matching supported artifact kind
		final ArtifactKind qerArtifactType = artifactKind(parsedCoords);
		if (parsedCoords.getType().startsWith(MAVEN_METADATA_XML)) {
			return resolve(uriInfo, parsedCoords, qerArtifactType.metadataProvider(), checksumSuffix);
		}
		if (parsedCoords.getType().startsWith("pom")) {
			return resolve(uriInfo, parsedCoords, qerArtifactType.pomProvider(), checksumSuffix);
		}
		final ArtifactContentProvider mainProvider = qerArtifactType.mainProvider();
		if (parsedCoords.getType().startsWith(mainProvider.getType())) {
			return resolve(uriInfo, parsedCoords, mainProvider, checksumSuffix);
		}
		throw artifactNotFoundException(parsedCoords);
	}

	private ArtifactKind artifactKind(ArtifactCoords coords) {
		for (ArtifactKind qerArtifactType : artifactKinds) {
			if (qerArtifactType.matches(coords)) {
				return qerArtifactType;
			}
		}
		throw artifactNotFoundException(coords);
	}

	private String resolve(UriInfo uriInfo, ArtifactCoords coords, ArtifactContentProvider contentProvider,
			String checksumSuffix) {
		final String artifactContent = contentProvider.artifactContent(coords, qerConfig, uriInfo);
		if (checksumSuffix != null) {
			// checksum
			try {
				if (SUFFIX_SHA1.equals(checksumSuffix)) {
					return HashUtils.sha1(artifactContent);
				}
				if (SUFFIX_MD5.equals(checksumSuffix)) {
					return HashUtils.md5(artifactContent);
				}
			} catch (IOException e) {
				throw new IllegalArgumentException("Failed to generate checksum for " + coords, e);
			}
			throw artifactNotFoundException(coords);
		}
		return artifactContent;
	}

	private static IllegalArgumentException artifactNotFoundException(ArtifactCoords coords) {
		return new IllegalArgumentException("Artifact " + coords + " is not found");
	}

	private static ArtifactCoords parseCoords(List<PathSegment> pathSegmentList) {
		if (pathSegmentList.isEmpty()) {
			throw new IllegalArgumentException("Coordinates are missing");
		}

		final String fileName = getFileName(pathSegmentList);
		final String version = pathSegmentList.get(pathSegmentList.size() - 2).getPath();
		final String artifactId = pathSegmentList.get(pathSegmentList.size() - 3).getPath();
		String classifier = "";
		final String type;
		
		if (fileName.startsWith(artifactId)) {

			int typeEnd = fileName.length();
			if (fileName.endsWith(SUFFIX_SHA1)) {
				typeEnd -= SUFFIX_SHA1.length();
			} else if (fileName.endsWith(SUFFIX_MD5)) {
				typeEnd -= SUFFIX_MD5.length();
			}
			int typeStart = fileName.lastIndexOf('.', typeEnd - 1) + 1;

			type = fileName.substring(typeStart, typeEnd);
			final boolean snapshot = version.endsWith(SNAPSHOT_SUFFIX);
			// if it's a snapshot version, in some cases the file name will contain the
			// actual -SNAPSHOT suffix,
			// in other cases the SNAPSHOT will be replaced with a timestamp+build number
			// expression
			// e.g. instead of artifactId-baseVersion-SNAPSHOT the file name will look like
			// artifactId-baseVersion-YYYYMMDD.HHMMSS-buildNumber
			final String baseVersion = snapshot ? version.substring(0, version.length() - SNAPSHOT_SUFFIX.length())
					: version;

			final int versionStart = artifactId.length() + 1;
			int versionEnd;
			if (snapshot) {
				if (fileName.regionMatches(versionStart, version, 0, version.length())) {
					// artifactId-version[-classifier].extensions
					versionEnd = versionStart + version.length();
				} else {
					final int firstDash = fileName.indexOf('-', versionStart + baseVersion.length() + 1);
					versionEnd = fileName.indexOf('.',
							firstDash < 0 ? versionStart + baseVersion.length() : firstDash + 1);
					final int lastDash;
					if(fileName.regionMatches(versionEnd - SNAPSHOT_SUFFIX.length(), SNAPSHOT_SUFFIX, 0, SNAPSHOT_SUFFIX.length())) {
						lastDash = fileName.lastIndexOf('-', versionEnd - SNAPSHOT_SUFFIX.length() - 1);
					} else {
						lastDash = fileName.lastIndexOf('-', versionEnd);
					}
					if (lastDash > firstDash) {
						versionEnd = lastDash;
					}
				}
			} else {
				versionEnd = versionStart + version.length();
			}

			if (fileName.charAt(versionEnd) == '-') {
				classifier = fileName.substring(versionEnd + 1, typeStart - 1);
			}
		} else if (fileName.startsWith(MAVEN_METADATA_XML)) {
			type = MAVEN_METADATA_XML;
		} else {
			throw new IllegalArgumentException(
					"Artifact file name " + fileName + " does not start with the artifactId " + artifactId);
		}

		final StringBuilder buf = new StringBuilder();
		buf.append(pathSegmentList.get(0).getPath());
		for (int i = 1; i < pathSegmentList.size() - 3; ++i) {
			buf.append('.').append(pathSegmentList.get(i).getPath());
		}

		return new ArtifactCoords(buf.toString(), artifactId, classifier, type, version);
	}

	static String getFileName(List<PathSegment> pathSegmentList) {
		return pathSegmentList.get(pathSegmentList.size() - 1).getPath();
	}

	private static String getChecksumSuffix(List<PathSegment> pathSegmentList, ArtifactCoords parsedCoords) {
		final String fileName = getFileName(pathSegmentList);
		return fileName.endsWith(parsedCoords.getType()) ? null
				: fileName.substring(fileName.lastIndexOf(parsedCoords.getType()) + parsedCoords.getType().length());
	}
}