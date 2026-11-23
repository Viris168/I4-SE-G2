package edu.itc.shop.api;

import java.net.URI;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import edu.itc.shop.api.dto.CreateReview;
import edu.itc.shop.domain.Product;
import edu.itc.shop.domain.Review;
import edu.itc.shop.domain.ReviewRepository;

/** Sub-resource created per request by {@link ProductResource#reviews(long)}. */
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReviewResource {
    private final Product product;
    private final ReviewRepository reviews;

    public ReviewResource(Product product, ReviewRepository reviews) {
        this.product = product;
        this.reviews = reviews;
    }

    @GET
    public List<Review> list() {
        return reviews.findByProductId(product.id());
    }

    @GET
    @Path("{reviewId: \\d+}")
    public Review one(@PathParam("reviewId") long reviewId) {
        return reviews.findById(product.id(), reviewId)
                .orElseThrow(() -> new NotFoundException(
                        "Review " + reviewId + " not found for product " + product.id()));
    }

    @POST
    public Response create(@Valid @NotNull CreateReview body, @Context UriInfo uriInfo) {
        Review saved = reviews.save(new Review(0, product.id(), body.stars(), body.author(), body.text()));
        URI location = uriInfo.getAbsolutePathBuilder().path(Long.toString(saved.id())).build();
        return Response.created(location).entity(saved).build();
    }
}
