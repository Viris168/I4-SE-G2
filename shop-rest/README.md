# shop-rest

Lab-04: a product catalogue API with Jakarta REST 4.0 on Eclipse Jersey and an embedded Grizzly server.

## Run

Requires JDK 25 and Maven 3.9.

```bash
mvn -q compile exec:java        # http://localhost:8080/api/ , stop with Ctrl+C
```

Without a local Maven install, use the wrapper: `./mvnw -q compile exec:java` (and `./mvnw test`).

## Layout

| Package | Content |
|---|---|
| `edu.itc.shop` | `Main`: Grizzly bootstrap and `config()` (`ResourceConfig` + `AbstractBinder`) |
| `edu.itc.shop.domain` | `Product`, `ProductRepository`, `InMemoryProductRepository`, `ProductService`, `Review`, `ReviewRepository`, `InMemoryReviewRepository`, `Money`, `ProductQuery`, domain exceptions. No HTTP code apart from the status carried by the exceptions. |
| `edu.itc.shop.api` | `ProductResource`, `ReviewResource` (sub-resource) |
| `edu.itc.shop.api.dto` | `ProductDto` (JSON + XML), `ProductV2` (`vnd.shop.v2+json`), `CreateReview`, `StockChange`, `Problem` (RFC 9457) |
| `edu.itc.shop.api.params` | `ProductFilter` (`@BeanParam`), `MoneyConverterProvider` |
| `edu.itc.shop.api.error` | `ValidationMapper`, `ProblemMapper` |
| `edu.itc.shop.api.filter` | Task 4 filters and the gzip interceptor |

## Task 1 · First resource and server

Transcript: [docs/task1-http.md](docs/task1-http.md).

`ProductResource` has only a `final` reference to the injected service and no mutable fields because, although Jersey creates a new instance per request, the injected objects are singletons shared by every request thread — so all state lives in the thread-safe repository (`ConcurrentHashMap` + `AtomicLong`) and the resource never holds anything that concurrent requests could corrupt or that would be silently lost when the instance is discarded.

## Task 2 · CRUD, status codes, JSON and XML

| Method | Path | Success | Errors |
|---|---|---|---|
| GET | `/api/products` | 200 list | |
| GET | `/api/products/{id}` | 200 | 404 |
| POST | `/api/products` | 201 + `Location` | 409 duplicate SKU, 415 wrong `Content-Type` |
| PUT | `/api/products/{id}` | 200 | 404, 409 SKU taken by another product |
| PATCH | `/api/products/{id}/stock` | 200 | 404, 409 stock would go negative |
| DELETE | `/api/products/{id}` | 204 | 404 |

- Every request and response body is a `ProductDto`. It is a JAXB-annotated class (not a record) so the same type serves `application/json` (JSON-B) and `application/xml` (JAXB). `version` stays internal.
- `Location` is built with `UriInfo.getAbsolutePathBuilder()`, never a hard-coded host.
- The rules (unique SKU, no negative stock) are in `ProductService`. It throws `DuplicateSkuException` / `InsufficientStockException` (409) and `NotFoundException` (404). Its write methods are `synchronized` so the check and the save happen atomically.
- The 415 for `Content-Type: text/plain` comes from Jersey, because of the class-level `@Consumes`.

Transcript: `docs/task2-http.md`. To regenerate it, start the server on a fresh run and execute `docs/record-task2.sh` (it now sends `X-Api-Key`, required for writes since Task 4).

## Task 3 · Parameters and validation

`GET /api/products` takes its query parameters through `@BeanParam @Valid ProductFilter`:

| Parameter | Example | Meaning |
|---|---|---|
| `q` | `q=mu` | name contains the text, case-insensitive |
| `minPrice`, `maxPrice` | `minPrice=5%20EUR` | `Money` converted by `MoneyConverterProvider`; must be EUR |
| `inStock` | `inStock=true` | keep only `stock > 0` |
| `sort` | `sort=price,desc` | field is one of `name`, `price`, `stock` (default `name,asc`) |

