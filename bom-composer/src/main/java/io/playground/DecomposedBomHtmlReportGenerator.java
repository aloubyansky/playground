package io.playground;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

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

	private int tagDepth;
	private int indentChars = 4;
	final StringBuilder buf = new StringBuilder();

	private int releaseOriginsTotal;
	private int releaseVersionsTotal;
	private int artifactsTotal;
	private boolean skipSingleReleases;

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

		writeTag("h1", "Project Releases Detected in " + bomArtifact);
	}

	@Override
	protected boolean writeStartReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin, int versions) throws IOException {
		if(skipSingleReleases && versions < 2) {
			return false;
		}
		offsetLine("<button class=\"accordion\">" + releaseOrigin + (versions > 1 ? " (" + versions + ")" : "") + "</button>");
		offsetLine("<div class=\"panel\">");
		openTag("ul");
		++releaseOriginsTotal;
		return true;
	}

	@Override
	protected void writeEndReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException {
		closeTag("ul");
		offsetLine("</div>");
	}

	@Override
	protected void writeProjectRelease(BufferedWriter writer, ProjectRelease release) throws IOException {
		final List<ProjectDependency> deps = release.dependencies();

		openTag("li");
		offsetLine("<button class=\"accordion\">" + release.id().version() + " (" + deps.size() + ")</button>");
		offsetLine("<div class=\"panel\">");

		openTag("table");
		int i = 1;
		for(ProjectDependency dep : deps) {
			openTag("tr");
			writeTag("td", i++ + ")");
			final StringBuilder buf = new StringBuilder();
			final Artifact artifact = dep.artifact();
			buf.append(artifact.getGroupId()).append(':').append(artifact.getArtifactId()).append(':').append(artifact.getClassifier()).append(':').append(artifact.getExtension());
			writeTag("td", buf.toString());
			if(dep.isUpdateAvailable()) {
				writeTag("td", "available in " + dep.availableUpdate().getVersion());
			}
			closeTag("tr");
			++artifactsTotal;
		}
		closeTag("table");
		offsetLine("</div>");
		closeTag("li");
		++releaseVersionsTotal;
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
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		buf.append('<').append(name);
		if(style != null) {
			buf.append(" style=\"").append(style).append("\"");
		}
		buf.append('>').append(value).append("</").append(name).append('>');
		writeLine(buf);
	}

	private void openTag(String name) throws IOException {
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		buf.append('<').append(name).append('>');
		writeLine(buf.toString());
		++tagDepth;
	}

	private void closeTag(String name) throws IOException {
		--tagDepth;
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		buf.append("</").append(name).append('>');
		writeLine(buf.toString());
	}

	private void emptyTag(String name) throws IOException {
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		buf.append("<").append(name).append("/>");
		writeLine(buf.toString());
	}

	private void offsetLine(String line) throws IOException {
		var buf = buf();
		for(int i = 0; i < tagDepth*indentChars; ++i) {
			buf.append(' ');
		}
		buf.append(line);
		writeLine(buf.toString());
	}
}
