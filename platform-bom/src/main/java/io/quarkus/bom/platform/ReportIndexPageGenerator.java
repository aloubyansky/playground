package io.quarkus.bom.platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import io.quarkus.bom.decomposer.DecomposedBom;
import io.quarkus.bom.decomposer.FileReportWriter;

public class ReportIndexPageGenerator extends FileReportWriter implements AutoCloseable {

	private List<DecomposedBom> boms = new ArrayList<>();
	private List<Path> releasesHtml = new ArrayList<>();
	private List<Path> diffHtml = new ArrayList<>();
	
	public ReportIndexPageGenerator(String name) throws IOException {
		super(name);
		initHtmlBody();
	}

	public ReportIndexPageGenerator(Path file) throws IOException {
		super(file);
		initHtmlBody();
	}

	private void completeHtmlBody() throws IOException {
		generateContents();
		closeTag("body");
		closeTag("html");
	}

	private void initHtmlBody() throws IOException {
		try {
			writeLine("<!DOCTYPE html>");
			openTag("html");

			openTag("head");
			offsetLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">");
			closeTag("head");

			openTag("body");

			writeTag("h1", "Platform BOM summary");
		} catch(IOException e) {
			close();
			throw e;
		}
	}
	
	private void generateContents() throws IOException {
		writeTag("p", "");
		openTag("table");
		writeTag("caption", "text-align:left;font-weight:bold", "Generated BOMs");
		for(int i = 0; i < boms.size(); ++i) {
			openTag("tr");			
			writeTag("td", "text-align:left;font-weight:bold;color:gray", boms.get(i).bomArtifact());
			writeTag("td", "text-align:left", generateAnchor(diffHtml.get(i).toUri().toURL().toExternalForm(), "diff"));
			writeTag("td", "text-align:left", generateAnchor(releasesHtml.get(i).toUri().toURL().toExternalForm(), "decomposed"));
			closeTag("tr");
		}
		closeTag("table");
	}
	public void bomReport(DecomposedBom bom, Path releasesHtml, Path diffHtml) {
		this.boms.add(bom);
		this.releasesHtml.add(releasesHtml);
		this.diffHtml.add(diffHtml);
	}
	
	@Override
	public void close() {
		if(!isClosed()) {
		    try {
				completeHtmlBody();
			} catch (IOException e) {
			}
		}
		super.close();
	}
}
