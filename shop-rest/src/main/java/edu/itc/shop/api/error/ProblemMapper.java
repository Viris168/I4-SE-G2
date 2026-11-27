package edu.itc.shop.api.error;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.UUID;
import jakarta.json.bind.JsonbException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import edu.itc.shop.api.dto.Problem;

/**
 * Every other error → RFC 9457 problem+json. A WebApplicationException (404, 405, 409, 415, ...) keeps its
 * status and headers; anything else is a 500 whose body carries only a reference id — the stack trace goes
 * to the log under the same id, never to the client. A request body JSON-B cannot read is the client's
 * fault, so it is a 400 rather than a 500.
 */
@Provider
public class ProblemMapper implements ExceptionMapper<Throwable> {
    private static final Logger LOG = System.getLogger("api");

    @Context UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable ex) {
        String path = uriInfo.getRequestUri().getPath();
        if (ex instanceof WebApplicationException wae) {
            Response original = wae.getResponse();
            int status = original.getStatus();
            var problem = new Problem("about:blank", reasonPhrase(original), status, wae.getMessage(), path, null);
            return Response.fromResponse(original).status(original.getStatusInfo()).type(Problem.MEDIA_TYPE).entity(problem).build();
        }
        if (isUnreadableBody(ex)) {
            var problem = new Problem("about:blank", "Bad Request", 400,
                                      "Request body is not valid JSON for this resource", path, null);
            return Response.status(400).type(Problem.MEDIA_TYPE).entity(problem).build();
        }
        String reference = UUID.randomUUID().toString();
        LOG.log(Level.ERROR, "Unhandled error on " + path + ", reference " + reference, ex);
        var problem = new Problem("about:blank", "Internal Server Error", 500,
                                  "reference " + reference, path, null);
        return Response.serverError().type(Problem.MEDIA_TYPE).entity(problem).build();
    }

    private static boolean isUnreadableBody(Throwable ex) {
        if (!(ex instanceof ProcessingException)) return false;
        for (Throwable t = ex.getCause(); t != null; t = t.getCause()) {
            if (t instanceof JsonbException) return true;
        }
        return false;
    }

    private static String reasonPhrase(Response response) {
        String phrase = response.getStatusInfo().getReasonPhrase();
        return phrase == null || phrase.isBlank() ? "HTTP " + response.getStatus() : phrase;
    }
}
