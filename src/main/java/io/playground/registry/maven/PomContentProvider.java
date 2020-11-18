package io.playground.registry.maven;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.StringWriter;

import javax.ws.rs.core.UriInfo;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;

import io.quarkus.maven.ArtifactCoords;

public class PomContentProvider implements ArtifactContentProvider {

	private static final MavenXpp3Writer POM_WRITER = new MavenXpp3Writer();
	
	@Override
	public String artifactContent(ArtifactCoords coords, QerConfig qerConfig, UriInfo uriInfo) {
		final Model model = new Model();
		model.setModelVersion("4.0.0");
		model.setGroupId(coords.getGroupId());
		model.setArtifactId(coords.getArtifactId());
		model.setVersion(coords.getVersion());
		model.setPackaging("pom");

		/*
		final Repository repo = new Repository();
		repo.setId("quarkiverse-registry");
		repo.setName("Quarkiverse Extension Registry");
		repo.setUrl(getMavenRepoUrl(qerConfig, uriInfo));
		
		RepositoryPolicy policy = new RepositoryPolicy();
		policy.setEnabled(true);
		policy.setUpdatePolicy("always");
		repo.setSnapshots(policy);
		
		model.addRepository(repo);
		*/
		
		final StringWriter stringWriter = new StringWriter();
		try (BufferedWriter pomFileWriter = new BufferedWriter(stringWriter)) {
		    POM_WRITER.write(pomFileWriter, model);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to generate pom.xml for " + coords, e);
		}
		return stringWriter.getBuffer().toString();
	}

	@Override
	public String getType() {
		return "pom";
	}
}
