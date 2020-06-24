package io.playground;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.eclipse.aether.artifact.Artifact;

public class DecomposedBomHtmlReportGenerator extends DecomposedBomReportFileWriter {

	public static HtmlWriterBuilder builder(String file) {
		return new DecomposedBomHtmlReportGenerator(file).new HtmlWriterBuilder();
	}

	public static HtmlWriterBuilder builder(Path file) {
		return new DecomposedBomHtmlReportGenerator(file).new HtmlWriterBuilder();
	}

	public class HtmlWriterBuilder {

		private HtmlWriterBuilder() {
		}

		public HtmlWriterBuilder indentChars(int indent) {
			indentChars = indent;
			return this;
		}

		public HtmlWriterBuilder skipSingleReleases() {
			skipSingleReleases = true;
			return this;
		}

		public DecomposedBomHtmlReportGenerator build() {
			return DecomposedBomHtmlReportGenerator.this;
		}
	}

	private static String RED = "Red";
	private static String GREEN = "Green";
	private static String BLUE = "Blue";
	
	private int tagDepth;
	private int indentChars = 4;
	final StringBuilder buf = new StringBuilder();

	private int releaseOriginsTotal;
	private int releaseVersionsTotal;
	private int artifactsTotal;
	private boolean skipSingleReleases;

	private Map<String, Map<String, ProjectDependency>> allDeps = new HashMap<>();
	private int originReleaseVersions;
	private List<ArtifactVersion> releaseVersions = new ArrayList<>();

	private DecomposedBomHtmlReportGenerator(String file) {
		super(file);
	}

	private DecomposedBomHtmlReportGenerator(Path file) {
		super(file);
	}

	@Override
	protected void writeStartBom(BufferedWriter writer, Artifact bomArtifact) throws IOException {
		writeLine("<!DOCTYPE html>");
		openTag("html");

		openTag("head");
		offsetLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");

		openTag("style");
		writeLine(".accordion {\n" +
				"  background-color: #eee;\n" +
				"  color: #444;\n" +
				"  cursor: pointer;\n" +
				"  padding: 18px;\n" +
				"  width: 100%;\n" +
				"  border: none;\n" +
				"  text-align: left;\n" +
				"  outline: none;\n" +
				"  font-size: 15px;\n" +
				"  transition: 0.4s;\n" +
				"}"
				);
		writeLine(".active, .accordion:hover {\n" +
				"  background-color: #ccc; \n" +
				"}");
		writeLine(".panel {\n" +
				"  padding: 0 18px;\n" +
				"  display: none;\n" +
				"  background-color: white;\n" +
				"  overflow: hidden;\n" +
				"}");
		closeTag("style");
		closeTag("head");

		openTag("body");

		writeTag("h1", "Multi Module Project Releases Detected in " + bomArtifact);
		if(skipSingleReleases) {
		    writeTag("p", "color:" + RED, "Release origins with a single release version were skipped");
		}

		writeTag("p", "The colors used to highlight the versions:");
		openTag("table");
		openTag("tr");
		writeTag("td", "text-align:left;color:" + BLUE, "Blue");
		writeTag("td", "- version of the artifact found in the BOM which is either the preferred version or an older version for which the preferred version is not available");
		closeTag("tr");
		openTag("tr");
		writeTag("td", "text-align:left;color:" + RED, "Red");
		writeTag("td", "- old version of the artifact found in the BOM for which the preferred version is available in the Maven repository");
		closeTag("tr");
		openTag("tr");
		writeTag("td", "text-align:left;color:" + GREEN, "Green");
		writeTag("td", "- the preferred version of the artifact found to be available in the Maven repository");
		closeTag("tr");
		closeTag("table");
		
		openTag("p");
		closeTag("p");
	}

