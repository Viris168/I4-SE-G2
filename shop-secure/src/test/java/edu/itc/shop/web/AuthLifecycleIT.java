package edu.itc.shop.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import edu.itc.shop.domain.AuditEvent;
import edu.itc.shop.domain.AuditType;
import edu.itc.shop.domain.RefreshToken;
import edu.itc.shop.domain.Role;
import edu.itc.shop.domain.UserAccount;
import edu.itc.shop.repository.AuditEventRepository;
import edu.itc.shop.repository.RefreshTokenRepository;
import edu.itc.shop.repository.UserAccountRepository;
import edu.itc.shop.security.LoginRateLimiter;
import edu.itc.shop.service.AccountService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Challenge end to end on a real server: refresh-token rotation and reuse detection, logout,
 * account lockout, the login rate limiter and the admin-only audit log.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "JWT_SECRET=test-only-secret-0123456789-0123456789-abcdefghij",
        "shop.seed.admin-password=Admin-pass-123",
        "shop.seed.customer-password=Alice-pass-123"
})
class AuthLifecycleIT {

    private static final String ALICE = "{\"username\":\"alice\",\"password\":\"Alice-pass-123\"}";
    private static final String ADMIN = "{\"username\":\"admin\",\"password\":\"Admin-pass-123\"}";

    private final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @Value("${local.server.port}")
    private int port;

