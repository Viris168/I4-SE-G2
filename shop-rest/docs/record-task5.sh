#!/usr/bin/env bash
# Records the Task 5 transcript. Start the server first on a fresh run
# (mvn -q compile exec:java), then run: bash docs/record-task5.sh
set -u
BASE=localhost:8080/api
OUT="$(dirname "$0")/task5-http.md"
JSON='Content-Type: application/json'
KEY='X-Api-Key: demo-123'

run() {   # run <title> <curl args...>
  local title="$1"; shift
  {
    echo "## $title"; echo; echo '```'
    printf '$ curl -i'; printf " '%s'" "$@"; echo
    curl -s -i "$@" | grep -v -i -E '^(date|access-control-[a-z-]+):' | tr -d '\r'
    echo; echo '```'; echo
  } >> "$OUT"
}

cat > "$OUT" <<'MD'
# Task 5 · HTTP transcript

Recorded against a fresh `mvn -q compile exec:java`. `Date` and the CORS headers of Task 4 are omitted
to keep the listing concise. All errors return `application/problem+json` (RFC 9457).

MD

run "1. Paginated list (page 1, size 10) - 200 with X-Total-Count and Link headers" \
    "$BASE/products?page=1&size=10"
run "2. Single product with ETag and Cache-Control - 200" \
    "$BASE/products/1"
run "3. Conditional GET with matching If-None-Match - 304 Not Modified" \
    "$BASE/products/1" -H 'If-None-Match: "1"'
run "4. PUT without If-Match - 428 Precondition Required (problem+json)" \
    -X PUT "$BASE/products/1" -H "$KEY" -H "$JSON" \
    -d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
run "5. PUT with stale If-Match - 412 Precondition Failed (problem+json)" \
    -X PUT "$BASE/products/1" -H "$KEY" -H 'If-Match: "0"' -H "$JSON" \
    -d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
run "6. PUT with matching If-Match - 200 OK with new ETag" \
    -X PUT "$BASE/products/1" -H "$KEY" -H 'If-Match: "1"' -H "$JSON" \
    -d '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
run "7. Media-type versioning: v1 (default application/json)" \
    "$BASE/products/1"
run "7b. Media-type versioning: v1 (explicit application/vnd.shop.v1+json)" \
    "$BASE/products/1" -H 'Accept: application/vnd.shop.v1+json'
run "8. Media-type versioning: v2 (application/vnd.shop.v2+json)" \
    "$BASE/products/1" -H 'Accept: application/vnd.shop.v2+json'
run "9. Sub-resource: POST a review - 201 Created with Location" \
    -X POST "$BASE/products/1/reviews" -H "$KEY" -H "$JSON" \
    -d '{"stars":5,"author":"Ana","text":"Boils fast."}'
run "9b. Sub-resource: POST an invalid review - 400 problem+json" \
    -X POST "$BASE/products/1/reviews" -H "$KEY" -H "$JSON" \
    -d '{"stars":6,"author":"","text":"Too high"}'
run "10. Sub-resource: GET reviews for product 1 - 200 OK" \
    "$BASE/products/1/reviews"
run "11. Sub-resource: GET reviews for unknown product 999 - 404 Not Found" \
    "$BASE/products/999/reviews"

echo "Wrote $OUT"
