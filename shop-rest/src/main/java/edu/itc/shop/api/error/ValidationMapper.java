package edu.itc.shop.api.error;

import java.util.Comparator;
import java.util.Map;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import edu.itc.shop.api.dto.Problem;

/** Bean Validation failures → 400 problem+json with one field/message pair per violation. */
@Provider
public class ValidationMapper implements ExceptionMapper<ConstraintViolationException> {
    @Context UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .map(v -> Map.of("field", lastNode(v.getPropertyPath()), "message", v.getMessage()))
                .sorted(Comparator.comparing((Map<String, String> e) -> e.get("field"))
                                  .thenComparing(e -> e.get("message")))
                .toList();
        var problem = new Problem("about:blank", "Invalid request", 400, "Validation failed",
                                  uriInfo.getRequestUri().getPath(), errors);
        return Response.status(400).type(Problem.MEDIA_TYPE).entity(problem).build();
    }

    // "create.arg0.price" → "price"
    static String lastNode(Path path) {
        String name = null;
        for (Path.Node n : path) name = n.getName();
        return name;
    }
}
