package edu.itc.shop.api.filter;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

/** Gives every request a correlation id, echoes it in the response and writes one access-log line. */
@Provider
@Priority(Priorities.USER)
public class CorrelationFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Correlation-Id";
    private static final String ID = "correlation.id";
    private static final String START = "correlation.start";
    private static final Logger LOG = System.getLogger(CorrelationFilter.class.getName());

    @Override
    public void filter(ContainerRequestContext request) {
        request.setProperty(ID, idOf(request));
        request.setProperty(START, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // A pre-matching filter (API key, CORS preflight) may have aborted before the request side ran.
        String id = request.getProperty(ID) instanceof String known ? known : idOf(request);
        String took = request.getProperty(START) instanceof Long start
                ? (System.nanoTime() - start) / 1_000_000 + " ms"
                : "- ms";
        response.getHeaders().putSingle(HEADER, id);
        LOG.log(Level.INFO, "{0} {1} -> {2} [{3}] {4}", request.getMethod(),
                request.getUriInfo().getRequestUri().getPath(), response.getStatus(), id, took);
    }

    private static String idOf(ContainerRequestContext request) {
        String sent = request.getHeaderString(HEADER);
        return sent == null || sent.isBlank() ? UUID.randomUUID().toString() : sent;
    }
}
