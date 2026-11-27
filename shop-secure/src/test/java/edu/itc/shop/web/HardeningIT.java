package edu.itc.shop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * Task 5 end-to-end verification: CORS, security headers, stored XSS defence (JSON + Thymeleaf),
 * request-id propagation, and SECURITY event logging (failed login, ok login, access denied).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij",
        "SHOP_SEED_ADMIN_PASSWORD=Admin-pass-123",
        "SHOP_SEED_CUSTOMER_PASSWORD=Alice-pass-123"
})
@ExtendWith(OutputCaptureExtension.class)
class HardeningIT {

    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final String XSS_PAYLOAD = "<img src=x onerror=\"document.title='pwned'\">";

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    // --- CORS -------------------------------------------------------------------------------------

    @Test
    void corsAllowsLocalFrontendAndRejectsEvilOrigin() throws Exception {
        HttpResponse<String> allowed = send(request("/api/orders")
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .method("OPTIONS", BodyPublishers.noBody()));

        assertEquals(200, allowed.statusCode());
        assertEquals("http://localhost:5173", header(allowed, "Access-Control-Allow-Origin"));
        assertThat(header(allowed, "Access-Control-Allow-Methods")).contains("GET", "POST", "PUT", "DELETE");
        assertThat(header(allowed, "Access-Control-Allow-Headers").toLowerCase())
                .contains("authorization", "content-type");
        assertEquals("3600", header(allowed, "Access-Control-Max-Age"));
        assertThat(allowed.headers().allValues("Vary")).anyMatch(v -> v.contains("Origin"));

        HttpResponse<String> evil = send(request("/api/orders")
                .header("Origin", "http://evil.example")
                .header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", BodyPublishers.noBody()));

        assertEquals(403, evil.statusCode());
        assertFalse(evil.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    // --- Security headers -------------------------------------------------------------------------

    @Test
    void hardenedSecurityHeadersPresentOnApiAndHtmlPages() throws Exception {
        HttpResponse<String> head = send(request("/api/products").method("HEAD", BodyPublishers.noBody()));
        assertEquals(200, head.statusCode());
        assertHardenedHeaders(head);

        HttpResponse<String> html = send(request("/products/2/reviews.html").GET());
        assertEquals(200, html.statusCode());
        assertHardenedHeaders(html);
        assertThat(header(html, "Content-Type")).startsWith("text/html");
    }

    // --- Stored XSS demo --------------------------------------------------------------------------

    @Test
    void storedXssPayloadIsSafeInJsonAndEscapedInThymeleaf() throws Exception {
        // Anonymous POST rejected with 401
        HttpResponse<String> anon = send(request("/api/reviews")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"productId\":2,\"stars\":5,\"text\":\"Great\"}")));
        assertEquals(401, anon.statusCode());

        String token = loginAndGetToken("alice", "Alice-pass-123");

        // Validation rejects out-of-range stars and oversized text
        String tooLong = "a".repeat(1001);
        HttpResponse<String> invalid = send(request("/api/reviews")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"productId\":2,\"stars\":6,\"text\":\"" + tooLong + "\"}")));
        assertEquals(400, invalid.statusCode());
        assertThat(header(invalid, "Content-Type")).startsWith("application/problem+json");

