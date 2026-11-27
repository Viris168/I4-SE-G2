package edu.itc.shop.filtertest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.zip.GZIPInputStream;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Task 4: the filters and the interceptor against a real Grizzly server. Uses java.net.http so the
 * test controls every header itself (the client never adds Accept-Encoding on its own).
 */
class FiltersTest {

    private static final String JSON = "{\"id\":7,\"name\":\"Kettle\"}";

    private static HttpServer server;
    private static URI base;
    // HTTP/1.1 like curl: the default HTTP/2 upgrade attempt loses Content-Type on Grizzly
    private static final HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @BeforeAll
    static void start() {
        ResourceConfig app = new ResourceConfig()
                .packages("edu.itc.shop.api.filter") // the same scan Main does for edu.itc.shop.api
                .register(ItemsResource.class);
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://localhost:0/api/"), app);
        base = URI.create("http://localhost:" + server.getListener("grizzly").getPort() + "/api/");
    }

    @AfterAll
    static void stop() {
        server.shutdownNow();
    }

    @BeforeEach
    void resetHits() {
        ItemsResource.HITS.set(0);
    }

    // --- correlation id ---------------------------------------------------------------------------

    @Test
    void echoesTheCorrelationIdTheClientSent() throws Exception {
        var response = send(get("items/1").header("X-Correlation-Id", "order-check-42"));

        assertEquals(200, response.statusCode());
        assertEquals("order-check-42", header(response, "X-Correlation-Id"));
    }

    @Test
    void generatesACorrelationIdWhenNoneWasSent() throws Exception {
        String id = header(send(get("items/1")), "X-Correlation-Id");

        assertNotNull(id);
        assertEquals(36, id.length()); // a UUID
    }

    // --- CORS -------------------------------------------------------------------------------------

    @Test
    void corsHeadersAreOnEveryResponse() throws Exception {
        var response = send(get("items/1"));

        assertEquals("http://localhost:5173", header(response, "Access-Control-Allow-Origin"));
        assertEquals("Location,ETag,Link,X-Total-Count,X-Correlation-Id",
                header(response, "Access-Control-Expose-Headers"));
    }

    @Test
    void preflightIsAnsweredWithoutReachingTheResource() throws Exception {
        var response = send(request("items")
                .method("OPTIONS", BodyPublishers.noBody())
                .header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST"));

        assertEquals(200, response.statusCode());
        assertEquals("GET,POST,PUT,PATCH,DELETE", header(response, "Access-Control-Allow-Methods"));
        assertEquals("Content-Type,X-Api-Key,X-Correlation-Id,If-Match", header(response, "Access-Control-Allow-Headers"));
        assertEquals(0, ItemsResource.HITS.get());
    }

    // --- API key ----------------------------------------------------------------------------------

    @Test
    void writeWithoutKeyIs401ProblemJsonBeforeMatching() throws Exception {
        var response = send(post("items"));

        assertEquals(401, response.statusCode());
        assertEquals("ApiKey realm=\"shop\"", header(response, "WWW-Authenticate"));
        assertTrue(header(response, "Content-Type").startsWith("application/problem+json"));
        assertTrue(response.body().contains("\"detail\":\"Missing or invalid X-Api-Key\""));
        assertTrue(response.body().contains("\"instance\":\"/api/items\""));
        // the abort still passes through the response filters
        assertNotNull(header(response, "X-Correlation-Id"));
        assertEquals("http://localhost:5173", header(response, "Access-Control-Allow-Origin"));
        assertEquals(0, ItemsResource.HITS.get());
    }

    @Test
    void writeWithWrongKeyIs401() throws Exception {
        assertEquals(401, send(post("items").header("X-Api-Key", "wrong")).statusCode());
        assertEquals(0, ItemsResource.HITS.get());
    }

    @Test
    void writeWithValidKeyGetsThrough() throws Exception {
        var response = send(post("items").header("X-Api-Key", "demo-123"));

        assertEquals(201, response.statusCode());
        assertEquals(1, ItemsResource.HITS.get());
    }

    @Test
    void readsStayPublic() throws Exception {
        assertEquals(200, send(get("items/1")).statusCode());
    }

    @Test
    void preMatchingKeyCheckRejectsEvenUnknownPaths() throws Exception {
        // 401, not 404: the key is checked before Jersey even looks for a matching resource
        assertEquals(401, send(request("no-such-path").POST(BodyPublishers.ofString(JSON))).statusCode());
    }

    // --- gzip -------------------------------------------------------------------------------------

    @Test
    void listIsGzippedWhenTheClientAcceptsGzip() throws Exception {
        var plain = client.send(get("items").build(), BodyHandlers.ofByteArray());
        var zipped = client.send(get("items").header("Accept-Encoding", "gzip, deflate").build(),
                BodyHandlers.ofByteArray());

        assertEquals("gzip", zipped.headers().firstValue("Content-Encoding").orElse(null));
        assertEquals("Accept-Encoding", zipped.headers().firstValue("Vary").orElse(null));
        assertTrue(zipped.body().length < plain.body().length);
        assertArrayEquals(plain.body(), gunzip(zipped.body()));
    }

    @Test
    void listIsNotCompressedWithoutAcceptEncoding() throws Exception {
        var response = client.send(get("items").build(), BodyHandlers.ofByteArray());

        assertFalse(response.headers().firstValue("Content-Encoding").isPresent());
        assertTrue(new String(response.body()).startsWith("[{\"id\":1"));
    }

    @Test
    void methodsWithoutCompressAreNeverGzipped() throws Exception {
        var response = send(get("items/1").header("Accept-Encoding", "gzip"));

        assertFalse(response.headers().firstValue("Content-Encoding").isPresent());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(base.resolve(path));
    }

    private static HttpRequest.Builder get(String path) {
        return request(path).GET();
    }

    private static HttpRequest.Builder post(String path) {
        return request(path).header("Content-Type", "application/json").POST(BodyPublishers.ofString(JSON));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return client.send(request.build(), BodyHandlers.ofString());
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static byte[] gunzip(byte[] body) throws IOException {
        try (var in = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return in.readAllBytes();
        }
    }
}
