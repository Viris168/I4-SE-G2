# Task 4 · Providers and filters: HTTP transcript

Recorded with curl against a temporary server built from the four Task 4 providers (`edu.itc.shop.api.filter`) and the test-only `ItemsResource` (`/api/items`), because the product resource from Tasks 1–2 did not exist yet. The same providers are picked up by `Main`'s `packages("edu.itc.shop.api")` scan, so `/api/products` behaves the same way once it is there. `Date` headers are left out.

## Filter order

Pre-matching filters run before post-matching ones, and within a group the lower `@Priority` runs first. A temporary `System.out.println(getClass().getSimpleName())` in each request filter printed:

```text
POST without key:   ApiKeyFilter                                   (aborts with 401)
POST with key:      ApiKeyFilter → CorsFilter → CorrelationFilter  → resource (201)
```

`ApiKeyFilter` (`@PreMatching`, `Priorities.AUTHENTICATION` = 1000) runs before `CorrelationFilter` (post-matching, `Priorities.USER` = 5000). When it aborts, the later request filters never run, but the response filters still do. That's why the 401 still carries `X-Correlation-Id` and the CORS headers. The access log shows `- ms` for aborted requests, because the start time is taken on the request side of `CorrelationFilter`, which was skipped. The prints were removed afterwards.

## 1. POST without a key: 401 problem+json

```text
$ curl -si -X POST localhost:8089/api/items -H 'Content-Type: application/json' -d '{"id":7,"name":"Kettle"}'
HTTP/1.1 401 Unauthorized
WWW-Authenticate: ApiKey realm="shop"
X-Correlation-Id: 3c9a6418-e2ac-4102-9832-8dd7929c8e54
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/problem+json
Content-Length: 122

{"detail":"Missing or invalid X-Api-Key","instance":"/api/items","status":401,"title":"Unauthorized","type":"about:blank"}
```

## 2. POST with a wrong key: 401

```text
$ curl -si -X POST localhost:8089/api/items -H 'Content-Type: application/json' -H 'X-Api-Key: wrong' -d '{"id":7,"name":"Kettle"}'
HTTP/1.1 401 Unauthorized
WWW-Authenticate: ApiKey realm="shop"
X-Correlation-Id: d7f08858-c95a-4ff0-b91d-d1f94aba1465
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/problem+json
Content-Length: 122

{"detail":"Missing or invalid X-Api-Key","instance":"/api/items","status":401,"title":"Unauthorized","type":"about:blank"}
```

## 3. POST with the right key: 201

```text
$ curl -si -X POST localhost:8089/api/items -H 'Content-Type: application/json' -H 'X-Api-Key: demo-123' -d '{"id":7,"name":"Kettle"}'
HTTP/1.1 201 Created
Location: http://localhost:8089/api/items/7
X-Correlation-Id: 86a81e13-3477-4e47-beea-2b5e7200b032
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/json
Content-Length: 24

{"id":7,"name":"Kettle"}
```

## 4. OPTIONS preflight: 200, never reaches a resource

```text
$ curl -si -X OPTIONS localhost:8089/api/items -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST'
HTTP/1.1 200 OK
X-Correlation-Id: 017d3455-a388-46c5-b85f-7e78b0618ea1
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Length: 0
```

## 5. Correlation id echoed back

```text
$ curl -si localhost:8089/api/items/1 -H 'X-Correlation-Id: order-check-42' | grep -i correlation
X-Correlation-Id: order-check-42
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
```

## 6. GET with `--compressed`: gzipped

```text
$ curl -sv --compressed localhost:8089/api/items -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary)'
> Accept-Encoding: deflate, gzip, br, zstd
< Content-Encoding: gzip
< Vary: Accept-Encoding
```

## 7. Same GET without Accept-Encoding: not compressed

```text
$ curl -sv localhost:8089/api/items -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'
< Content-Length: 6985
```

The list is 6985 bytes uncompressed. Only methods annotated with `@Compress` (name binding) go through `GzipWriterInterceptor`, so `GET /api/items/1` is never gzipped, even with `Accept-Encoding: gzip`.

## Server log (one access-log line per request)

```text
INFO: POST /api/items -> 401 [3c9a6418-e2ac-4102-9832-8dd7929c8e54] - ms
INFO: POST /api/items -> 401 [d7f08858-c95a-4ff0-b91d-d1f94aba1465] - ms
INFO: POST /api/items -> 201 [86a81e13-3477-4e47-beea-2b5e7200b032] 82 ms
INFO: OPTIONS /api/items -> 200 [017d3455-a388-46c5-b85f-7e78b0618ea1] - ms
INFO: GET /api/items/1 -> 200 [order-check-42] 3 ms
INFO: GET /api/items -> 200 [5de9c956-5fc6-49c8-92fe-bfd5b57deccd] 4 ms
INFO: GET /api/items -> 200 [a09f6443-1399-47e7-98e1-98f927a18a07] 1 ms
```

## Tests

`FiltersTest` (12 tests, `mvn test`) checks each acceptance item against a real Grizzly server: the echoed and generated correlation id, CORS on every response, a preflight that doesn't hit the resource, 401 without a key, 401 with a wrong key, 201 with a valid key, public reads, 401 on an unknown path (proof that the check runs before matching), gzip only with `Accept-Encoding: gzip`, and no gzip on methods without `@Compress`.
