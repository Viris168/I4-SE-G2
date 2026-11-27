# security-session.md

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
