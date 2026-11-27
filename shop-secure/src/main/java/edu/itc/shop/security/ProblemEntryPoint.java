package edu.itc.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401: no or invalid credentials. Same generic body whatever the reason. */
@Component
public class ProblemEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter writer;

    public ProblemEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer"); // Task 4: Bearer tokens, not Basic
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Authentication required");
    }
}
