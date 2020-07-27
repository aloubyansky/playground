package io.quarkus.bom.decomposer.maven;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.Artifact;
import io.quarkus.bom.PomSource;
import io.quarkus.bom.decomposer.BomDecomposerException;
import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.DecomposedBomHtmlReportGenerator;
import io.quarkus.bom.decomposer.PomUtils;
import io.quarkus.bom.diff.BomDiff;
import io.quarkus.bom.diff.HtmlBomDiffReportGenerator;
import io.quarkus.bom.platform.PlatformBomComposer;
import io.quarkus.bom.platform.PlatformBomConfig;
import io.quarkus.bom.platform.ReportIndexPageGenerator;

@Mojo(name = "generate-platform-bom", defaultPhase = LifecyclePhase.PROCESS_RESOURCES, requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class GeneratePlatformBomMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter(defaultValue = "${skipPlatformBom}")
    protected boolean skip;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(skip) {
			return;
		}
		try {
			doExecute();
		} catch (Exception e) {
			throw new MojoExecutionException("Failed to analyze managed dependencies of " + project.getArtifact(), e);
		}
	}

	private void doExecute() throws Exception {
		//final MojoMessageWriter msgWriter = new MojoMessageWriter(getLog());

		final Path outputDir = Paths.get("target").resolve("boms");

		//PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.of(new DefaultArtifact(project.getArtifact().getGroupId(), project.getArtifact().getArtifactId(), "", "pom", project.getArtifact().getVersion())));
		PlatformBomConfig config = PlatformBomConfig.forPom(PomSource.of(project.getFile().toPath()));

		try (ReportIndexPageGenerator index = new ReportIndexPageGenerator(outputDir.resolve("index.html"))) {
			final PlatformBomComposer bomComposer = new PlatformBomComposer(config);
			final DecomposedBom generatedBom = bomComposer.platformBom();

			report(bomComposer.originalPlatformBom(), generatedBom, outputDir, index);
			final File generatedPom = outputDir.resolve(bomDirName(generatedBom.bomArtifact())).resolve("pom.xml").toFile();
			if(!generatedPom.exists()) {
				throw new MojoExecutionException("Failed to locate the generated platform pom.xml at " + generatedPom);
			} else {
				System.out.println("GENERATED PLATFORM BOM " + generatedBom);
			}
			project.setPomFile(generatedPom);

			for (DecomposedBom importedBom : bomComposer.upgradedImportedBoms()) {
				report(bomComposer.originalImportedBom(importedBom.bomArtifact()), importedBom, outputDir, index);
			}
		}
	}

	private static void report(DecomposedBom originalBom, DecomposedBom generatedBom, Path outputDir, ReportIndexPageGenerator index)
			throws IOException, BomDecomposerException {
		outputDir = outputDir.resolve(bomDirName(generatedBom.bomArtifact()));
		final Path platformBomXml = outputDir.resolve("pom.xml");
		PomUtils.toPom(generatedBom, platformBomXml);

		final BomDiff.Config config = BomDiff.config();
		if(generatedBom.bomResolver().isResolved()) {
			config.compare(generatedBom.bomResolver().pomPath());
		} else {
			config.compare(generatedBom.bomArtifact());
		}
		final BomDiff bomDiff = config.to(platformBomXml);

		final Path diffFile = outputDir.resolve("diff.html");
		HtmlBomDiffReportGenerator.config(diffFile).report(bomDiff);

		final Path generatedReleasesFile = outputDir.resolve("generated-releases.html");
		generatedBom.visit(DecomposedBomHtmlReportGenerator.builder(generatedReleasesFile)
				.skipOriginsWithSingleRelease().build());


		final Path originalReleasesFile = outputDir.resolve("original-releases.html");
		originalBom.visit(DecomposedBomHtmlReportGenerator.builder(originalReleasesFile)
				.skipOriginsWithSingleRelease().build());

		index.bomReport(bomDiff.mainUrl(), bomDiff.toUrl(), generatedBom, originalReleasesFile, generatedReleasesFile, diffFile);
	}

	private static String bomDirName(Artifact a) {
		return a.getGroupId() + "." + a.getArtifactId() + "-" + a.getVersion();
	}
}
