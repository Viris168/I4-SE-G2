# shop-secure — Lab-05: Security in Java Web Applications

Spring Boot 4.1.1 · Spring Security 7.1 · JDK 25 · Maven 3.9 (wrapper included)

## How to run

```bash
cp .env.example .env             # set JWT_SECRET, SHOP_SEED_ADMIN_PASSWORD, SHOP_SEED_CUSTOMER_PASSWORD
export JWT_SECRET=$(openssl rand -base64 48)
export SHOP_SEED_ADMIN_PASSWORD='<choose-one>'      # 12+ chars, upper, lower and digit
export SHOP_SEED_CUSTOMER_PASSWORD='<choose-one>'
# the tests set their own test-only values; only JWT_SECRET is needed for ./mvnw verify
./mvnw -q verify                 # build + unit + integration tests
./mvnw spring-boot:run           # http://localhost:8080
```

Transcripts for every task are in [security-session.md](security-session.md).

## Task 1 — Default behaviour (no security configuration)

### What happened

Starting the app with no `SecurityFilterChain` and no users locks **every** endpoint behind HTTP Basic / form
login: `GET /api/products` answers `401` with an empty body and a `WWW-Authenticate: Basic realm="Realm"` header,
and a `JSESSIONID` session cookie is created.

In this project there is a twist: **no password is generated**. The reason shows up in the
`--debug` condition report:

```
UserDetailsServiceAutoConfiguration:
   Did not match:
      - AnyNestedCondition 0 matched 3 did not; ... @ConditionalOnMissingClass found unwanted class
        'org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector'
```

Because `spring-boot-starter-oauth2-resource-server` is on the classpath, Boot assumes the app will
authenticate with tokens and backs off from creating the in-memory user. With no user, **nobody** can log in.
Setting just the user name (`--spring.security.user.name=user`) re-enables the auto-configuration, and the
familiar line appears in the log:

```
Using generated security password: da647e5e-8f7c-45d4-84fa-aedd147c9433

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

after which `curl -u user:<password> localhost:8080/api/products` returns `200`.

### Which auto-configuration produced it

| Auto-configuration | What it contributes |
|---|---|
| `ServletWebSecurityAutoConfiguration` → `SecurityFilterChainConfiguration` | Because there is no `SecurityFilterChain` bean (`@ConditionalOnMissingBean`), it registers the default chain: `anyRequest().authenticated()`, `formLogin()`, `httpBasic()`. Its `EnableWebSecurityConfiguration` applies `@EnableWebSecurity`. |
| `UserDetailsServiceAutoConfiguration` | When no `UserDetailsService` / `AuthenticationManager` / `AuthenticationProvider` / `JwtDecoder` bean exists, it creates an `InMemoryUserDetailsManager` with one user `user` and a random UUID password, which it logs as "Using generated security password". It backs off when the resource-server classes are present, unless `spring.security.user.name` or `.password` is set. |
| `SecurityFilterAutoConfiguration` | Registers the `springSecurityFilterChain` (`DelegatingFilterProxy`) with the servlet container, so the chain actually sees requests. |
| `SecurityAutoConfiguration` | Publishes authentication events (`DefaultAuthenticationEventPublisher`). |

The default headers (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-cache, …`) come
from Spring Security's `HeadersConfigurer`, which is on by default in any chain.

### What replaced it

`config/SecurityConfig` declares its own `SecurityFilterChain`, so `SecurityFilterChainConfiguration` backs off.
Its `UserDetailsService` bean makes `UserDetailsServiceAutoConfiguration` back off too:

* `GET /api/products/**` → `permitAll()`
* `DELETE /api/products/**` → `hasRole("ADMIN")`
* any other `/api/**` → `authenticated()`
* `anyRequest()` → `denyAll()` (deny by default)
* HTTP Basic on, form login off, `SessionCreationPolicy.STATELESS`, CSRF off (no cookie credentials)
* 401 / 403 answered as `application/problem+json` by `security/ProblemEntryPoint` and
  `security/ProblemAccessDeniedHandler`. Validation errors and not-found go through `web/ApiExceptionHandler`.

Temporary users (until Task 2): `admin/admin` (ADMIN), `alice/alice` (CUSTOMER).


## Task 5 — Hardening: CSRF, CORS, headers, XSS, secrets, logging

- **CSRF strategy ([docs/csrf.md](docs/csrf.md)).** `SecurityConfig` splits the application into three ordered chains:
  - `@Order(1)` `/api/**` — stateless (`SessionCreationPolicy.STATELESS`), credentials via `Authorization` header only (never cookies), so CSRF is disabled.
  - `@Order(2)` `/products/**` — public read-only `GET`/`HEAD /products/{id}/reviews.html` Thymeleaf page, CSRF left enabled by default.
  - `@Order(3)` fallback — `anyRequest().denyAll()` (except `DispatcherType.ERROR`).
