package org.acme.dynamodb;

import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/fruits")
public class FruitResource {

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        return "Hello AWS Community from Quarkus!";
    }

    @Inject
    FruitSyncService service;

    @GET
    @Path("/all")
    public List<Fruit> getAll() {
        return service.findAll();
    }

    @GET
    @Path("{name}")
    public Fruit getSingle(String name) {
        return service.get(name);
    }

    @POST
    public Fruit add(Fruit fruit) {
        service.add(fruit);
        return fruit;
    }

    @PATCH
    @Path("/update")
    public Fruit update(Fruit fruit) {
        service.update(fruit);
        return fruit;
    }

    @DELETE
    @Path("/delete")
    public void delete(Fruit fruit) {
        service.delete(fruit);
    }
}
