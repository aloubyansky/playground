package org.acme.quarkus.sample;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.acme.MyValue;


@Path("/hello")
public class HelloResource {
	
	@MyValue
	String myValue;
	
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
    	return "hello " + myValue;
    }
}