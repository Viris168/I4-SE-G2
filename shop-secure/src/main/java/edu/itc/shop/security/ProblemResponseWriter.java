package edu.itc.shop.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes an RFC 9457 problem+json body directly to the servlet response.
 * Used by the security filter chain, which runs before Spring MVC and its
 * {@code @RestControllerAdvice} can handle anything.
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper json;

    public ProblemResponseWriter(JsonMapper json) {
        this.json = json;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      HttpStatus status, String detail) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", status.getReasonPhrase());
        body.put("status", status.value());
        body.put("detail", detail);
        body.put("instance", request.getRequestURI());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), body);
    }
}
