package edu.itc.shop.api.filter;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

/**
 * CORS for the front end on localhost:5173. The request side answers every OPTIONS preflight
 * before matching, so it never reaches a resource; the response side adds the headers to all answers.
 */
@Provider
@PreMatching
public class CorsFilter implements ContainerRequestFilter, ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext request) {
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            request.abortWith(Response.ok().build());
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        MultivaluedMap<String, Object> headers = response.getHeaders();
        headers.putSingle("Access-Control-Allow-Origin", "http://localhost:5173");
        headers.putSingle("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE");
        headers.putSingle("Access-Control-Allow-Headers", "Content-Type,X-Api-Key,X-Correlation-Id,If-Match");
        headers.putSingle("Access-Control-Expose-Headers", "Location,ETag,Link,X-Total-Count,X-Correlation-Id");
    }
}