	@Override
	protected boolean writeStartReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin, int versions) throws IOException {
		originReleaseVersions = versions;
		return !skipSingleReleases || versions > 1;
	}

	@Override
	protected void writeEndReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException {
		offsetLine("<button class=\"accordion\">" + releaseOrigin + (originReleaseVersions > 1 ? " (" + originReleaseVersions + ")" : "") + "</button>");
		offsetLine("<div class=\"panel\">");

		Collections.sort(releaseVersions);
		final List<String> stringVersions = releaseVersions.stream().map(v -> v.toString()).collect(Collectors.toList());

		openTag("table");
		int i = 1;

		for(String releaseVersionStr : stringVersions) {
			final Map<String, ProjectDependency> releaseDeps = allDeps.get(releaseVersionStr);			
			final List<String> sortedKeys = new ArrayList<>(releaseDeps.keySet());
			Collections.sort(sortedKeys);
			for(String key : sortedKeys) {
				openTag("tr");
				writeTag("td", i++ + ")");
				final ProjectDependency dep = releaseDeps.get(key);
				writeTag("td", dep.artifact());
				for(int j = 0; j < stringVersions.size(); ++j) {
					final String version = stringVersions.get(j);
					if(dep.releaseId().version().asString().equals(version)) {
						writeTag("td", !dep.isUpdateAvailable() || j == stringVersions.size() - 1 ? "color:" + BLUE : "color:" + RED, version);
					} else if(dep.isUpdateAvailable() && dep.availableUpdate().releaseId().version().asString().equals(version)) {
						writeTag("td", "color:" + GREEN, version);
					} else {
						emptyTag("td");
					}
				}
				closeTag("tr");
			}
		}
		
		closeTag("table");

		offsetLine("</div>");

		++releaseOriginsTotal;
		allDeps.clear();
		releaseVersionsTotal += releaseVersions.size();
		releaseVersions.clear();
	}

	@Override
	protected void writeProjectRelease(BufferedWriter writer, ProjectRelease release) throws IOException {
		final List<ProjectDependency> deps = release.dependencies();
        releaseVersions.add(new DefaultArtifactVersion(release.id().version().asString()));
        final Map<String, ProjectDependency> releaseDeps = new HashMap<>(deps.size());
        allDeps.put(release.id().version().asString(), releaseDeps);
		for(ProjectDependency dep : deps) {
			releaseDeps.put(dep.key().toString(), dep);
		}
		artifactsTotal += deps.size();
	}

	@Override
	protected void writeEndBom(BufferedWriter writer) throws IOException {

		writeTag("h2", "Total Reported");

		openTag("table");
		openTag("tr");
		writeTag("th", "text-align:left;", "Release origins:");
		writeTag("td", "text-align:right;", releaseOriginsTotal);
		closeTag("tr");
		openTag("tr");
		writeTag("th", "text-align:left;", "Release versions:");
		writeTag("td", "text-align:right;", releaseVersionsTotal);
		closeTag("tr");
		openTag("tr");
		writeTag("th", "text-align:left;", "Artifacts:");
		writeTag("td", "text-align:right;", artifactsTotal);
		closeTag("tr");
		closeTag("table");

		openTag("script");
		writeLine("var acc = document.getElementsByClassName(\"accordion\");\n" +
				"var i;\n" +
				"for (i = 0; i < acc.length; i++) {\n" +
				"  acc[i].addEventListener(\"click\", function() {\n" +
				"    this.classList.toggle(\"active\");\n" +
				"    var panel = this.nextElementSibling;\n" +
				"    if (panel.style.display === \"block\") {\n" +
				"      panel.style.display = \"none\";\n" +
				"    } else {\n" +
				"      panel.style.display = \"block\";\n" +
				"    }\n" +
				"  });\n" +
				"}");
		closeTag("script");

		closeTag("body");
		closeTag("html");
	}

	private StringBuilder buf() {
		buf.setLength(0);
		return buf;
	}

	private void writeTag(String name, Object value) throws IOException {
		writeTag(name, null, value);
	}

	private void writeTag(String name, String style, Object value) throws IOException {
		offset();
		var buf = buf();
		buf.append('<').append(name);
		if(style != null) {
			buf.append(" style=\"").append(style).append("\"");
		}
		buf.append('>').append(value).append("</").append(name).append('>');
		writeLine(buf);
	}

	private void openTag(String name) throws IOException {
		offset();
		var buf = buf();
		buf.append('<').append(name).append('>');
		writeLine(buf.toString());
		++tagDepth;
	}

	private void closeTag(String name) throws IOException {
		--tagDepth;
		offset();
		var buf = buf();
		buf.append("</").append(name).append('>');
		writeLine(buf.toString());
	}

	private void emptyTag(String name) throws IOException {
		offset();
		var buf = buf();
		buf.append("<").append(name).append("/>");
		writeLine(buf.toString());
	}

	private void offsetLine(String line) throws IOException {
		offset();
		writeLine(line);
	}

	private void offset() throws IOException {
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		append(buf);
	}
}
