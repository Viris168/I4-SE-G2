# shop-rest — Lab 04

Jersey (JAX-RS) on an embedded Grizzly server at `http://localhost:8080/api/`.

```bash
./mvnw -q compile exec:java
```

## 1. First resource and server

Transcript: [docs/task1-http.md](docs/task1-http.md).

`ProductResource` has only a `final` reference to the repository and no mutable fields because, although Jersey creates a new instance per request, the injected `InMemoryProductRepository` is a single object shared by every request thread — so all state lives in that thread-safe repository (`ConcurrentHashMap` + `AtomicLong`) and the resource never holds anything that concurrent requests could corrupt or that would be silently lost when the instance is discarded.
