package org.acme;

import java.io.BufferedWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriInfo;

import io.quarkus.registry.catalog.Extension;


@Path("/registry")
public class GreetingResource {
	
	@Inject
	PlatformRegistry registry;
	
    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("catalog")
    public String catalog(@Context UriInfo uriInfo) {
    	return renderCatalog(uriInfo);
    }

	private String renderCatalog(UriInfo uriInfo) {
		final MultivaluedMap<String, String> params = uriInfo.getQueryParameters();
		
    	StringWriter writer = new StringWriter();
    	try(PrintWriter out = new PrintWriter(new BufferedWriter(writer))) {
    		out.println("<!DOCTYPE html>");
    		out.println("<html>");
    		out.println("<body>");
    		out.println("<h1>Extension Catalog</h1>");
    		
    		out.println("<table>");
    		out.println("<tr><td>");
    		out.println("<form id='selectExtensions' action='/registry/catalog' method='get'>");
    		for(Extension e : registry.catalog().getExtensions()) {
    			final String eId = extensionId(e);
				out.print("<input type='checkbox' id='" + eId + "' name='" + eId + "' value='" + e.getArtifact().getKey() + "' onChange='this.form.submit()'");
				if(params.containsKey(eId)) {
					out.print(" checked");
				}
    			out.println("><label for='" + eId + "'>" + e.getName() + "</label><br>");
    		}
    		out.println("</form>");
    		out.println("</td><td>");
    		out.println("<td style='text-align:left;vertical-align:top;'>");
    		if(!params.isEmpty()) {
    			out.println("<ul>");
    			for(String bom : registry.getBoms(params.keySet())) {
    				out.println("<li>" + bom + "</li>");
    			}
        		out.println("</ul>");
    		}
    		out.println("</td></tr>");
    		out.println("</table>");
    		
    		out.println("</body>");
    		out.println("</html>");
    	} catch (Exception e) {
    		throw new RuntimeException("Failed to generate the catalog page", e);
		}
        return writer.toString();
	}

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Path("/new-platform/{groupId}/{artifactId}/{version}")
    public String registryPlatform(@PathParam("groupId") String groupId, @PathParam("artifactId") String artifactId, @PathParam("version") String version) {
    	final String msg = "Registered platform " + registry.registerPlatform(groupId, artifactId, version).getBom();
    	System.out.println(msg);
		return msg;
    }
    
    private static String extensionId(Extension e) {
    	return e.getArtifact().getGroupId() + ":" + e.getArtifact().getArtifactId();
    }
}