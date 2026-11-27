package edu.itc.shop.filtertest;

import java.net.URI;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import edu.itc.shop.api.filter.Compress;

/**
 * Test-only stand-in for the product resource, so the filters can be tested before Task 1 exists.
 * It lives outside edu.itc.shop.api, so the real application never scans it.
 */
@Path("/items")
@Produces(MediaType.APPLICATION_JSON)
public class ItemsResource {

    /** Counts calls that reached a resource method, to prove a filter stopped a request earlier. */
    static final AtomicInteger HITS = new AtomicInteger();

    public record Item(int id, String name) {
    }

    @GET
    @Compress
    public List<Item> list() {
        HITS.incrementAndGet();
        return IntStream.rangeClosed(1, 200).mapToObj(i -> new Item(i, "Item number " + i)).toList();
    }

    @GET
    @Path("{id: \\d+}")
    public Item one(@PathParam("id") int id) {
        HITS.incrementAndGet();
        return new Item(id, "Item number " + id);
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(Item item) {
        HITS.incrementAndGet();
        return Response.created(URI.create("/api/items/" + item.id())).entity(item).build();
    }
}
