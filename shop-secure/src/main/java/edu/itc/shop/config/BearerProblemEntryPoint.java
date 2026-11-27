package edu.itc.shop.config;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import edu.itc.shop.security.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 for a malformed or expired Bearer token. Spring's BearerTokenAuthenticationEntryPoint sets the
 * WWW-Authenticate: Bearer error="invalid_token" header; the body is the same problem+json as every
 * other error. The detail never says which check failed.
 */
@Component
public class BearerProblemEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
    private final ProblemResponseWriter writer;

    public BearerProblemEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        bearer.commence(request, response, ex);
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Token expired or invalid");
    }
}
