package edu.itc.shop.api;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.GenericEntity;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import edu.itc.shop.api.dto.ProductDto;
import edu.itc.shop.api.dto.ProductV2;
import edu.itc.shop.api.dto.StockChange;
import edu.itc.shop.api.filter.Compress;
import edu.itc.shop.api.params.ProductFilter;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.ProductService;
import edu.itc.shop.domain.ReviewRepository;

/** Thin and stateless: HTTP mapping only, rules are in ProductService. */
@Path("/products")
@Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
@Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
public class ProductResource {
    private static final String V1_JSON = "application/vnd.shop.v1+json";

    private final ProductService service;
    private final ReviewRepository reviews;

    @Inject
    public ProductResource(ProductService service, ReviewRepository reviews) {
        this.service = service;
        this.reviews = reviews;
    }

    @GET
    @Compress
    public Response list(@BeanParam @Valid ProductFilter filter, @Context UriInfo uriInfo) {
        List<Product> all = service.search(filter.toQuery());
        int total = all.size();
        int lastPage = Math.max(0, (total - 1) / filter.size);
        List<ProductDto> page = all.stream()
                .skip((long) filter.page * filter.size)
                .limit(filter.size)
                .map(ProductDto::from)
                .toList();

        var links = new ArrayList<Link>();
        links.add(pageLink(uriInfo, 0, "first"));
        if (filter.page > 0) {
            links.add(pageLink(uriInfo, Math.min(filter.page - 1, lastPage), "prev"));
        }
        if (filter.page < lastPage) {
            links.add(pageLink(uriInfo, filter.page + 1, "next"));
        }
        links.add(pageLink(uriInfo, lastPage, "last"));

        return Response.ok(new GenericEntity<List<ProductDto>>(page) {})
                .links(links.toArray(Link[]::new))
                .header("X-Total-Count", total)
                .build();
    }

    @GET
    @Path("{id: \\d+}")
    @Produces({"application/json; qs=1.0", V1_JSON + "; qs=0.9", MediaType.APPLICATION_XML + "; qs=0.9"})
    public Response one(@PathParam("id") long id, @Context Request request) {
        Product product = service.get(id);
        return cachedProduct(product, ProductDto.from(product), request);
    }

    @GET
    @Path("{id: \\d+}")
    @Produces(ProductV2.MEDIA_TYPE + "; qs=0.9")
    public Response oneV2(@PathParam("id") long id, @Context Request request) {
        Product product = service.get(id);
        return cachedProduct(product, ProductV2.from(product), request);
    }

    @POST
    public Response create(@Valid @NotNull ProductDto dto, @Context UriInfo uriInfo) {
        Product saved = service.create(dto.toProduct(0, 0));
        URI location = uriInfo.getAbsolutePathBuilder().path(Long.toString(saved.id())).build();
        return Response.created(location).entity(ProductDto.from(saved)).build();
    }

    @PUT
    @Path("{id: \\d+}")
    public Response replace(@PathParam("id") long id,
                            @HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
                            @Valid @NotNull ProductDto dto,
                            @Context Request request) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new ClientErrorException("If-Match header is required", Response.Status.PRECONDITION_REQUIRED);
        }
        Product current = service.get(id);
        EntityTag currentTag = etagOf(current);
        Response.ResponseBuilder cond = request.evaluatePreconditions(currentTag);
        if (cond != null) {
            throw new ClientErrorException("ETag does not match current version", cond.tag(currentTag).build());
        }
        Product saved = service.replace(id, dto.toProduct(id, current.version()), current.version());
        return Response.ok(ProductDto.from(saved)).tag(etagOf(saved)).build();
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

    @Path("{id: \\d+}/reviews")
    public ReviewResource reviews(@PathParam("id") long id) {
        return new ReviewResource(service.get(id), reviews);
    }

    private static Response cachedProduct(Product product, Object representation, Request request) {
        EntityTag tag = etagOf(product);
        Response.ResponseBuilder cond = request.evaluatePreconditions(tag);
        if (cond != null) {
            return cond.tag(tag).build();
        }
        CacheControl cc = new CacheControl();
        cc.setPrivate(true);
        cc.setMaxAge(60);
        cc.setNoTransform(true);
        return Response.ok(representation).tag(tag).cacheControl(cc).build();
    }

    private static EntityTag etagOf(Product product) {
        return new EntityTag(Integer.toHexString(product.version()));
    }

    private static Link pageLink(UriInfo uriInfo, int page, String rel) {
        return Link.fromUriBuilder(uriInfo.getRequestUriBuilder().replaceQueryParam("page", page))
                .rel(rel)
                .build();
    }
}
