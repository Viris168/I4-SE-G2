package edu.itc.shop.api.filter;

import java.util.Set;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import edu.itc.shop.api.dto.Problem;

/** Writes need a valid X-Api-Key; reads stay public. Runs before matching, ahead of the user filters. */
@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION)
public class ApiKeyFilter implements ContainerRequestFilter {

    public static final String HEADER = "X-Api-Key";
    private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS");

    private final Set<String> keys = Set.of(System.getProperty("shop.api.keys", "demo-123").split(","));

    @Override
    public void filter(ContainerRequestContext ctx) {
        if (SAFE.contains(ctx.getMethod())) {
            return;
        }
        String key = ctx.getHeaderString(HEADER);
        if (key != null && keys.contains(key)) {
            return;
        }
        var problem = new Problem("about:blank", "Unauthorized", 401,
                "Missing or invalid X-Api-Key", ctx.getUriInfo().getRequestUri().getPath(), null);
        ctx.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                .header("WWW-Authenticate", "ApiKey realm=\"shop\"")
                .type(Problem.MEDIA_TYPE)
                .entity(problem)
                .build());
    }
}
