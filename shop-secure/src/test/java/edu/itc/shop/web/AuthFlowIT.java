package edu.itc.shop.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.context.TestPropertySource;


/**
 * Task 4 end to end on a real server: login, use the token, reject expired, malformed and wrong
 * credentials, through Task 1's real filter chain and Task 3's real order API. Until Task 2 is
 * merged, the user is a test-only stand-in (nested here, so the application never scans it).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij")
@Import(AuthFlowIT.StandIns.class)
class AuthFlowIT {

    private static final Pattern TOKEN = Pattern.compile("\"token\":\"([^\"]+)\"");
    private static final String ALICE = "{\"username\":\"alice\",\"password\":\"Alice-pass-123\"}";

    // HTTP/1.1 like curl; the default HTTP/2 upgrade attempt can drop headers on the test server
    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JwtDecoder decoder;

    @Autowired
    private JwtEncoder encoder;

    @Test
    void loginReturnsAnHs256TokenWithAllClaims() throws Exception {
        HttpResponse<String> response = login(ALICE);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"tokenType\":\"Bearer\""));
        assertTrue(response.body().contains("\"expiresIn\":900"));

        Jwt jwt = decoder.decode(token(response)); // also proves signature and issuer are valid
        assertEquals("HS256", jwt.getHeaders().get("alg"));
        assertEquals("shop-api", jwt.getClaimAsString("iss"));
        assertEquals("alice", jwt.getSubject());
        assertEquals(List.of("ROLE_CUSTOMER"), jwt.getClaimAsStringList("roles"));
        assertNotNull(jwt.getId());
        assertEquals(Duration.ofMinutes(15), Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()));
    }

    @Test
    void tokenGivesAccessAndNoSessionCookieAppears() throws Exception {
        HttpResponse<String> loginResponse = login(ALICE);
        String token = token(loginResponse);

        HttpResponse<String> create = send(request("/api/orders").header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString("{\"productId\":1,\"quantity\":2}")));
        HttpResponse<String> list = send(request("/api/orders").header("Authorization", "Bearer " + token).GET());

        assertEquals(201, create.statusCode());
        assertEquals(200, list.statusCode());
        assertTrue(list.body().contains("\"customer\":\"alice\""));
        for (HttpResponse<String> r : List.of(loginResponse, list, create)) {
            assertFalse(r.headers().firstValue("Set-Cookie").isPresent(), "stateless: no session cookie");
        }
    }

    @Test
    void expiredTokenIs401ProblemJsonWithBearerChallenge() throws Exception {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("shop-api").subject("alice")
                .issuedAt(past).expiresAt(past.plus(Duration.ofMinutes(15)))
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of("ROLE_CUSTOMER"))
                .build();
        String expired = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        HttpResponse<String> response = send(request("/api/orders").header("Authorization", "Bearer " + expired).GET());

        assertEquals(401, response.statusCode());
        String challenge = response.headers().firstValue("WWW-Authenticate").orElse("");
        assertTrue(challenge.startsWith("Bearer error=\"invalid_token\""), challenge);
        assertTrue(challenge.contains("expired"), challenge);
        assertProblem(response, "Token expired or invalid", "/api/orders");
    }

    @Test
    void malformedTokenIs401ProblemJson() throws Exception {
        HttpResponse<String> response = send(request("/api/orders").header("Authorization", "Bearer not.a.jwt").GET());

        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
        assertProblem(response, "Token expired or invalid", "/api/orders");
    }

    @Test
    void noTokenIs401() throws Exception {
        assertEquals(401, send(request("/api/orders").GET()).statusCode());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameGeneric401() throws Exception {
        HttpResponse<String> wrongPassword = login("{\"username\":\"alice\",\"password\":\"wrong\"}");
        HttpResponse<String> unknownUser = login("{\"username\":\"nobody\",\"password\":\"Alice-pass-123\"}");

        assertProblem(wrongPassword, "Bad credentials", "/api/auth/login");
        assertProblem(unknownUser, "Bad credentials", "/api/auth/login");
        assertEquals(wrongPassword.body(), unknownUser.body()); // nothing tells which part was wrong
    }

    // --- helpers ----------------------------------------------------------------------------------

    private HttpResponse<String> login(String body) throws Exception {
        return send(request("/api/auth/login").header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private static String token(HttpResponse<String> response) {
        Matcher m = TOKEN.matcher(response.body());
        assertTrue(m.find(), response.body());
        return m.group(1);
    }

    private static void assertProblem(HttpResponse<String> response, String detail, String instance) {
        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertTrue(response.body().contains("\"detail\":\"" + detail + "\""), response.body());
        assertTrue(response.body().contains("\"instance\":\"" + instance + "\""), response.body());
    }

    /**
     * Test-only stand-in for Task 2; the filter chain is Task 1's real SecurityConfig and
     * /api/orders is Task 3's real OrderController. Remove when the JPA users (Task 2) are merged.
     */
    @TestConfiguration
    static class StandIns {

        /** Task 2 stand-in: one customer with a bcrypt hash. @Primary over Task 1's temporary users. */
        @Bean
        @Primary
        UserDetailsService testUsers() {
            PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
            return new InMemoryUserDetailsManager(User.withUsername("alice")
                    .password(encoder.encode("Alice-pass-123")).roles("CUSTOMER").build());
        }

    }
}