- **Validation.** `ProductDto` carries the Bean Validation constraints (name, SKU pattern `AA-00`, price ≥ 0.01 with 2 decimals, stock ≥ 0). POST and PUT bodies are `@Valid @NotNull`, `StockChange.delta` is limited to ±1000. The sort field is whitelisted twice: by the `@Pattern` on `ProductFilter.sort`, and by the comparator map in `ProductService`, so no unchecked value reaches the sorting code.
- **Converter.** `MoneyConverterProvider` throws a `BadRequestException` on bad input. Otherwise Jersey would either ignore the parameter (on an `IllegalArgumentException`) or answer 404 (query parameter extraction failures).
- **Errors.** Every error is `application/problem+json` with `type`, `title`, `status`, `detail` and `instance`:
  - `ValidationMapper` maps `ConstraintViolationException` to 400 with an `errors` list of `field`/`message` pairs.
  - `ProblemMapper` (`ExceptionMapper<Throwable>`) keeps the status and headers of any `WebApplicationException` (404, 405, 409, 415, …), turns an unreadable JSON body into 400, and answers everything else with 500 and `detail: "reference <uuid>"`. The stack trace is logged under that id and never sent to the client.
- The resource methods contain no try/catch and no validation code; the 404/409 of Tasks 1–2 became problem bodies without touching `ProductResource`.

Transcript: `docs/task3-http.md` (regenerate with `docs/record-task3.sh`; the 500 experiment is described in the file).

## Task 4 · Providers and filters

All four providers live in `edu.itc.shop.api.filter` and are picked up by the package scan; `ProductResource` was not changed except for `@Compress` on the list method.

| Provider | Kind | What it does |
|---|---|---|
| `ApiKeyFilter` | `@PreMatching` request filter, `Priorities.AUTHENTICATION` | Every method except `GET`/`HEAD`/`OPTIONS` needs `X-Api-Key` from the system property `shop.api.keys` (default `demo-123`); otherwise 401 problem+json with `WWW-Authenticate: ApiKey realm="shop"`, before any matching. |
| `CorsFilter` | `@PreMatching` request + response filter | Answers every `OPTIONS` preflight with 200 before it reaches a resource and adds the `Access-Control-*` headers to all responses. |
| `CorrelationFilter` | request + response filter, `Priorities.USER` | Echoes `X-Correlation-Id` or generates a UUID, and logs one line per request: `METHOD path -> status [id] ms`. |
| `GzipWriterInterceptor` + `@Compress` | name-bound `WriterInterceptor` | Gzips only `@Compress` methods (the product list), and only when `Accept-Encoding` contains `gzip`; sets `Content-Encoding: gzip` and `Vary: Accept-Encoding`. |

`ApiKeyFilter` runs before `CorrelationFilter` (pre-matching before post-matching, then by priority). When it aborts, the response filters still run, so a 401 also carries the correlation id and CORS headers.

Transcript: `docs/task4-http.md` (regenerate with `docs/record-task4.sh`). `FiltersTest` (12 tests) checks the same behaviour with JUnit.

## Task 5 · Pagination, caching and versioning

- **Pagination.** `ProductFilter` adds validated `page` (`>= 0`, default `0`) and `size` (`1..100`, default `20`). `InMemoryProductRepository` seeds 30 additional products (33 total). `GET /api/products` returns `X-Total-Count` (after filtering) and `Link` headers (`first`, `prev`, `next`, `last`) built with `UriInfo.getRequestUriBuilder().replaceQueryParam("page", n)`, omitting `prev` on the first page and `next` on the last page.
- **Caching & optimistic concurrency.** `GET /api/products/{id}` derives an `EntityTag` from `product.version()`, returns `304 Not Modified` when `If-None-Match` matches via `Request.evaluatePreconditions(tag)`, and attaches `ETag` and `Cache-Control: private, no-transform, max-age=60`. `PUT /api/products/{id}` requires `If-Match` (`428 Precondition Required` as `problem+json` when missing), returns `412 Precondition Failed` (`problem+json`) when stale, and returns `200` with the new `ETag` on success. `PATCH /api/products/{id}/stock` continues to work without preconditions.
- **Media-type versioning.** `GET /api/products/{id}` serves `ProductV2` (`Money` object + `available` flag) for `Accept: application/vnd.shop.v2+json`, while plain `Accept: */*` / `application/json` (`qs=1.0`) and `application/vnd.shop.v1+json` stay on v1 (`ProductDto`).
- **Sub-resource.** `@Path("{id: \\d+}/reviews")` on `ProductResource` is a sub-resource locator returning `new ReviewResource(product, reviews)` backed by a bound thread-safe `ReviewRepository` singleton. `ReviewResource` serves `GET` (list and by id) and validated `POST` (`stars` 1–5, non-blank `author`, `text` ≤ 500 chars → `201` + `Location`). Requests for unknown products fail with `404` in the locator.

Transcript: [docs/task5-http.md](docs/task5-http.md) (regenerate with `docs/record-task5.sh`). Run `mvn -q test` to execute both `FiltersTest` and `Task5Test`.


