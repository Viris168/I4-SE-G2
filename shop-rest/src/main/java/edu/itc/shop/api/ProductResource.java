package edu.itc.shop.api;

import java.net.URI;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import edu.itc.shop.api.dto.ProductDto;
import edu.itc.shop.api.dto.StockChange;
import edu.itc.shop.api.filter.Compress;
import edu.itc.shop.api.params.ProductFilter;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductService;

/** Thin and stateless: HTTP mapping only, rules are in ProductService. */
@Path("/products")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public class ProductResource {
    private final ProductService service;

    @Inject
    public ProductResource(ProductService service) { this.service = service; }

    @GET
    @Compress
    public List<ProductDto> list(@BeanParam @Valid ProductFilter filter) {
        return service.search(filter.toQuery()).stream().map(ProductDto::from).toList();
    }

    @GET
    @Path("{id: \\d+}")
    public ProductDto one(@PathParam("id") long id) {
        return ProductDto.from(service.get(id));
    }

    @POST
    public Response create(@Valid @NotNull ProductDto dto, @Context UriInfo uriInfo) {
        Product saved = service.create(dto.toProduct(0, 0));
        URI location = uriInfo.getAbsolutePathBuilder().path(Long.toString(saved.id())).build();
        return Response.created(location).entity(ProductDto.from(saved)).build();
    }

    @PUT
    @Path("{id: \\d+}")
    public ProductDto replace(@PathParam("id") long id, @Valid @NotNull ProductDto dto) {
        return ProductDto.from(service.replace(id, dto.toProduct(id, 0)));
    }

    @PATCH
    @Path("{id: \\d+}/stock")
    @Consumes(MediaType.APPLICATION_JSON)
    public ProductDto changeStock(@PathParam("id") long id, @Valid @NotNull StockChange change) {
        return ProductDto.from(service.adjustStock(id, change.delta()));
    }

    @DELETE
    @Path("{id: \\d+}")
    public Response delete(@PathParam("id") long id) {
        service.delete(id);
        return Response.noContent().build();
    }
}
