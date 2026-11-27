# CSRF Strategy

## Per-chain decision

| Order | Filter chain matcher | Credentials accepted | Session policy | CSRF |
|---|---|---|---|---|
| 1 | `/api/**` | `Authorization` header only (`Bearer` JWT; HTTP Basic retained for legacy tests), **never cookies** | `STATELESS` | **Disabled** |
| 2 | `/products/**` | Public read-only server-rendered HTML (`GET /products/{id}/reviews.html`) | Default | **Enabled** (default) |
| 3 | Everything else (`/**`) | None (`denyAll()`) | Default | **Enabled** (default) |

## Why CSRF is disabled on `/api/**`

Cross-Site Request Forgery exploits the browser's automatic inclusion of ambient credentials — primarily session cookies — on cross-site requests.

The `/api/**` chain enforces `SessionCreationPolicy.STATELESS`, sets no session cookie (`Set-Cookie` is never sent), and accepts credentials strictly in the `Authorization` HTTP header. A malicious third-party page cannot read a Bearer token stored by our origin and cannot cause the browser to attach an `Authorization` header automatically. Therefore CSRF protection is unnecessary for `/api/**` and is disabled explicitly in `SecurityConfig`.

## If a same-origin SPA with a session cookie were added

If the application later introduced a browser-facing chain authenticating with a session cookie (`JSESSIONID`), CSRF **must** be re-enabled on that chain using Spring Security's cookie repository so JavaScript on the same origin can read the token and echo it in a custom header:

```java
@Bean
@Order(2)
SecurityFilterChain spa(HttpSecurity http) throws Exception {
    var requestHandler = new CsrfTokenRequestAttributeHandler();
    http.securityMatcher("/spa/**")
        .csrf(csrf -> csrf
            .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .csrfTokenRequestHandler(requestHandler))
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
    return http.build();
}
```

The SPA reads the `XSRF-TOKEN` cookie set by Spring Security and sends its value in the `X-XSRF-TOKEN` header on every state-changing request:

```javascript
function readCookie(name) {
  const match = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
  return match ? decodeURIComponent(match[1]) : '';
}

await fetch('/spa/orders', {
  method: 'POST',
  credentials: 'same-origin',
  headers: {
    'Content-Type': 'application/json',
    'X-XSRF-TOKEN': readCookie('XSRF-TOKEN')
  },
  body: JSON.stringify({ productId: 1, quantity: 2 })
});
```

An attacker on another origin cannot read the `XSRF-TOKEN` cookie (blocked by the Same-Origin Policy) and thus cannot supply a matching `X-XSRF-TOKEN` header.
