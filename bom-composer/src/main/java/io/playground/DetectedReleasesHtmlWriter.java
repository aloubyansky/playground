package io.playground;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;

import org.eclipse.aether.artifact.Artifact;

public class DetectedReleasesHtmlWriter extends DetectedReleasesFileWriter {

	public static HtmlWriterBuilder builder(String file) {
		return new DetectedReleasesHtmlWriter(file).new HtmlWriterBuilder();
	}

	public static HtmlWriterBuilder builder(Path file) {
		return new DetectedReleasesHtmlWriter(file).new HtmlWriterBuilder();
	}

	public class HtmlWriterBuilder {

		private HtmlWriterBuilder() {
		}

		public HtmlWriterBuilder setIndentChars(int indent) {
			indentChars = indent;
			return this;
		}

		public DetectedReleasesHtmlWriter build() {
			return DetectedReleasesHtmlWriter.this;
		}
	}

	private int tagDepth;
	private int indentChars = 4;
	final StringBuilder buf = new StringBuilder();

	private int releaseOriginsTotal;
	private int releaseVersionsTotal;
	private int artifactsTotal;

	private DetectedReleasesHtmlWriter(String file) {
		super(file);
	}

	private DetectedReleasesHtmlWriter(Path file) {
		super(file);
	}

	@Override
	protected void writeStartBom(BufferedWriter writer, Artifact bomArtifact) throws IOException {
		writeLine("<!DOCTYPE html>");
		openTag("html");
		openTag("body");

		writeTag("h1", "Project Releases Detected in " + bomArtifact);
	}

	@Override
	protected void writeStartReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException {
		writeTag("h2", releaseOrigin);
		openTag("ul");
		++releaseOriginsTotal;
	}

	@Override
	protected void writeEndReleaseOrigin(BufferedWriter writer, ReleaseOrigin releaseOrigin) throws IOException {
		closeTag("ul");
	}

	@Override
	protected void writeStartReleaseVersion(BufferedWriter writer, ReleaseVersion releaseVersion,
			Collection<Artifact> artifacts) throws IOException {
		openTag("li");
		writeTag("h3", releaseVersion);

		openTag("ol");
		for(Artifact a : artifacts) {
			writeTag("li", a);
			++artifactsTotal;
		}
		closeTag("ol");
		closeTag("li");
		++releaseVersionsTotal;
	}

	@Override
	protected void writeEndReleaseVersion(BufferedWriter writer, ReleaseVersion releaseVersion) throws IOException {
	}

	@Override
	protected void writeEndBom(BufferedWriter writer) throws IOException {

		writeTag("h2", "Total Processed");

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
}
