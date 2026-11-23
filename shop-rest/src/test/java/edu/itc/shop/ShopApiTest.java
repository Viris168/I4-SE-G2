package edu.itc.shop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.jupiter.api.Test;

/**
 * The whole application ({@link Main#config()}, same as production) on an in-memory Grizzly server.
 * JerseyTest starts a new container per test and {@code config()} builds new repositories every time,
 * so each test sees the seeded data and an empty idempotency store.
 * <p>
 * One test (at least) per promised status code: 200, 201, 204, 304, 400, 401, 404, 406, 409, 412, 415, 422, 428.
 */
class ShopApiTest extends JerseyTest {

    private static final String KEY = "demo-123";
    private static final String PROBLEM = "application/problem+json";
    private static final String KETTLE_X2 = """
            {"lines":[{"productId":1,"quantity":2}]}""";

    @Override
    protected Application configure() {
        forceSet(TestProperties.CONTAINER_PORT, "0");            // any free port; no clash with a running server
        return Main.config();                                    // same app as production, fresh state
    }

    // --- 200 ------------------------------------------------------------------------------------

    @Test
    void ok200_orderCanBePaidByFollowingRelsOnly() {
        Response placed = placeOrder("flow-1", KETTLE_X2);
        assertEquals(201, placed.getStatus());

        // follow rel="pay" (PUT) without building any URL by hand
        URI pay = placed.getLink("pay").getUri();
        Response paid = client().target(pay).request().header("X-Api-Key", KEY).put(Entity.text(""));
        assertEquals(200, paid.getStatus());
        assertEquals(MediaType.APPLICATION_JSON_TYPE, paid.getMediaType());
        JsonObject body = json(paid);
        assertEquals("PAID", body.getString("status"));
        assertTrue(body.getJsonObject("_links").containsKey("self"));
        assertFalse(body.getJsonObject("_links").containsKey("pay"), "pay disappears once PAID");
        assertFalse(body.getJsonObject("_links").containsKey("cancel"), "cancel disappears once PAID");
        assertNull(paid.getLink("pay"));
        assertNotNull(paid.getLink("self"));

        // follow rel="self"
        Response self = client().target(paid.getLink("self").getUri()).request().get();
        assertEquals(200, self.getStatus());
        assertEquals("PAID", json(self).getString("status"));
    }

    @Test
    void ok200_cancelFollowsRelAndRestoresStock() {
        int before = stockOf(1);
        Response placed = placeOrder("cancel-1", KETTLE_X2);
        assertEquals(before - 2, stockOf(1), "stock is reserved when the order is placed");
        // built from UriInfo: JerseyTest mounts the app at "/", production at "/api/"
        assertEquals(getBaseUri().resolve("products/1").toString(),
                     json(placed).getJsonObject("_links").getString("product-1"));

        Response cancelled = client().target(placed.getLink("cancel").getUri()).request()
                .header("X-Api-Key", KEY).delete();
        assertEquals(200, cancelled.getStatus());
        assertEquals("CANCELLED", json(cancelled).getString("status"));
        assertEquals(before, stockOf(1), "stock is restored on cancel");
    }

    // --- 201 + idempotency ----------------------------------------------------------------------

    @Test
    void created201_placingTheSameOrderTwiceCreatesOneOrder() {
        int before = stockOf(1);
        Response first = placeOrder("k-1", KETTLE_X2);
        Response second = placeOrder("k-1", KETTLE_X2);

        assertEquals(201, first.getStatus());
        assertEquals(201, second.getStatus());
        assertEquals("false", first.getHeaderString("Idempotency-Replayed"));
        assertEquals("true", second.getHeaderString("Idempotency-Replayed"));
        assertNotNull(first.getLocation());
        assertEquals(first.getLocation(), second.getLocation());
        assertEquals(first.getLocation(), first.getLink("self").getUri());
        assertNotNull(first.getLink("pay"));
        assertNotNull(first.getLink("cancel"));
        assertEquals(before - 2, stockOf(1), "only one order reserved stock");

        JsonObject order = json(first);
        assertEquals("NEW", order.getString("status"));
        assertEquals("49.80", order.getJsonNumber("total").bigDecimalValue().toPlainString());
    }

