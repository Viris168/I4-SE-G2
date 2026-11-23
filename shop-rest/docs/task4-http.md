# Task 4 · Providers and filters: HTTP transcript

Recorded with curl against a fresh `./mvnw -q compile exec:java` (`docs/record-task4.sh`). `Date` headers are left out.

## Filter order

Pre-matching filters run before post-matching ones, and within a group the lower `@Priority` runs first. A temporary `System.out.println(getClass().getSimpleName())` in each request filter printed:

```text
POST without key:   ApiKeyFilter                                   (aborts with 401)
POST with key:      ApiKeyFilter → CorsFilter → CorrelationFilter  → resource (201)
```

`ApiKeyFilter` (`@PreMatching`, `Priorities.AUTHENTICATION` = 1000) runs before `CorrelationFilter` (post-matching, `Priorities.USER` = 5000). When it aborts, the later request filters never run, but the response filters still do. That's why the 401 still carries `X-Correlation-Id` and the CORS headers. The access log shows `- ms` for aborted requests, because the start time is taken on the request side of `CorrelationFilter`, which was skipped. The prints were removed afterwards.

## 1. POST without a key: 401 problem+json

```text
$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 401 Unauthorized
WWW-Authenticate: ApiKey realm="shop"
X-Correlation-Id: ad63a6b3-c6f6-478a-adae-f90e0b8124a6
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/problem+json
Content-Length: 125

{"detail":"Missing or invalid X-Api-Key","instance":"/api/products","status":401,"title":"Unauthorized","type":"about:blank"}
```

## 2. POST with a wrong key: 401

```text
$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/json' -H 'X-Api-Key: wrong' -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 401 Unauthorized
WWW-Authenticate: ApiKey realm="shop"
X-Correlation-Id: 04cce92e-95fc-4ea4-aa9f-6f103bfc30bc
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/problem+json
Content-Length: 125

{"detail":"Missing or invalid X-Api-Key","instance":"/api/products","status":401,"title":"Unauthorized","type":"about:blank"}
```

## 3. POST with the right key: 201

```text
$ curl -si -X POST localhost:8080/api/products -H 'Content-Type: application/json' -H 'X-Api-Key: demo-123' -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/34
X-Correlation-Id: ad6738ce-82be-4b2d-8b42-b4f576980d8b
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Type: application/json
Content-Length: 63

{"id":34,"name":"Teapot","price":31.00,"sku":"TP-01","stock":5}
```

## 4. OPTIONS preflight: 200, never reaches a resource

```text
$ curl -si -X OPTIONS localhost:8080/api/products -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST'
HTTP/1.1 200 OK
X-Correlation-Id: c5691c0b-f5d9-42ec-98e3-95f6e62d15d2
Access-Control-Allow-Origin: http://localhost:5173
Access-Control-Allow-Methods: GET,POST,PUT,PATCH,DELETE
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
Content-Length: 0

```

## 5. Correlation id echoed back

```text
$ curl -si localhost:8080/api/products/1 -H 'X-Correlation-Id: order-check-42' | grep -i correlation
X-Correlation-Id: order-check-42
Access-Control-Allow-Headers: Content-Type,X-Api-Key,X-Correlation-Id,If-Match
Access-Control-Expose-Headers: Location,ETag,Link,X-Total-Count,X-Correlation-Id
```

## 6. GET with `--compressed`: gzipped

```text
$ curl -sv --compressed localhost:8080/api/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary)'
> Accept-Encoding: deflate, gzip, br, zstd
< Content-Encoding: gzip
< Vary: Accept-Encoding
```

## 7. Same GET without Accept-Encoding: not compressed

```text
$ curl -sv localhost:8080/api/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'
< Content-Length: 1293
```

## 8. Single product with `--compressed`: not gzipped (no `@Compress`)

```text
$ curl -sv --compressed localhost:8080/api/products/1 -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'
> Accept-Encoding: deflate, gzip, br, zstd
< Content-Length: 63
```

Only methods annotated with `@Compress` (name binding) go through `GzipWriterInterceptor`: the list is gzipped when the client accepts it, the single product never is.

## Server log (one access-log line per request)

```text
INFO: POST /api/products -> 401 [ad63a6b3-c6f6-478a-adae-f90e0b8124a6] - ms
INFO: POST /api/products -> 401 [04cce92e-95fc-4ea4-aa9f-6f103bfc30bc] - ms
INFO: POST /api/products -> 201 [ad6738ce-82be-4b2d-8b42-b4f576980d8b] 36 ms
INFO: OPTIONS /api/products -> 200 [c5691c0b-f5d9-42ec-98e3-95f6e62d15d2] - ms
INFO: GET /api/products/1 -> 200 [order-check-42] 0 ms
INFO: GET /api/products -> 200 [26443310-d417-4d80-8f3f-953874d1620b] 19 ms
INFO: GET /api/products -> 200 [03be5c02-323a-4363-9567-3b3b3b7c8ede] 1 ms
INFO: GET /api/products/1 -> 200 [b260af9d-0eca-45b5-a166-b1059984d423] 0 ms
```

## Tests

`FiltersTest` (12 tests, `./mvnw test`) checks each acceptance item against a real Grizzly server: the echoed and generated correlation id, CORS on every response, a preflight that doesn't hit the resource, 401 without a key, 401 with a wrong key, 201 with a valid key, public reads, 401 on an unknown path (proof that the check runs before matching), gzip only with `Accept-Encoding: gzip`, and no gzip on methods without `@Compress`.
