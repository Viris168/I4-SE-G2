# shop-secure — Lab-05: Security in Java Web Applications

Spring Boot 4.1.1 · Spring Security 7.1 · JDK 25 · Maven 3.9 (wrapper included)

## How to run

```bash
./mvnw -q verify                 # build + tests
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