- **CORS (`config/CorsConfig`).** Allows only `http://localhost:5173` on `/api/**` (`GET, POST, PUT, DELETE`, headers `Authorization` and `Content-Type`, exposed `Location`, `Max-Age: 3600`). Any other origin (e.g. `http://evil.example`) receives `403` without `Access-Control-Allow-Origin`.
- **Security headers.** Every chain applies `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Strict-Transport-Security: max-age=31536000 ; includeSubDomains`, `Content-Security-Policy: default-src 'self'; frame-ancestors 'none'; object-src 'none'`, and `Referrer-Policy: strict-origin-when-cross-origin`.
  - *Note on HSTS:* `.requestMatcher(AnyRequestMatcher.INSTANCE)` is enabled so HSTS appears over plain `http://localhost:8080` during the lab; in production remove that matcher so Spring Security's default (HTTPS-only) applies.
- **Stored XSS defence (`Review`, `ReviewService`, `ReviewController`, `templates/reviews.html`).** `POST /api/reviews` (authenticated, validated `@Min(1) @Max(5)` stars, `@NotBlank @Size(max=1000)` text) stores text unchanged; `GET /api/products/{id}/reviews` returns JSON escaped by Jackson with `nosniff`, and `GET /products/{id}/reviews.html` renders reviews with Thymeleaf `th:text` (HTML-entity encoded).
- **Secrets & fail-fast.** No literal secrets remain in `src/main` or `application.yml`; `spring.h2.console.enabled` is `false`. Required variables are listed without values in [`.env.example`](.env.example). Missing or short `JWT_SECRET` fails startup immediately via `@Validated SecurityProps`.
- **Security logging (`RequestIdFilter`, `SecurityEventLogger`).** `RequestIdFilter` (instantiated in `SecurityConfig`, never `@Component`) sets MDC `rid` and response header `X-Request-Id`. `JwtConfig` connects Boot's `AuthenticationEventPublisher` to `ProviderManager` and `SecurityConfig` exposes `SpringAuthorizationEventPublisher`, so `SecurityEventLogger` writes `login.failed`, `login.ok`, and `access.denied` lines with `rid=%X{rid}` to the `SECURITY` logger.

Transcripts and `th:utext` counter-example: [security-session.md](security-session.md#task-5--hardening-csrf-cors-headers-xss-secrets-logging). Verification tests: `HardeningIT`.

## Challenge — Refresh tokens, lockout, audit log, OWASP checklist

- **Refresh tokens with rotation (`RefreshTokenService`, `domain/RefreshToken`).** `POST /api/auth/login` also returns an opaque `refreshToken` (32 `SecureRandom` bytes, base64url) valid for 7 days; only its SHA-256 hash is stored in `refresh_tokens`. `POST /api/auth/refresh` returns a new access token and a new refresh token and revokes the old one (`replacedBy`). Presenting a revoked token again revokes the whole `familyId` and is audited as `REFRESH_REUSE_DETECTED`. `POST /api/auth/logout` revokes the family. Every failure is the same `401 Refresh token invalid`. Refresh reloads the user, so a locked or disabled account cannot refresh and role changes show up in the new token.
- **Rate limiting (`security/LoginRateLimiter`).** Fixed one-minute window, 5 login attempts per client IP and per username, kept in a `ConcurrentHashMap`; the 6th answers `429` problem+json with `Retry-After`.
- **Account lockout (`UserAccount.failedAttempts` / `lockedUntil`, `security/LockoutListener`).** Driven by the authentication events: the 5th consecutive bad password locks the account for 15 minutes, a successful login resets the counter. `JpaUserDetailsService` marks the account locked, so the client gets the same `Bad credentials` 401 while the audit log records `LOCKED`.
- **Audit log (`service/AuditService`, table `security_audit`).** `REQUIRES_NEW` transaction per row with time, type, username, IP and request id, for `LOGIN_OK`, `LOGIN_FAILED`, `LOCKED`, `TOKEN_REFRESHED`, `REFRESH_REUSE_DETECTED`, `LOGOUT`, `ACCESS_DENIED` (403s of authenticated users) and `ROLE_CHANGED` (`PUT /api/admin/users/{username}/roles`). `GET /api/admin/audit?username=` is ADMIN only, by URL rule and by `@PreAuthorize`.
- **OWASP checklist.** [docs/owasp-checklist.md](docs/owasp-checklist.md) maps the ten 2021 categories to controls and evidence; A06 and A10 are stated as gaps with a plan.
- **Tests.** `AuthLifecycleIT` (7 tests) covers rotation, reuse detection, logout, lockout, counter reset, the 429 and the audit endpoint. The integration tests reset the rate limiter before each test because they all log in from 127.0.0.1.
