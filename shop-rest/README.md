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
| `edu.itc.shop.api.dto` | `ProductDto` (JSON + XML), `StockChange` |

## Task 1 · First resource and server

Transcript: [docs/task1-http.md](docs/task1-http.md).

`ProductResource` has only a `final` reference to its dependency and no mutable fields because, although Jersey creates a new instance per request, the injected repository/service is a single object shared by every request thread — so all state lives in that thread-safe object (`ConcurrentHashMap` + `AtomicLong`) and the resource never holds anything that concurrent requests could corrupt or that would be silently lost when the instance is discarded.

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

Transcript: `docs/task2-http.md`. To regenerate it, start the server on a fresh run and execute `docs/record-task2.sh`.
