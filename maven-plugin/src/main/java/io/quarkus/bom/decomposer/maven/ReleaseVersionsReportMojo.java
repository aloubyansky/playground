package io.quarkus.bom.decomposer.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import io.quarkus.bom.decomposer.BomDecomposer;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator.HtmlWriterBuilder;
import io.quarkus.bom.decomposer.DecomposedBomReleasesLogger;

@Mojo(name = "report-release-versions")
public class ReleaseVersionsReportMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(property = "htmlReport", defaultValue = "true")
    protected boolean htmlReport = true;

    @Parameter(defaultValue = "${reportAllReleases}")
    protected boolean reportAllReleases;

    @Parameter(defaultValue = "${logReport}")
    protected DecomposedBomReleasesLogger.Level logReport;

    @Parameter(defaultValue = "${onConflict}")
    protected DecomposedBomReleasesLogger.Level onConflict;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			decompose();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to analyze managed dependencies of " + project.getArtifact(), e);
		}
	}

	private void decompose() throws Exception {
		final MojoMessageWriter msgWriter = new MojoMessageWriter(getLog());
		final DecomposedBom decomposedBom = BomDecomposer.config()
				.logger(msgWriter)
				.debug()
				.bomArtifact(project.getGroupId(), project.getArtifactId(), project.getVersion()).enableUpdateChecks()
				.decompose();

		if (htmlReport) {
			final HtmlWriterBuilder htmlWriter = DecomposedBomHtmlReportGenerator
					.builder("target/managed-releases.html");
			if (!reportAllReleases) {
				htmlWriter.skipOriginsWithSingleRelease();
			}
			decomposedBom.visit(htmlWriter.build());
		}

		if (logReport != null || onConflict != null) {
			final DecomposedBomReleasesLogger.Config loggerConfig = DecomposedBomReleasesLogger.config(!reportAllReleases);
			if(logReport != null) {
				loggerConfig.defaultLogLevel(logReport);
			}
			if(onConflict != null) {
				loggerConfig.conflictLogLevel(onConflict);
			}
			decomposedBom.visit(loggerConfig.logger(msgWriter).build());
		}
	}
}
