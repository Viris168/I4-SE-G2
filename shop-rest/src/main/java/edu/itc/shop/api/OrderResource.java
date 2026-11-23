package edu.itc.shop.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import edu.itc.shop.api.dto.OrderDto;
import edu.itc.shop.api.dto.PlaceOrder;
import edu.itc.shop.api.dto.Problem;
import edu.itc.shop.api.idempotency.IdempotencyStore;
import edu.itc.shop.api.idempotency.IdempotencyStore.Stored;
import edu.itc.shop.api.idempotency.UnprocessableContent;
import edu.itc.shop.domain.order.Order;
import edu.itc.shop.domain.order.OrderService;
import edu.itc.shop.domain.order.OrderStatus;

/**
 * Orders with hypermedia: every response carries the next possible actions as Link headers and as
 * {@code _links}. A client follows {@code pay} with PUT and {@code cancel} with DELETE; both rels
 * disappear once the order is no longer NEW.
 */
@Path("/orders")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Orders", description = "Place, pay and cancel orders by following links")
public class OrderResource {
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String REPLAYED = "Idempotency-Replayed";

    private final OrderService orders;
    private final IdempotencyStore idempotency;

    @Inject
    public OrderResource(OrderService orders, IdempotencyStore idempotency) {
        this.orders = orders;
        this.idempotency = idempotency;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Operation(summary = "Place an order",
               description = "Reserves stock. Safe to retry: the same Idempotency-Key and body replay the first response.")
    @APIResponse(responseCode = "201", description = "Order created (or replayed, see Idempotency-Replayed)",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = OrderDto.class)))
    @APIResponse(responseCode = "400", description = "Invalid body", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "401", description = "Missing or invalid X-Api-Key", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "404", description = "Unknown product", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "409", description = "Insufficient stock", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "415", description = "Body is not JSON", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "422", description = "Idempotency-Key reused with a different body", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "428", description = "Idempotency-Key header missing", content = @Content(mediaType = Problem.MEDIA_TYPE))
    public Response place(
            @Parameter(in = ParameterIn.HEADER, name = IDEMPOTENCY_KEY, required = true,
                       description = "Client-chosen unique key per order attempt")
            @HeaderParam(IDEMPOTENCY_KEY) String key,
            @Valid @NotNull PlaceOrder cmd,
            @Context UriInfo uriInfo) {
        if (key == null || key.isBlank()) {
            throw new ClientErrorException(IDEMPOTENCY_KEY + " header required", Response.Status.PRECONDITION_REQUIRED);
        }
        String hash = IdempotencyStore.hash(cmd);
        var outcome = idempotency.once(key, hash, () -> {
            Order order = orders.place(cmd.toOrderLines());              // 404 / 409 inside, nothing stored
            OrderDto dto = represent(order, uriInfo);
            return new Stored(hash, Response.Status.CREATED, URI.create(dto.links().get("self")), dto);
        });
        Stored s = outcome.stored();
        if (!s.bodyHash().equals(hash)) {
            throw new ClientErrorException(IDEMPOTENCY_KEY + " reused with a different body",
                                           Response.status(UnprocessableContent.STATUS).build());
        }
        OrderDto dto = (OrderDto) s.body();
        return Response.status(s.status()).location(s.location())
                .links(linkHeaders(dto))
                .header(REPLAYED, outcome.replayed())
                .entity(dto).build();
    }

    @GET
    @Path("{id}")
    @Operation(summary = "Get an order with its next possible actions")
    @APIResponse(responseCode = "200", description = "The order",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = OrderDto.class)))
    @APIResponse(responseCode = "404", description = "Unknown order", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "406", description = "Unsupported Accept", content = @Content(mediaType = Problem.MEDIA_TYPE))
    public Response one(@Parameter(description = "Order id, e.g. o-1001") @PathParam("id") String id,
                        @Context UriInfo uriInfo) {
        return ok(orders.get(id), uriInfo);
    }

    @PUT
    @Path("{id}/payment")
    @Operation(summary = "Pay an order (rel \"pay\")", description = "NEW → PAID")
    @APIResponse(responseCode = "200", description = "Paid",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = OrderDto.class)))
    @APIResponse(responseCode = "401", description = "Missing or invalid X-Api-Key", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "404", description = "Unknown order", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "409", description = "Order is not NEW", content = @Content(mediaType = Problem.MEDIA_TYPE))
    public Response pay(@Parameter(description = "Order id") @PathParam("id") String id, @Context UriInfo uriInfo) {
        return ok(orders.pay(id), uriInfo);
    }

    @DELETE
    @Path("{id}")
    @Operation(summary = "Cancel an order (rel \"cancel\")", description = "NEW → CANCELLED, restores the reserved stock")
    @APIResponse(responseCode = "200", description = "Cancelled",
                 content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = OrderDto.class)))
    @APIResponse(responseCode = "401", description = "Missing or invalid X-Api-Key", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "404", description = "Unknown order", content = @Content(mediaType = Problem.MEDIA_TYPE))
    @APIResponse(responseCode = "409", description = "Order is already PAID or CANCELLED", content = @Content(mediaType = Problem.MEDIA_TYPE))
    public Response cancel(@Parameter(description = "Order id") @PathParam("id") String id, @Context UriInfo uriInfo) {
        return ok(orders.cancel(id), uriInfo);
    }

    private static Response ok(Order order, UriInfo uriInfo) {
        OrderDto dto = represent(order, uriInfo);
        return Response.ok(dto).links(linkHeaders(dto)).build();
    }

    /** Links are built from the request's base URI, never from a hard-coded host. */
    static OrderDto represent(Order order, UriInfo uriInfo) {
        String self = uriInfo.getBaseUriBuilder().path(OrderResource.class).path(order.id()).build().toString();
        Map<String, String> links = new LinkedHashMap<>();
        links.put("self", self);
        if (order.status() == OrderStatus.NEW) {
            links.put("pay", uriInfo.getBaseUriBuilder().path(OrderResource.class).path(order.id())
                                    .path("payment").build().toString());
            links.put("cancel", self);
        }
        for (var line : order.lines()) {
            links.put("product-" + line.productId(), uriInfo.getBaseUriBuilder().path(ProductResource.class)
                    .path(Long.toString(line.productId())).build().toString());
        }
        return new OrderDto(order.id(), order.status().name(), order.total(),
                order.lines().stream().map(PlaceOrder.Line::from).toList(), links);
    }

    /** The same links as Link headers; product-N becomes rel="product". */
    private static Link[] linkHeaders(OrderDto dto) {
        List<Link> headers = new ArrayList<>();
        dto.links().forEach((rel, uri) ->
                headers.add(Link.fromUri(uri).rel(rel.startsWith("product-") ? "product" : rel).build()));
        return headers.toArray(Link[]::new);
    }
}