    @Autowired private LoginRateLimiter rateLimiter;
    @Autowired private AccountService accounts;
    @Autowired private UserAccountRepository users;
    @Autowired private RefreshTokenRepository refreshTokens;
    @Autowired private AuditEventRepository auditEvents;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }

    @Test
    void refreshRotatesAndReuseRevokesTheWholeFamily() throws Exception {
        JsonNode first = json(login(ALICE), 200);
        assertEquals(604800, first.get("refreshExpiresIn").asLong());
        String access1 = first.get("token").asString();
        String refresh1 = first.get("refreshToken").asString();
        assertEquals(43, refresh1.length());   // 32 random bytes, base64url without padding
        assertEquals(200, send(get("/api/orders", access1)).statusCode());

        JsonNode second = json(refresh(refresh1), 200);
        String access2 = second.get("token").asString();
        String refresh2 = second.get("refreshToken").asString();
        assertNotEquals(refresh1, refresh2);
        assertEquals(200, send(get("/api/orders", access2)).statusCode());

        // the rotated token comes back: treated as stolen
        HttpResponse<String> reuse = refresh(refresh1);
        assertProblem(reuse, 401, "Refresh token invalid", "/api/auth/refresh");

        // ...so the newest token of the family is dead too
        assertProblem(refresh(refresh2), 401, "Refresh token invalid", "/api/auth/refresh");

        RefreshToken stored1 = refreshTokens.findAll().stream()
                .filter(t -> t.getTokenHash().equals(sha256(refresh1))).findFirst().orElseThrow();
        List<RefreshToken> family = refreshTokens.findAll().stream()
                .filter(t -> t.getFamilyId().equals(stored1.getFamilyId())).toList();
        assertEquals(2, family.size());
        assertTrue(family.stream().allMatch(RefreshToken::isRevoked));
        assertTrue(refreshTokens.findAll().stream().noneMatch(t -> t.getTokenHash().equals(refresh1)),
                "only hashes are stored");
        assertThat(types("alice")).contains(AuditType.TOKEN_REFRESHED, AuditType.REFRESH_REUSE_DETECTED);
    }

    @Test
    void logoutRevokesTheFamily() throws Exception {
        JsonNode tokens = json(login(ALICE), 200);
        String refresh1 = tokens.get("refreshToken").asString();
        String refresh2 = json(refresh(refresh1), 200).get("refreshToken").asString();

        assertEquals(204, post("/api/auth/logout", "{\"refreshToken\":\"" + refresh2 + "\"}").statusCode());
        assertProblem(refresh(refresh2), 401, "Refresh token invalid", "/api/auth/refresh");
        assertThat(types("alice")).contains(AuditType.LOGOUT);
    }

    @Test
    void fiveFailuresLockTheAccountWithoutTellingTheClient() throws Exception {
        accounts.register("carol", "Carol-pass-123", Set.of(Role.CUSTOMER));
        String wrong = "{\"username\":\"carol\",\"password\":\"nope\"}";

        String firstBody = null;
        for (int i = 0; i < 5; i++) {
            HttpResponse<String> res = login(wrong);
            assertProblem(res, 401, "Bad credentials", "/api/auth/login");
            firstBody = firstBody == null ? res.body() : firstBody;
            assertEquals(firstBody, res.body());
        }
        UserAccount carol = users.findByUsername("carol").orElseThrow();
        assertTrue(carol.isLocked());
        assertEquals(5, carol.getFailedAttempts());

        rateLimiter.reset();   // get past the limiter to reach the lock check
        HttpResponse<String> correct = login("{\"username\":\"carol\",\"password\":\"Carol-pass-123\"}");
        assertProblem(correct, 401, "Bad credentials", "/api/auth/login");
        assertEquals(firstBody, correct.body());   // same answer as a wrong password: no enumeration

        List<AuditType> types = types("carol");
        assertEquals(4, types.stream().filter(AuditType.LOGIN_FAILED::equals).count());
        assertEquals(2, types.stream().filter(AuditType.LOCKED::equals).count());   // the 5th failure + the locked attempt
    }

    @Test
    void successfulLoginResetsTheFailureCounter() throws Exception {
        accounts.register("dave", "Dave-pass-1234", Set.of(Role.CUSTOMER));
        login("{\"username\":\"dave\",\"password\":\"nope\"}");
        login("{\"username\":\"dave\",\"password\":\"nope\"}");
        assertEquals(2, users.findByUsername("dave").orElseThrow().getFailedAttempts());

        json(login("{\"username\":\"dave\",\"password\":\"Dave-pass-1234\"}"), 200);
        assertEquals(0, users.findByUsername("dave").orElseThrow().getFailedAttempts());
        assertThat(types("dave")).contains(AuditType.LOGIN_OK);
    }

    @Test
    void sixthLoginWithinAMinuteIs429WithRetryAfter() throws Exception {
        String wrong = "{\"username\":\"mallory\",\"password\":\"nope\"}";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, login(wrong).statusCode());
        }
        HttpResponse<String> limited = login(wrong);
        assertEquals(429, limited.statusCode());
        assertTrue(limited.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        long retryAfter = Long.parseLong(limited.headers().firstValue("Retry-After").orElseThrow());
        assertTrue(retryAfter >= 1 && retryAfter <= 60, "Retry-After " + retryAfter);
        assertTrue(limited.body().contains("\"status\":429"), limited.body());
    }

    @Test
    void auditLogIsAdminOnlyAndRecordsAccessDenied() throws Exception {
        String alice = json(login(ALICE), 200).get("token").asString();
        String admin = json(login(ADMIN), 200).get("token").asString();

        assertEquals(403, send(request("/api/orders/1/ship")
                .header("Authorization", "Bearer " + alice).POST(BodyPublishers.noBody())).statusCode());
        assertEquals(403, send(get("/api/admin/audit", alice)).statusCode());

        HttpResponse<String> audit = send(get("/api/admin/audit?username=alice", admin));
        assertEquals(200, audit.statusCode());
        JsonNode rows = json.readTree(audit.body());
        assertTrue(rows.isArray() && !rows.isEmpty());
        for (JsonNode row : rows) {
            assertEquals("alice", row.get("username").asString());
        }
        assertThat(audit.body()).contains("\"type\":\"ACCESS_DENIED\"", "\"type\":\"LOGIN_OK\"");
        JsonNode denied = null;
        for (JsonNode row : rows) {
            if (row.get("type").asString().equals("ACCESS_DENIED")) { denied = row; break; }
        }
        assertEquals("127.0.0.1", denied.get("ip").asString());
        assertTrue(denied.get("requestId").asString().length() > 0);
    }

    @Test
    void roleChangeIsAuditedAndShowsUpOnRefresh() throws Exception {
        accounts.register("erin", "Erin-pass-1234", Set.of(Role.CUSTOMER));
        String erinRefresh = json(login("{\"username\":\"erin\",\"password\":\"Erin-pass-1234\"}"), 200)
                .get("refreshToken").asString();
        String admin = json(login(ADMIN), 200).get("token").asString();

        HttpResponse<String> changed = send(request("/api/admin/users/erin/roles")
                .header("Authorization", "Bearer " + admin).header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString("{\"roles\":[\"CUSTOMER\",\"ADMIN\"]}")));
        assertEquals(200, changed.statusCode(), changed.body());

        List<AuditEvent> events = auditEvents.findTop200ByUsernameOrderByIdDesc("erin");
        AuditEvent roleChanged = events.stream().filter(e -> e.getType() == AuditType.ROLE_CHANGED).findFirst().orElseThrow();
        assertEquals("[CUSTOMER] -> [CUSTOMER, ADMIN] by admin", roleChanged.getDetail());

        // the refreshed access token carries the new role
        String newAccess = json(refresh(erinRefresh), 200).get("token").asString();
        assertEquals(200, send(get("/api/admin/audit", newAccess)).statusCode());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<AuditType> types(String username) {
        return auditEvents.findTop200ByUsernameOrderByIdDesc(username).stream().map(AuditEvent::getType).toList();
    }

    private HttpResponse<String> login(String body) throws Exception {
        return post("/api/auth/login", body);
    }

    private HttpResponse<String> refresh(String refreshToken) throws Exception {
        return post("/api/auth/refresh", "{\"refreshToken\":\"" + refreshToken + "\"}");
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return send(request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder get(String path, String bearer) {
        return request(path).header("Authorization", "Bearer " + bearer).GET();
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private JsonNode json(HttpResponse<String> response, int expectedStatus) throws Exception {
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private static void assertProblem(HttpResponse<String> response, int status, String detail, String instance) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"));
        assertTrue(response.body().contains("\"detail\":\"" + detail + "\""), response.body());
        assertTrue(response.body().contains("\"instance\":\"" + instance + "\""), response.body());
    }
}
