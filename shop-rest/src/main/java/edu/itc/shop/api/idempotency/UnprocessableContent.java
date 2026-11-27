package edu.itc.shop.api.idempotency;

import jakarta.ws.rs.core.Response;

/** RFC 9110 status 422, which jakarta.ws.rs.core.Response.Status does not define. */
public enum UnprocessableContent implements Response.StatusType {
    STATUS;

    @Override public int getStatusCode() { return 422; }
    @Override public Response.Status.Family getFamily() { return Response.Status.Family.CLIENT_ERROR; }
    @Override public String getReasonPhrase() { return "Unprocessable Content"; }
}
