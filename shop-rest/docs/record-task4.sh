#!/usr/bin/env bash
# Records the Task 4 transcript. Start the server first on a fresh run, keeping its output:
#   ./mvnw -q compile exec:java > /tmp/shop-rest.log 2>&1 &
# then run: SERVER_LOG=/tmp/shop-rest.log bash docs/record-task4.sh
set -u
BASE=localhost:8080/api
OUT="$(dirname "$0")/task4-http.md"
JSON='Content-Type: application/json'
BODY='{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
LOG_START=$( [ -f "${SERVER_LOG:-}" ] && wc -l < "$SERVER_LOG" || echo 0 )

block() {   # block <title> <shown command> <command...>
  local title="$1" shown="$2"; shift 2
  {
    echo "## $title"; echo; echo '```text'
    echo "\$ $shown"
    "$@" | grep -v -i '^date:' | tr -d '\r'
    echo '```'; echo
  } >> "$OUT"
}

cat > "$OUT" <<'MD'
# Task 4 · Providers and filters: HTTP transcript

Recorded with curl against a fresh `./mvnw -q compile exec:java` (`docs/record-task4.sh`). `Date` headers are left out.

## Filter order

Pre-matching filters run before post-matching ones, and within a group the lower `@Priority` runs first. A temporary `System.out.println(getClass().getSimpleName())` in each request filter printed:

```text
POST without key:   ApiKeyFilter                                   (aborts with 401)
POST with key:      ApiKeyFilter → CorsFilter → CorrelationFilter  → resource (201)
```

`ApiKeyFilter` (`@PreMatching`, `Priorities.AUTHENTICATION` = 1000) runs before `CorrelationFilter` (post-matching, `Priorities.USER` = 5000). When it aborts, the later request filters never run, but the response filters still do. That's why the 401 still carries `X-Correlation-Id` and the CORS headers. The access log shows `- ms` for aborted requests, because the start time is taken on the request side of `CorrelationFilter`, which was skipped. The prints were removed afterwards.

MD

block "1. POST without a key: 401 problem+json" \
  "curl -si -X POST $BASE/products -H '$JSON' -d '$BODY'" \
  curl -si -X POST "$BASE/products" -H "$JSON" -d "$BODY"

block "2. POST with a wrong key: 401" \
  "curl -si -X POST $BASE/products -H '$JSON' -H 'X-Api-Key: wrong' -d '$BODY'" \
  curl -si -X POST "$BASE/products" -H "$JSON" -H 'X-Api-Key: wrong' -d "$BODY"

block "3. POST with the right key: 201" \
  "curl -si -X POST $BASE/products -H '$JSON' -H 'X-Api-Key: demo-123' -d '$BODY'" \
  curl -si -X POST "$BASE/products" -H "$JSON" -H 'X-Api-Key: demo-123' -d "$BODY"

block "4. OPTIONS preflight: 200, never reaches a resource" \
  "curl -si -X OPTIONS $BASE/products -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST'" \
  curl -si -X OPTIONS "$BASE/products" -H 'Origin: http://localhost:5173' -H 'Access-Control-Request-Method: POST'

block "5. Correlation id echoed back" \
  "curl -si $BASE/products/1 -H 'X-Correlation-Id: order-check-42' | grep -i correlation" \
  sh -c "curl -si $BASE/products/1 -H 'X-Correlation-Id: order-check-42' | grep -i correlation"

block "6. GET with \`--compressed\`: gzipped" \
  "curl -sv --compressed $BASE/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary)'" \
  sh -c "curl -sv --compressed $BASE/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary)'"

block "7. Same GET without Accept-Encoding: not compressed" \
  "curl -sv $BASE/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'" \
  sh -c "curl -sv $BASE/products -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'"

block "8. Single product with \`--compressed\`: not gzipped (no \`@Compress\`)" \
  "curl -sv --compressed $BASE/products/1 -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'" \
  sh -c "curl -sv --compressed $BASE/products/1 -o /dev/null 2>&1 | grep -i -E '^[<>] (accept-encoding|content-encoding|vary|content-length)'"

cat >> "$OUT" <<'MD'
Only methods annotated with `@Compress` (name binding) go through `GzipWriterInterceptor`: the list is gzipped when the client accepts it, the single product never is.

MD

if [ -n "${SERVER_LOG:-}" ] && [ -f "$SERVER_LOG" ]; then
  sleep 1
  {
    echo "## Server log (one access-log line per request)"; echo; echo '```text'
    tail -n +$((LOG_START + 1)) "$SERVER_LOG" | grep -E '^INFO: [A-Z]+ /api/'
    echo '```'; echo
  } >> "$OUT"
fi

cat >> "$OUT" <<'MD'
## Tests

`FiltersTest` (12 tests, `./mvnw test`) checks each acceptance item against a real Grizzly server: the echoed and generated correlation id, CORS on every response, a preflight that doesn't hit the resource, 401 without a key, 401 with a wrong key, 201 with a valid key, public reads, 401 on an unknown path (proof that the check runs before matching), gzip only with `Accept-Encoding: gzip`, and no gzip on methods without `@Compress`.
MD
