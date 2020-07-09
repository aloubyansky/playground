package io.quarkus.bom.diff;

import java.io.IOException;
import java.nio.file.Path;

import io.quarkus.bom.decomposer.FileReportWriter;

public class HtmlBomDiffReportGenerator extends FileReportWriter implements BomDiffReportGenerator {

	public static Config config(String name) {
		return new HtmlBomDiffReportGenerator(name).new Config();
	}

	public static Config config(Path reportFile) {
		return new HtmlBomDiffReportGenerator(reportFile).new Config();
	}

	public class Config {

		private Config() {
		}

		public void report(BomDiff bomDiff) {
			HtmlBomDiffReportGenerator.this.report(bomDiff);
		}
	}

	private HtmlBomDiffReportGenerator(String name) {
		super(name);
	}

	private HtmlBomDiffReportGenerator(Path path) {
		super(path);
	}

	@Override
	public void report(BomDiff bomDiff) {
		try {
			generateHtml(bomDiff);
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate HTML report", e);
		} finally {
			close();
		}
	}

	protected void generateHtml(BomDiff bomDiff) throws IOException {
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

		writeTag("h1", "Managed Dependencies Comparison Report");

		generateBody(bomDiff);

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

	private void generateBody(BomDiff bomDiff) {


	}
}
