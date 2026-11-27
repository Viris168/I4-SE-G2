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

## Task 2 — Users and passwords in the database

Replaced in-memory users with JPA entities (`UserAccount`, `Role`), bcrypt password hashes produced
by Spring Security's `DelegatingPasswordEncoder`, and `JpaUserDetailsService`. Public registration endpoint
at `POST /api/register` with strict validation on username and password policy. Seed users (`admin` with
ADMIN + CUSTOMER, `alice` with CUSTOMER) are seeded idempotently by `CommandLineRunner` in `ShopSecureApplication`
reading passwords from properties (`shop.seed.admin-password` and `shop.seed.customer-password`).

### Registration validation failure (short password) - 400

```
$ curl -i -X POST localhost:8080/api/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"short"}'

HTTP/1.1 400 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json
Transfer-Encoding: chunked

{"detail":"Validation failed","instance":"/api/register","status":400,"title":"Bad Request","errors":[{"field":"password","message":"size must be between 12 and 72"},{"field":"password","message":"must contain an upper-case letter, a lower-case letter and a digit"}]}
```

### Successful registration - 201

```
$ curl -i -X POST localhost:8080/api/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"Correct-Horse-9"}'

HTTP/1.1 201 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 18

{"username":"bob"}
```

### Authenticate as newly registered user (Basic Auth) - 200

```
$ curl -i -u bob:Correct-Horse-9 localhost:8080/api/me

HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 40

{"name":"bob","roles":["ROLE_CUSTOMER"]}
```

### Duplicate username registration - 409

```
$ curl -i -X POST localhost:8080/api/register -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"Correct-Horse-9"}'

HTTP/1.1 409 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
Content-Type: application/problem+json

{"detail":"Username 'bob' is already taken","instance":"/api/register","status":409,"title":"Conflict"}
```

### H2 database query: bcrypt password hashes

```
-- users table (H2)
ID USERNAME PASSWORD_HASH ENABLED
1 admin {bcrypt}$2a$10$gG4uMDWQXP3L8oDgw1iWj.0kFrN4pjpvLt5CIzpTIu2/kmzlCYcLa TRUE
2 alice {bcrypt}$2a$10$OW3UMCBWUPe7OcqR8OSvs.piKlUbgiv5Y5HpqSNxIMsIrYDRwnq1W TRUE
3 bob {bcrypt}$2a$10$CLO5ZWAI6w15XQrEmTc00uAT4myOf102q4H/.Ynoceys2PVN7fyjC TRUE
```

The password hash is never logged or returned by any endpoint, and `UserAccount.toString()` excludes `passwordHash`.

## Task 3 · Roles and method security

alice places an order (customer taken from the Authentication), bob is refused by the
`@PostAuthorize` owner check, admin passes through `hasRole('ADMIN')`. Method-level denials
return the same `application/problem+json` 403 as URL-level denials.

### 1. alice creates an order - 201

```
$ curl -i -u alice:*** '-X' 'POST' 'localhost:8080/api/orders' '-H' 'Content-Type: application/json' '-d' '{"productId":2,"quantity":1}'
HTTP/1.1 201 
Location: /api/orders/1
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/json

{"id":1,"customer":"alice","productId":2,"quantity":1,"status":"NEW"}

```

### 2. bob reads alice's order - 403 (@PostAuthorize owner check)

```
$ curl -i -u bob:*** 'localhost:8080/api/orders/1'
HTTP/1.1 403 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 107

{"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied","instance":"/api/orders/1"}

```

### 3. admin reads alice's order - 200

```
$ curl -i -u admin:*** 'localhost:8080/api/orders/1'
HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 69

{"id":1,"customer":"alice","productId":2,"quantity":1,"status":"NEW"}

```

### 4. alice reads bob's history - 403 (@PreAuthorize)

```
$ curl -i -u alice:*** 'localhost:8080/api/orders?customer=bob'
HTTP/1.1 403 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 105

{"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied","instance":"/api/orders"}

```

### 5. admin reads alice's history - 200

```
$ curl -i -u admin:*** 'localhost:8080/api/orders?customer=alice'
HTTP/1.1 200 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/json
Content-Length: 71

[{"id":1,"customer":"alice","productId":2,"quantity":1,"status":"NEW"}]

```

### 6. alice tries to ship - 403

```
$ curl -i -u alice:*** '-X' 'POST' 'localhost:8080/api/orders/1/ship'
HTTP/1.1 403 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY
Content-Type: application/problem+json
Content-Length: 112

{"type":"about:blank","title":"Forbidden","status":403,"detail":"Access denied","instance":"/api/orders/1/ship"}

```

### 7. admin ships - 204

```
$ curl -i -u admin:*** '-X' 'POST' 'localhost:8080/api/orders/1/ship'
HTTP/1.1 204 
X-Content-Type-Options: nosniff
X-XSS-Protection: 0
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
X-Frame-Options: DENY


```

