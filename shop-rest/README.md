# shop-rest

Lab-04: a product catalogue API with Jakarta REST 4.0 on Eclipse Jersey and an embedded Grizzly server.

## Run

Requires JDK 25 and Maven 3.9.

```bash
mvn -q compile exec:java        # http://localhost:8080/api/ , stop with Ctrl+C
```

## Layout

| Package | Content |
|---|---|
| `edu.itc.shop` | `Main`: Grizzly bootstrap and `config()` (`ResourceConfig` + `AbstractBinder`) |
| `edu.itc.shop.domain` | `Product`, `ProductRepository`, `InMemoryProductRepository`, `ProductService`, domain exceptions. No HTTP code apart from the status carried by the exceptions. |
| `edu.itc.shop.api` | `ProductResource` |
| `edu.itc.shop.api.dto` | `ProductDto` (JSON + XML), `StockChange`, `Problem` (RFC 9457) |
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

