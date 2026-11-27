# security-session.md — Lab-05 transcripts

All transcripts were recorded against `shop-secure` running on `localhost:8080` (H2 in-memory, seeded with three products).

## Task 1 — Default security and the first filter chain

### 1a. Zero configuration (before `SecurityConfig` existed)

With only `spring-boot-starter-security` + `spring-boot-starter-oauth2-resource-server` on the classpath and **no** security
configuration, Spring Boot creates **no** default user (see README, "Default behaviour"). To get the classic generated
password the app was started with only the user *name* set:

```
$ java -jar target/shop-secure-0.0.1-SNAPSHOT.jar --spring.security.user.name=user
...
Using generated security password: da647e5e-8f7c-45d4-84fa-aedd147c9433

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

Without credentials:

```
$ curl -i localhost:8080/api/products
HTTP/1.1 401
Set-Cookie: JSESSIONID=303FA843AA70FDD901182ACCCE5F7FD6; Path=/; HttpOnly
WWW-Authenticate: Basic realm="Realm", charset="UTF-8"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Length: 0
```

With the generated password:

```
$ curl -i -u user:da647e5e-8f7c-45d4-84fa-aedd147c9433 localhost:8080/api/products
HTTP/1.1 200
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 175

[{"name":"USB-C cable","price":7.90,"stock":25,"id":1},{"name":"Wireless mouse","price":19.90,"stock":10,"id":2},{"name":"Mechanical keyboard","price":89.00,"stock":4,"id":3}]
```

Things to notice: every endpoint (even the catalogue) is locked, the 401 has an **empty body**, and a `JSESSIONID`
cookie is created — the default chain is session-based. (The generated password changes on every start; it is
not a secret of this project.)

### 1b. Explicit `SecurityFilterChain` (`config/SecurityConfig`)

Rules: `GET /api/products/**` public, `DELETE /api/products/**` ADMIN, other `/api/**` authenticated, everything else
denied. HTTP Basic, stateless, problem+json for 401/403. Temporary users `admin/admin` (ADMIN), `alice/alice` (CUSTOMER).

```
$ curl -i localhost:8080/api/products

HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 175
Date: Fri, 27 Nov 2026 01:06:08 GMT

[{"id":1,"name":"USB-C cable","price":7.90,"stock":25},{"id":2,"name":"Wireless mouse","price":19.90,"stock":10},{"id":3,"name":"Mechanical keyboard","price":89.00,"stock":4}]

$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"Mouse","price":19.9,"stock":5}'

HTTP/1.1 401 
WWW-Authenticate: Basic realm="Realm"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 120
Date: Fri, 27 Nov 2026 01:06:08 GMT

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required","instance":"/api/products"}

$ curl -i -u alice:alice -X DELETE localhost:8080/api/products/1

HTTP/1.1 403 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 109
Date: Fri, 27 Nov 2026 01:06:09 GMT

{"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied","instance":"/api/products/1"}

$ curl -i -u admin:admin -X DELETE localhost:8080/api/products/1

HTTP/1.1 204 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Date: Fri, 27 Nov 2026 01:06:09 GMT



$ curl -i -u admin:admin -X DELETE localhost:8080/api/products/1

HTTP/1.1 404 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Fri, 27 Nov 2026 01:06:09 GMT

{"detail":"Product 1 not found","instance":"/api/products/1","status":404,"title":"Not Found"}

$ curl -i -u alice:alice -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{"name":"","price":-1,"stock":5}'

HTTP/1.1 400 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked
Date: Fri, 27 Nov 2026 01:06:09 GMT
Connection: close

{"detail":"Validation failed","instance":"/api/products","status":400,"title":"Bad Request","errors":[{"field":"name","message":"must not be blank"},{"field":"price","message":"must be greater than or equal to 0.00"}]}

$ curl -i -u alice:wrong localhost:8080/api/orders

HTTP/1.1 401 
WWW-Authenticate: Basic realm="Realm"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 118
Date: Fri, 27 Nov 2026 01:06:09 GMT

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required","instance":"/api/orders"}

$ curl -i localhost:8080/actuator

HTTP/1.1 401 
WWW-Authenticate: Basic realm="Realm"
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 116
Date: Fri, 27 Nov 2026 01:06:09 GMT

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Authentication required","instance":"/actuator"}
```

Required cases: anonymous GET **200**, anonymous POST **401**, customer DELETE **403**, admin DELETE **204**.
Extra checks: a second DELETE gives 404 problem+json, invalid input gives 400 with field errors, a wrong password gives
the same generic 401, and a path outside `/api/**` (`/actuator`) is caught by `anyRequest().denyAll()`.
No `Set-Cookie` header appears any more (`SessionCreationPolicy.STATELESS`).
