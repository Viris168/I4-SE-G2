package edu.itc.shop.api.filter;

import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.WriterInterceptor;
import jakarta.ws.rs.ext.WriterInterceptorContext;

/** Gzips the body of @Compress methods, but only when the client says it accepts gzip. */
@Provider
@Compress
public class GzipWriterInterceptor implements WriterInterceptor {

    @Context
    HttpHeaders requestHeaders; // providers may use @Context injection

    @Override
    public void aroundWriteTo(WriterInterceptorContext ctx) throws IOException {
        String accepted = requestHeaders.getHeaderString(HttpHeaders.ACCEPT_ENCODING);
        if (accepted == null || !accepted.contains("gzip")) {
            ctx.proceed();
            return;
        }
        MultivaluedMap<String, Object> headers = ctx.getHeaders();
        headers.putSingle(HttpHeaders.CONTENT_ENCODING, "gzip");
        headers.add(HttpHeaders.VARY, HttpHeaders.ACCEPT_ENCODING); // caches must key on Accept-Encoding
        headers.remove(HttpHeaders.CONTENT_LENGTH); // the compressed size is not known in advance
        GZIPOutputStream gzip = new GZIPOutputStream(ctx.getOutputStream());
        ctx.setOutputStream(gzip);
        try {
            ctx.proceed();
        } finally {
            gzip.finish(); // write the gzip trailer; the container closes the real stream
        }
    }
}