### 8. Security tests

```
$ ./mvnw -q test -Dtest='Order*SecurityTest'
[INFO] Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
```

## Task 4 · JWT login and a stateless API

Recorded with a random `JWT_SECRET` from `openssl rand -base64 48` (only in the environment, never in a file). Until Tasks 1–3 are merged, the user `alice`, `/api/orders` and the filter chain are the test-only stand-ins from `AuthFlowIT`. `Date` and the default security headers are left out.

### Login: an HS256 token, valid for 15 minutes

```text
$ curl -s -X POST localhost:8095/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"Alice-pass-123"}'
{"token":"eyJhbGciOiJIUzI1NiJ9.eyJpc3MiOiJzaG9wLWFwaSIsInN1YiI6ImFsaWNlIiwiZXhwIjoxNzk1NzQyMzkzLCJpYXQiOjE3OTU3NDE0OTMsImp0aSI6IjQ0NGQ1MTA0LTljZmMtNGExYi1hZjZkLWFlOTAwMDVhNTZhMCIsInJvbGVzIjpbIlJPTEVfQ1VTVE9NRVIiXX0.DaZCm0yDysV3P3zMICscJYYuAZ-WkupW10_KuGzLlOg","tokenType":"Bearer","expiresIn":900}

```

### Decoded token

The token is three base64url parts: `header.payload.signature`. Decoding the first two (jwt.io, or `base64 -d`):

```json
{"alg":"HS256"}
{"iss":"shop-api","sub":"alice","exp":1795742393,"iat":1795741493,"jti":"ab4f7b8b-be04-4778-baf2-71756a93ee53","roles":["ROLE_CUSTOMER"]}
```

`exp - iat = 900` seconds (15 minutes), `jti` is a random id, and `roles` becomes the user's authorities.

**Why the payload is readable, and why that's fine:** a JWT is *signed*, not *encrypted*. Anyone can read the claims, but nobody without `JWT_SECRET` can change them or make a new token, because the HMAC-SHA256 signature would no longer match. So the payload must never hold secrets (no password, no personal data), only who the user is and what they may do.

### The token gives access, and no session cookie appears

```text
$ curl -si localhost:8095/api/orders -H "Authorization: Bearer $TOKEN"
HTTP/1.1 200 
Content-Type: application/json
Content-Length: 71

[{"status":"NEW","quantity":1,"productId":2,"id":1,"customer":"alice"}]


$ curl -si localhost:8095/api/orders -H "Authorization: Bearer $TOKEN" -X POST -H 'Content-Type: application/json' -d '{"productId":1,"quantity":2}'
HTTP/1.1 201 
Content-Type: application/json
Content-Length: 42

{"status":"NEW","id":2,"customer":"alice"}

```

There's no `Set-Cookie` in any response: `SessionCreationPolicy.STATELESS`, so the server keeps no session and every request carries its own token.

### Expired token: 401 problem+json with a Bearer challenge

```text
$ curl -si localhost:8095/api/orders -H "Authorization: Bearer $EXPIRED"
HTTP/1.1 401 
WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Jwt expired at 2026-11-27T00:19:53Z", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1", resource_metadata="http://localhost:8095/.well-known/oauth-protected-resource"
Content-Type: application/problem+json
Content-Length: 119

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Token expired or invalid","instance":"/api/orders"}

```

### Malformed token: same 401

```text
$ curl -si localhost:8095/api/orders -H 'Authorization: Bearer not.a.jwt'
HTTP/1.1 401 
WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Malformed token", error_uri="https://tools.ietf.org/html/rfc6750#section-3.1", resource_metadata="http://localhost:8095/.well-known/oauth-protected-resource"
Content-Type: application/problem+json
Content-Length: 119

{"type":"about:blank","title":"Unauthorized","status":401,"detail":"Token expired or invalid","instance":"/api/orders"}

```

### Wrong password: generic 401

```text
$ curl -si -X POST localhost:8095/api/auth/login -H 'Content-Type: application/json' -d '{"username":"alice","password":"wrong"}'
HTTP/1.1 401 
Content-Type: application/problem+json
Transfer-Encoding: chunked

{"detail":"Bad credentials","instance":"/api/auth/login","status":401,"title":"Unauthorized"}

```

An unknown user gets exactly the same body (checked in `AuthFlowIT`), so an attacker can't tell which part of the login was wrong.

### Tests

`AuthFlowIT` (6 tests, `./mvnw verify`) runs on a random port:
- login gives a token with every claim and a 15-minute lifetime
- the token opens `GET`/`POST /api/orders`, with no `Set-Cookie`
- an expired token (signed with the app's own `JwtEncoder`, `exp` in the past) gets 401 + `WWW-Authenticate: Bearer error="invalid_token"`, and the challenge mentions expiry
- `Bearer not.a.jwt` gets 401 problem+json
- no token gets 401
- a wrong password and an unknown user get the identical generic 401
