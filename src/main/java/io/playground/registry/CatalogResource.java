package io.playground.registry;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.UriInfo;

import io.quarkus.maven.ArtifactCoords;
import io.quarkus.registry.catalog.Extension;

@Path("catalog")
public class CatalogResource {

	@Inject
	PlatformRegistry registry;

	@GET
	@Produces(MediaType.TEXT_HTML)
	public String catalog(@Context UriInfo uriInfo) {
		return renderCatalog(uriInfo);
	}

	private String renderCatalog(UriInfo uriInfo) {
		final MultivaluedMap<String, String> params = uriInfo.getQueryParameters();

		final Collection<String> allStreams = registry.streams();
		String stream = params.getFirst("stream");
		if(stream == null) {
			if(allStreams.isEmpty()) {
				return "The registry is empty";
			}
			stream = allStreams.iterator().next();
		}
		
		StringWriter writer = new StringWriter();
		try (PrintWriter out = new PrintWriter(new BufferedWriter(writer))) {
			out.println("<!DOCTYPE html>");
			out.println("<html>");
			out.println("<body>");
			out.println("<h1>Extension Catalog</h1>");

			out.println("<form id='selectExtensions' action='/registry/catalog' method='get'>");
			out.println("<label for='stream'>Stream:</label>");
			out.println("<select name='stream' id='stream' onChange='this.form.submit()'>");
			for(String s : allStreams) {
				out.append("<option value='").append(s).append('\'');
				if(s.equals(stream)) {
					out.print(" selected='true'");
				}
				out.append(">").append(s).append("</option>");
			}
			out.println("</select><br>");
			
			out.println("<a href='/registry/catalog'>Clear selection</a>");
			out.println("<table>");
			out.println("<tr><td>");
			for (Extension e : registry.catalog(stream).getExtensions()) {
				final String eId = extensionId(e);
				out.print("<input type='checkbox' id='" + eId + "' name='" + eId + "' value='"
						+ e.getArtifact().getKey() + "' onChange='this.form.submit()'");
				if (params.containsKey(eId)) {
					out.print(" checked");
				}
				out.println("><label for='" + eId + "'>" + e.getName() + "</label><br>");
			}
			out.println("</td><td>");
			out.println("<td style='text-align:left;vertical-align:top;'>");
			final List<String> extIds;
			if(params.isEmpty()) {
				extIds = Collections.emptyList();
			} else {
				extIds = new ArrayList<>(params.size() -1);
				for(String s : params.keySet()) {
					if("stream".equals(s)) {
						continue;
					}
					extIds.add(s);
				}
			}
			if (!extIds.isEmpty()) {
				startXmlElement(out, "dependencyManagement", 0);
				startXmlElement(out, "dependencies", 1);
				for (ArtifactCoords bom : registry.getBoms(stream, extIds)) {
					startXmlElement(out, "dependency", 2);
					xmlElement(out, "groupId", bom.getGroupId(), 3);
					xmlElement(out, "artifactId", bom.getArtifactId(), 3);
					xmlElement(out, "version", bom.getVersion(), 3);
					xmlElement(out, "type", "pom", 3);
					xmlElement(out, "scope", "import", 3);
					endXmlElement(out, "dependency", 2);
				}
				endXmlElement(out, "dependencies", 1);
				endXmlElement(out, "dependencyManagement", 0);
			}
			out.println("</td></tr>");
			out.println("</table>");
			out.println("</form>");

			out.println("</body>");
			out.println("</html>");
		} catch (Exception e) {
			throw new RuntimeException("Failed to generate the catalog page", e);
		}
		return writer.toString();
	}

	private static void xmlElement(PrintWriter out, String name, String value, int offset) {
		offset(out, offset);
		out.print("&lt;");
		out.print(name);
		out.print("&gt;");
		out.print(value);
		out.print("&lt;/");
		out.print(name);
		out.println("&gt;<br>");
	}

	private static void startXmlElement(PrintWriter out, String name, int offset) {
		offset(out, offset);
		out.print("&lt;");
		out.print(name);
		out.println("&gt;<br>");
	}

	private static void endXmlElement(PrintWriter out, String name, int offset) {
		offset(out, offset);
		out.print("&lt;/");
		out.print(name);
		out.println("&gt;<br>");
	}

	private static void offset(PrintWriter out, int offset) {
		for (int i = 0; i < offset; ++i) {
			out.print("&nbsp;&nbsp;&nbsp;&nbsp;");
		}
	}

	private static String extensionId(Extension e) {
		return e.getArtifact().getGroupId() + ":" + e.getArtifact().getArtifactId();
	}
}