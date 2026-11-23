#!/usr/bin/env bash
# Records the Task 3 transcript. Start the server first, on a fresh run
# (mvn -q compile exec:java), then run: bash docs/record-task3.sh
# The 500 experiment needs a temporary code change and is appended by hand (see task3-http.md).
set -u
BASE=localhost:8080/api
OUT="$(dirname "$0")/task3-http.md"
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
# Task 3 · HTTP transcript

Recorded against a fresh `mvn -q compile exec:java`. `Date` and the CORS headers of Task 4 are left out
to keep the listing short. Every error body is `application/problem+json` (RFC 9457).

MD

run "1. Filtered and sorted list - 200" "$BASE/products?minPrice=5%20EUR&inStock=true&sort=price,desc"
run "1b. Text search, case-insensitive - 200" "$BASE/products?q=MU"
run "2. Unparsable money - 400 from the ParamConverter" "$BASE/products?minPrice=abc"
run "2b. Money in another currency - 400" "$BASE/products?maxPrice=10%20USD"
run "3. Sort by a field outside the whitelist - 400" "$BASE/products?sort=colour,asc"
run "4. Invalid POST - 400 with one error per violated constraint" -X POST $BASE/products -H "$KEY" -H "$JSON" \
    -d '{"name":"","sku":"kettle","price":0,"stock":-1}'
run "4b. Invalid PATCH body - 400" -X PATCH $BASE/products/2/stock -H "$KEY" -H "$JSON" -d '{"delta":5000}'
run "4c. Malformed JSON body - 400, not 500" -X POST $BASE/products -H "$KEY" -H "$JSON" -d '{"name":'
run "5. Unknown product - 404 as problem+json" $BASE/products/99
run "5b. Duplicate SKU - 409 as problem+json (no change in the resource)" -X POST $BASE/products -H "$KEY" -H "$JSON" \
    -d '{"name":"Mug","sku":"MG-01","price":6.50,"stock":1}'
run "5c. Stock below zero - 409 as problem+json" -X PATCH $BASE/products/3/stock -H "$KEY" -H "$JSON" -d '{"delta":-1}'
run "5d. Wrong Content-Type - 415 as problem+json" -X POST $BASE/products -H "$KEY" -H 'Content-Type: text/plain' -d 'Teapot'

echo "Wrote $OUT"
