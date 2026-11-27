package edu.itc.shop.web;

import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.UserAccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij",
        "shop.seed.admin-password=Admin-pass-123",
        "shop.seed.customer-password=Alice-pass-123"
})
class RegisterControllerTest {

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsAdminAndAliceIdempotently() {
        Optional<UserAccount> admin = userRepository.findByUsername("admin");
        assertTrue(admin.isPresent());
        assertTrue(admin.get().isEnabled());
        assertThat(admin.get().getRoles()).containsExactlyInAnyOrder(Role.ADMIN, Role.CUSTOMER);
        assertTrue(admin.get().getPasswordHash().startsWith("{bcrypt}$2a$10$"));
        assertFalse(admin.get().toString().contains(admin.get().getPasswordHash()));

        Optional<UserAccount> alice = userRepository.findByUsername("alice");
        assertTrue(alice.isPresent());
        assertTrue(alice.get().isEnabled());
        assertThat(alice.get().getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER);
        assertTrue(alice.get().getPasswordHash().startsWith("{bcrypt}$2a$10$"));
    }

    @Test
    void registerWithInvalidPasswordReturns400ProblemJsonWithFieldErrors() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"bob_invalid\",\"password\":\"short\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        String responseBody = response.body();
        assertThat(responseBody).contains("\"title\":\"Bad Request\"");
        assertThat(responseBody).contains("\"status\":400");
        assertThat(responseBody).contains("\"detail\":\"Validation failed\"");
        assertThat(responseBody).contains("\"instance\":\"/api/register\"");
        assertThat(responseBody).contains("size must be between 12 and 72");
        assertThat(responseBody).contains("must contain an upper-case letter, a lower-case letter and a digit");
    }

    @Test
    void registerWithValidPasswordReturns201AndStoresBcryptHash() throws Exception {
        HttpRequest registerReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"bob\",\"password\":\"Correct-Horse-9\"}"))
                .build();

        HttpResponse<String> registerRes = client.send(registerReq, HttpResponse.BodyHandlers.ofString());

        assertThat(registerRes.statusCode()).isEqualTo(201);
        assertThat(registerRes.body()).isEqualTo("{\"username\":\"bob\"}");
        assertFalse(registerRes.body().contains("Correct-Horse-9"));

        // Verify entity in repository
        UserAccount bob = userRepository.findByUsername("bob").orElseThrow();
        assertThat(bob.getUsername()).isEqualTo("bob");
        assertTrue(bob.isEnabled());
        assertThat(bob.getRoles()).containsExactly(Role.CUSTOMER);
        assertTrue(bob.getPasswordHash().startsWith("{bcrypt}$2a$10$"));
        assertFalse(bob.toString().contains(bob.getPasswordHash()));
        assertFalse(bob.toString().contains("Correct-Horse-9"));

        // The stored value is a bcrypt hash; only checked, never printed
        String stored = jdbcTemplate.queryForObject(
                "SELECT PASSWORD_HASH FROM USERS WHERE USERNAME = ?", String.class, "bob");
        assertThat(stored).startsWith("{bcrypt}$2a$10$").doesNotContain("Correct-Horse-9");

        // Log in as the new user and call /api/me with the Bearer token
        HttpRequest loginReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"bob\",\"password\":\"Correct-Horse-9\"}"))
                .build();
        HttpResponse<String> loginRes = client.send(loginReq, HttpResponse.BodyHandlers.ofString());
        assertThat(loginRes.statusCode()).isEqualTo(200);
        String token = JsonMapper.builder().build().readTree(loginRes.body()).get("token").asString();

        HttpRequest meReq = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();

        HttpResponse<String> meRes = client.send(meReq, HttpResponse.BodyHandlers.ofString());
        assertThat(meRes.statusCode()).isEqualTo(200);
        assertThat(meRes.body()).contains("\"name\":\"bob\"");
        assertThat(meRes.body()).contains("\"roles\":[\"ROLE_CUSTOMER\"]");
    }

    @Test
    void registerWithDuplicateUsernameReturns409Conflict() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"alice\",\"password\":\"Different-Pass-123\"}"))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(response.body()).contains("\"status\":409");
    }

    @Test
    void unauthenticatedAccessToApiMeReturns401() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/me"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }
}