        // Post the XSS payload as alice
        String jsonBody = "{\"productId\":2,\"stars\":1,\"text\":\"<img src=x onerror=\\\"document.title='pwned'\\\">\"}";
        HttpResponse<String> created = send(request("/api/reviews")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(jsonBody)));
        assertEquals(201, created.statusCode());
        assertEquals("/api/products/2/reviews", header(created, "Location"));

        // (a) Public JSON API returns it JSON-escaped with application/json + nosniff
        HttpResponse<String> apiReviews = send(request("/api/products/2/reviews").GET());
        assertEquals(200, apiReviews.statusCode());
        assertThat(header(apiReviews, "Content-Type")).startsWith("application/json");
        assertEquals("nosniff", header(apiReviews, "X-Content-Type-Options"));
        assertThat(apiReviews.body()).contains("\"author\":\"alice\"");
        assertThat(apiReviews.body()).contains("<img src=x onerror=\\\"document.title='pwned'\\\">");

        // (b) Thymeleaf page renders it escaped via th:text
        HttpResponse<String> htmlPage = send(request("/products/2/reviews.html").GET());
        assertEquals(200, htmlPage.statusCode());
        assertThat(htmlPage.body())
                .contains("<p>&lt;img src=x onerror=&quot;document.title=&#39;pwned&#39;&quot;&gt;</p>");
        assertFalse(htmlPage.body().contains(XSS_PAYLOAD), "raw unescaped payload must never appear in HTML");
    }

    // --- Security logging & request id ------------------------------------------------------------

    @Test
    void logsFailedLoginSuccessfulLoginAndAccessDeniedWithRequestIds(CapturedOutput output) throws Exception {
        HttpResponse<String> failed = send(request("/api/auth/login")
                .header("X-Request-Id", "req-fail-1")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"username\":\"alice\",\"password\":\"wrong\"}")));
        assertEquals(401, failed.statusCode());
        assertEquals("req-fail-1", header(failed, "X-Request-Id"));

        HttpResponse<String> ok = send(request("/api/auth/login")
                .header("X-Request-Id", "req-ok-2")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"username\":\"alice\",\"password\":\"Alice-pass-123\"}")));
        assertEquals(200, ok.statusCode());
        assertEquals("req-ok-2", header(ok, "X-Request-Id"));
        String token = extractToken(ok.body());

        // Trigger method-level @PreAuthorize denial: alice reading bob's orders
        HttpResponse<String> deniedMethod = send(request("/api/orders?customer=bob")
                .header("X-Request-Id", "req-deny-3")
                .header("Authorization", "Bearer " + token)
                .GET());
        assertEquals(403, deniedMethod.statusCode());
        assertEquals("req-deny-3", header(deniedMethod, "X-Request-Id"));

        // Trigger URL-level denial: alice trying to ship an order
        HttpResponse<String> deniedUrl = send(request("/api/orders/1/ship")
                .header("X-Request-Id", "req-deny-4")
                .header("Authorization", "Bearer " + token)
                .POST(BodyPublishers.noBody()));
        assertEquals(403, deniedUrl.statusCode());
        assertEquals("req-deny-4", header(deniedUrl, "X-Request-Id"));

        String logs = output.getAll();
        assertThat(logs).contains("rid=req-fail-1 SECURITY : login.failed user=alice reason=BadCredentialsException");
        assertThat(logs).contains("rid=req-ok-2 SECURITY : login.ok user=alice type=UsernamePasswordAuthenticationToken");
        assertThat(logs).contains("rid=req-deny-3 SECURITY : access.denied user=alice object=OrderService.history(bob)");
        assertThat(logs).contains("rid=req-deny-4 SECURITY : access.denied user=alice object=POST /api/orders/1/ship");
    }

    // --- helpers ----------------------------------------------------------------------------------

    private void assertHardenedHeaders(HttpResponse<?> res) {
        assertEquals("nosniff", header(res, "X-Content-Type-Options"));
        assertEquals("DENY", header(res, "X-Frame-Options"));
        assertEquals("max-age=31536000 ; includeSubDomains", header(res, "Strict-Transport-Security"));
        assertEquals("default-src 'self'; frame-ancestors 'none'; object-src 'none'",
                header(res, "Content-Security-Policy"));
        assertEquals("strict-origin-when-cross-origin", header(res, "Referrer-Policy"));
        assertThat(header(res, "X-Request-Id")).isNotBlank();
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        HttpResponse<String> res = send(request("/api/auth/login")
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}")));
        assertEquals(200, res.statusCode());
        return extractToken(res.body());
    }

    private static String extractToken(String body) {
        Matcher m = TOKEN.matcher(body);
        assertTrue(m.find(), body);
        return m.group(1);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder req) throws Exception {
        return client.send(req.build(), BodyHandlers.ofString());
    }

    private static String header(HttpResponse<?> res, String name) {
        return res.headers().firstValue(name).orElse("");
    }
}