    @Test
    void created201_concurrentRetriesCreateOneOrder() throws Exception {
        int before = stockOf(1);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Response>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            calls.add(() -> { start.await(); return placeOrder("race-1", KETTLE_X2); });
        }
        List<Future<Response>> futures = new ArrayList<>();
        for (var c : calls) futures.add(pool.submit(c));
        start.countDown();

        var locations = new HashSet<URI>();
        int created = 0;
        for (var f : futures) {
            Response r = f.get();
            assertEquals(201, r.getStatus());
            locations.add(r.getLocation());
            if ("false".equals(r.getHeaderString("Idempotency-Replayed"))) created++;
        }
        pool.shutdown();

        assertEquals(1, created, "exactly one request really placed the order");
        assertEquals(1, locations.size(), "every retry points to the same order");
        assertEquals(before - 2, stockOf(1), "stock reserved once");
    }

    // --- 204 ------------------------------------------------------------------------------------

    @Test
    void noContent204_deleteProduct() {
        Response res = target("products/2").request().header("X-Api-Key", KEY).delete();
        assertEquals(204, res.getStatus());
        assertFalse(res.hasEntity());
        assertEquals(404, target("products/2").request().get().getStatus());
    }

    // --- 304 ------------------------------------------------------------------------------------

    @Test
    void notModified304_whenIfNoneMatchHasTheCurrentEtag() {
        Response first = target("products/1").request(MediaType.APPLICATION_JSON).get();
        assertEquals(200, first.getStatus());
        String etag = first.getHeaderString(HttpHeaders.ETAG);
        assertNotNull(etag);
        assertTrue(first.getHeaderString(HttpHeaders.CACHE_CONTROL).contains("max-age=60"));

        Response again = target("products/1").request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.IF_NONE_MATCH, etag).get();
        assertEquals(304, again.getStatus());
        assertEquals(etag, again.getHeaderString(HttpHeaders.ETAG));
    }

    // --- 400 ------------------------------------------------------------------------------------

    @Test
    void badRequest400_invalidOrderLine() {
        Response res = placeOrder("bad-1", """
                {"lines":[{"productId":1,"quantity":0}]}""");
        JsonObject problem = assertProblem(res, 400);
        var errors = problem.getJsonArray("errors");
        assertEquals(1, errors.size());
        assertEquals("quantity", errors.getJsonObject(0).getString("field"));
    }

    @Test
    void badRequest400_invalidProductHasOneErrorPerConstraint() {
        Response res = target("products").request().header("X-Api-Key", KEY)
                .post(Entity.json("""
                        {"name":"","sku":"kettle","price":0,"stock":-1}"""));
        assertEquals(4, assertProblem(res, 400).getJsonArray("errors").size());
    }

    // --- 401 ------------------------------------------------------------------------------------

    @Test
    void unauthorized401_writeWithoutApiKey() {
        Response res = target("orders").request().header("Idempotency-Key", "k-401")
                .post(Entity.json(KETTLE_X2));
        assertProblem(res, 401);
        assertEquals("ApiKey realm=\"shop\"", res.getHeaderString(HttpHeaders.WWW_AUTHENTICATE));
    }

    // --- 404 ------------------------------------------------------------------------------------

    @Test
    void notFound404_unknownOrderAndUnknownProductInAnOrder() {
        assertProblem(target("orders/o-999").request().get(), 404);

        Response res = placeOrder("k-404", """
                {"lines":[{"productId":999,"quantity":1}]}""");
        assertTrue(assertProblem(res, 404).getString("detail").contains("999"));
        // nothing was stored for the failed attempt: the same key works once the body is fixed
        assertEquals(201, placeOrder("k-404-fixed", KETTLE_X2).getStatus());
    }

    // --- 406 ------------------------------------------------------------------------------------

    @Test
    void notAcceptable406_unsupportedAccept() {
        assertProblem(target("products/1").request("text/csv").get(), 406);
    }

    // --- 409 ------------------------------------------------------------------------------------

    @Test
    void conflict409_insufficientStockAndInvalidTransitions() {
        assertProblem(placeOrder("k-tea", """
                {"lines":[{"productId":3,"quantity":1}]}"""), 409);          // Tea has stock 0

        Response placed = placeOrder("k-409", KETTLE_X2);
        URI pay = placed.getLink("pay").getUri();
        URI cancel = placed.getLink("cancel").getUri();
        assertEquals(200, client().target(pay).request().header("X-Api-Key", KEY).put(Entity.text("")).getStatus());

        assertProblem(client().target(pay).request().header("X-Api-Key", KEY).put(Entity.text("")), 409);
        assertProblem(client().target(cancel).request().header("X-Api-Key", KEY).delete(), 409);
    }

    // --- 412 ------------------------------------------------------------------------------------

    @Test
    void preconditionFailed412_putWithStaleEtag() {
        Response res = target("products/1").request().header("X-Api-Key", KEY)
                .header(HttpHeaders.IF_MATCH, "\"0\"")
                .put(Entity.json("""
                        {"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}"""));
        assertProblem(res, 412);
    }

    // --- 415 ------------------------------------------------------------------------------------

    @Test
    void unsupportedMediaType415_orderAsPlainText() {
        Response res = target("orders").request().header("X-Api-Key", KEY).header("Idempotency-Key", "k-415")
                .post(Entity.text(KETTLE_X2));
        assertProblem(res, 415);
    }

    // --- 422 ------------------------------------------------------------------------------------

    @Test
    void unprocessable422_sameKeyDifferentBody() {
        assertEquals(201, placeOrder("k-422", KETTLE_X2).getStatus());
        Response res = placeOrder("k-422", """
                {"lines":[{"productId":2,"quantity":1}]}""");
        assertTrue(assertProblem(res, 422).getString("detail").contains("different body"));
    }

    // --- 428 ------------------------------------------------------------------------------------

    @Test
    void preconditionRequired428_orderWithoutIdempotencyKey() {
        Response res = target("orders").request().header("X-Api-Key", KEY).post(Entity.json(KETTLE_X2));
        assertProblem(res, 428);
    }

    @Test
    void preconditionRequired428_putWithoutIfMatch() {
        Response res = target("products/1").request().header("X-Api-Key", KEY)
                .put(Entity.json("""
                        {"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}"""));
        assertProblem(res, 428);
    }

    // --- OpenAPI --------------------------------------------------------------------------------

    @Test
    void openApiDocumentListsTheEndpointsAndStatusCodes() {
        Response res = target("openapi").request().get();
        assertEquals(200, res.getStatus());
        assertEquals("application/yaml", res.getMediaType().toString());
        String yaml = res.readEntity(String.class);
        assertTrue(yaml.contains("openapi: 3.1"), yaml.lines().findFirst().orElse(""));
        for (String path : List.of("/orders:", "/orders/{id}:", "/orders/{id}/payment:", "/products:", "/products/{id}:")) {
            assertTrue(yaml.contains(path), "missing path " + path);
        }
        for (String code : List.of("200", "201", "204", "304", "400", "401", "404", "406", "409", "412", "415", "422", "428")) {
            assertTrue(yaml.contains("\"" + code + "\":"), "missing status " + code);
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private Response placeOrder(String idempotencyKey, String body) {
        Invocation.Builder req = target("orders").request(MediaType.APPLICATION_JSON).header("X-Api-Key", KEY);
        if (idempotencyKey != null) req = req.header("Idempotency-Key", idempotencyKey);
        return req.post(Entity.json(body));
    }

    private int stockOf(long productId) {
        return json(target("products/" + productId).request(MediaType.APPLICATION_JSON).get()).getInt("stock");
    }

    private static JsonObject json(Response res) {
        try (var reader = Json.createReader(new StringReader(res.readEntity(String.class)))) {
            return reader.readObject();
        }
    }

    /** Asserts an RFC 9457 body with the given status and returns it. */
    private static JsonObject assertProblem(Response res, int status) {
        assertEquals(status, res.getStatus());
        assertEquals(PROBLEM, res.getMediaType().toString());
        JsonObject problem = json(res);
        assertEquals(status, problem.getInt("status"));
        for (String field : List.of("type", "title", "detail", "instance")) {
            assertTrue(problem.containsKey(field), "problem without " + field);
        }
        return problem;
    }
}
