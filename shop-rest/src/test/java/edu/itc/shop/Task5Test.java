package edu.itc.shop;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Task 5 integration tests against a real Grizzly server using {@link Main#config()}:
 * pagination + Link headers, ETag/304/Cache-Control, conditional PUT (428/412/200),
 * v1/v2 media-type versioning, and the /products/{id}/reviews sub-resource.
 */
class Task5Test {

    private static final String API_KEY = "demo-123";
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private HttpServer server;
    private URI base;

    @BeforeEach
    void start() {
        server = GrizzlyHttpServerFactory.createHttpServer(URI.create("http://localhost:0/api/"), Main.config());
        base = URI.create("http://localhost:" + server.getListener("grizzly").getPort() + "/api/");
    }

    @AfterEach
    void stop() {
        server.shutdownNow();
    }

    // --- pagination -------------------------------------------------------------------------------

    @Test
    void middlePageReturnsTotalCountAndAllFourLinks() throws Exception {
        var res = send(get("products?page=1&size=10"));

        assertEquals(200, res.statusCode());
        assertEquals("33", header(res, "X-Total-Count"));
        String links = allLinks(res);
        assertTrue(links.contains("page=0>; rel=\"first\"") || links.contains("page=0&size=10>; rel=\"first\""), links);
        assertTrue(links.contains("page=0>; rel=\"prev\"") || links.contains("page=0&size=10>; rel=\"prev\""), links);
        assertTrue(links.contains("page=2>; rel=\"next\"") || links.contains("page=2&size=10>; rel=\"next\""), links);
        assertTrue(links.contains("page=3>; rel=\"last\"") || links.contains("page=3&size=10>; rel=\"last\""), links);
        assertTrue(links.contains("size=10"), links);
        assertEquals(10, countProducts(res.body()));
    }

    @Test
    void firstPageOmitsPrevAndLastPageOmitsNext() throws Exception {
        var first = send(get("products?page=0&size=10"));
        assertEquals(200, first.statusCode());
        assertEquals(10, countProducts(first.body()));
        String firstLinks = allLinks(first);
        assertTrue(firstLinks.contains("rel=\"first\""), firstLinks);
        assertFalse(firstLinks.contains("rel=\"prev\""), firstLinks);
        assertTrue(firstLinks.contains("rel=\"next\""), firstLinks);
        assertTrue(firstLinks.contains("rel=\"last\""), firstLinks);

        var last = send(get("products?page=3&size=10"));
        assertEquals(200, last.statusCode());
        assertEquals(3, countProducts(last.body()));
        String lastLinks = allLinks(last);
        assertTrue(lastLinks.contains("rel=\"first\""), lastLinks);
        assertTrue(lastLinks.contains("rel=\"prev\""), lastLinks);
        assertFalse(lastLinks.contains("rel=\"next\""), lastLinks);
        assertTrue(lastLinks.contains("rel=\"last\""), lastLinks);
    }

    @Test
    void totalCountReflectsFilteringAndLinksPreserveQueryParams() throws Exception {
        var res = send(get("products?q=Kettle&size=10"));

        assertEquals(200, res.statusCode());
        assertEquals("1", header(res, "X-Total-Count"));
        assertEquals(1, countProducts(res.body()));
        String links = allLinks(res);
        assertTrue(links.contains("q=Kettle"), links);
        assertFalse(links.contains("rel=\"prev\""), links);
        assertFalse(links.contains("rel=\"next\""), links);
    }

    @Test
    void invalidPageAndSizeAre400ProblemJson() throws Exception {
        var negPage = send(get("products?page=-1"));
        assertEquals(400, negPage.statusCode());
        assertTrue(header(negPage, "Content-Type").startsWith("application/problem+json"));
        assertTrue(negPage.body().contains("\"field\":\"page\""));

        var zeroSize = send(get("products?size=0"));
        assertEquals(400, zeroSize.statusCode());
        assertTrue(header(zeroSize, "Content-Type").startsWith("application/problem+json"));

        var hugeSize = send(get("products?size=101"));
        assertEquals(400, hugeSize.statusCode());
        assertTrue(header(hugeSize, "Content-Type").startsWith("application/problem+json"));
    }

    @Test
    void paginatedListStillSupportsXml() throws Exception {
        var res = send(get("products?page=0&size=2").header("Accept", "application/xml"));

        assertEquals(200, res.statusCode());
        assertEquals("33", header(res, "X-Total-Count"));
        assertTrue(header(res, "Content-Type").startsWith("application/xml"));
        assertTrue(res.body().contains("<product "));
    }

    // --- caching & preconditions ------------------------------------------------------------------

    @Test
    void getItemReturnsEtagCacheControlAndHonoursIfNoneMatch() throws Exception {
        var first = send(get("products/1"));
        assertEquals(200, first.statusCode());
        assertEquals("\"1\"", header(first, "ETag"));
        String cc = header(first, "Cache-Control");
        assertNotNull(cc);
        assertTrue(cc.contains("private"), cc);
        assertTrue(cc.contains("no-transform"), cc);
        assertTrue(cc.contains("max-age=60"), cc);

        var notModified = send(get("products/1").header("If-None-Match", "\"1\""));
        assertEquals(304, notModified.statusCode());
        assertEquals("\"1\"", header(notModified, "ETag"));
        assertTrue(notModified.body().isEmpty());

        var stale = send(get("products/1").header("If-None-Match", "\"99\""));
        assertEquals(200, stale.statusCode());
        assertEquals("\"1\"", header(stale, "ETag"));
    }

    @Test
    void putRequiresIfMatchAndEnforcesOptimisticLocking() throws Exception {
        String body = "{\"name\":\"Kettle XL\",\"sku\":\"KT-01\",\"price\":29.90,\"stock\":12}";

        var missing = send(put("products/1", body));
        assertEquals(428, missing.statusCode());
        assertTrue(header(missing, "Content-Type").startsWith("application/problem+json"));
        assertTrue(missing.body().contains("\"status\":428"), missing.body());
        assertTrue(missing.body().contains("Precondition Required"), missing.body());

        var stale = send(put("products/1", body).header("If-Match", "\"0\""));
        assertEquals(412, stale.statusCode());
        assertTrue(header(stale, "Content-Type").startsWith("application/problem+json"));
        assertTrue(stale.body().contains("\"status\":412"), stale.body());
        assertTrue(stale.body().contains("Precondition Failed"), stale.body());

        var updated = send(put("products/1", body).header("If-Match", "\"1\""));
        assertEquals(200, updated.statusCode());
        assertEquals("\"2\"", header(updated, "ETag"));
        assertTrue(updated.body().contains("\"Kettle XL\""));

        var afterUpdateOldTag = send(get("products/1").header("If-None-Match", "\"1\""));
        assertEquals(200, afterUpdateOldTag.statusCode());
        assertEquals("\"2\"", header(afterUpdateOldTag, "ETag"));

        var afterUpdateNewTag = send(get("products/1").header("If-None-Match", "\"2\""));
        assertEquals(304, afterUpdateNewTag.statusCode());
    }

    @Test
    void patchStockStillWorksWithoutPreconditions() throws Exception {
        var res = send(request("products/2/stock")
                .header("X-Api-Key", API_KEY)
                .header("Content-Type", "application/json")
                .method("PATCH", BodyPublishers.ofString("{\"delta\":-5}")));

        assertEquals(200, res.statusCode());
        assertTrue(res.body().contains("\"stock\":35"), res.body());
    }

    // --- media-type versioning --------------------------------------------------------------------

    @Test
    void plainClientsGetV1AndV2MediaTypeReturnsV2Representation() throws Exception {
        var plain = send(get("products/1").header("Accept", "*/*"));
        assertEquals(200, plain.statusCode());
        assertEquals("application/json", header(plain, "Content-Type"));
        assertTrue(plain.body().contains("\"price\":24.90"), plain.body());
        assertFalse(plain.body().contains("\"available\""), plain.body());

        var explicitV1 = send(get("products/1").header("Accept", "application/vnd.shop.v1+json"));
        assertEquals(200, explicitV1.statusCode());
        assertEquals("application/vnd.shop.v1+json", header(explicitV1, "Content-Type"));
        assertTrue(explicitV1.body().contains("\"price\":24.90"), explicitV1.body());

        var v2InStock = send(get("products/1").header("Accept", "application/vnd.shop.v2+json"));
        assertEquals(200, v2InStock.statusCode());
        assertEquals("application/vnd.shop.v2+json", header(v2InStock, "Content-Type"));
        assertEquals("\"1\"", header(v2InStock, "ETag"));
        assertTrue(v2InStock.body().contains("\"available\":true"), v2InStock.body());
        assertTrue(v2InStock.body().contains("\"price\":{\"amount\":24.90,\"currency\":\"EUR\"}"), v2InStock.body());

        var v2OutOfStock = send(get("products/3").header("Accept", "application/vnd.shop.v2+json"));
        assertEquals(200, v2OutOfStock.statusCode());
        assertTrue(v2OutOfStock.body().contains("\"available\":false"), v2OutOfStock.body());

        var v2Cached = send(get("products/1")
                .header("Accept", "application/vnd.shop.v2+json")
                .header("If-None-Match", "\"1\""));
        assertEquals(304, v2Cached.statusCode());

        var xml = send(get("products/1").header("Accept", "application/xml"));
        assertEquals(200, xml.statusCode());
        assertTrue(header(xml, "Content-Type").startsWith("application/xml"));
        assertTrue(xml.body().contains("<product id=\"1\">"));
    }

    // --- reviews sub-resource ---------------------------------------------------------------------

    @Test
    void reviewsSubResourceCreatesListsValidatesAnd404sUnknownProduct() throws Exception {
        var empty = send(get("products/1/reviews"));
        assertEquals(200, empty.statusCode());
        assertEquals("[]", empty.body());

        var created = send(post("products/1/reviews",
                "{\"stars\":5,\"author\":\"Ana\",\"text\":\"Boils fast.\"}"));
        assertEquals(201, created.statusCode());
        String location = header(created, "Location");
        assertNotNull(location);
        assertTrue(location.endsWith("/api/products/1/reviews/1"), location);
        assertTrue(created.body().contains("\"author\":\"Ana\""));
        assertTrue(created.body().contains("\"productId\":1"));

        var listed = send(get("products/1/reviews"));
        assertEquals(200, listed.statusCode());
        assertTrue(listed.body().contains("\"Boils fast.\""));

        var fetchedByLocation = send(HttpRequest.newBuilder(URI.create(location)).GET());
        assertEquals(200, fetchedByLocation.statusCode());
        assertTrue(fetchedByLocation.body().contains("\"Ana\""));

        String tooLong = "x".repeat(501);
        var invalid = send(post("products/1/reviews",
                "{\"stars\":6,\"author\":\" \",\"text\":\"" + tooLong + "\"}"));
        assertEquals(400, invalid.statusCode());
        assertTrue(header(invalid, "Content-Type").startsWith("application/problem+json"));
        assertTrue(invalid.body().contains("\"field\":\"stars\""), invalid.body());
        assertTrue(invalid.body().contains("\"field\":\"author\""), invalid.body());
        assertTrue(invalid.body().contains("\"field\":\"text\""), invalid.body());

        var unknownGet = send(get("products/999/reviews"));
        assertEquals(404, unknownGet.statusCode());
        assertTrue(header(unknownGet, "Content-Type").startsWith("application/problem+json"));

        var unknownPost = send(post("products/999/reviews",
                "{\"stars\":4,\"author\":\"Bo\",\"text\":\"Nice\"}"));
        assertEquals(404, unknownPost.statusCode());
        assertTrue(header(unknownPost, "Content-Type").startsWith("application/problem+json"));
    }

    // --- helpers ----------------------------------------------------------------------------------

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(base.resolve(path));
    }

    private HttpRequest.Builder get(String path) {
        return request(path).GET();
    }

    private HttpRequest.Builder put(String path, String json) {
        return request(path)
                .header("X-Api-Key", API_KEY)
                .header("Content-Type", "application/json")
                .PUT(BodyPublishers.ofString(json));
    }

    private HttpRequest.Builder post(String path, String json) {
        return request(path)
                .header("X-Api-Key", API_KEY)
                .header("Content-Type", "application/json")
                .POST(BodyPublishers.ofString(json));
    }

    private static HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
        return CLIENT.send(request.build(), BodyHandlers.ofString());
    }

    private static String header(HttpResponse<?> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    private static String allLinks(HttpResponse<?> response) {
        List<String> values = response.headers().allValues("Link");
        return String.join(", ", values);
    }

    private static int countProducts(String jsonArray) {
        int count = 0;
        int idx = 0;
        while ((idx = jsonArray.indexOf("\"sku\":", idx)) != -1) {
            count++;
            idx += 6;
        }
        return count;
    }
}
