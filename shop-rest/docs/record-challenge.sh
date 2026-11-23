#!/usr/bin/env bash
# Records the challenge transcript. Start the server first, on a fresh run
# (mvn -q process-classes exec:java, so META-INF/openapi.yaml exists), then run: bash docs/record-challenge.sh
set -u
BASE=localhost:8080/api
OUT="$(dirname "$0")/challenge-http.md"
JSON='Content-Type: application/json'
KEY='X-Api-Key: demo-123'

run() {   # run <title> <curl args...>
  local title="$1"; shift
  {
    echo "## $title"; echo; echo '```'
    printf '$ curl -i'; printf " '%s'" "$@"; echo
    curl -s -i "$@" | grep -v -i -E '^(date|access-control-[a-z-]+|x-correlation-id):' | tr -d '\r'
    echo; echo '```'; echo
  } >> "$OUT"
}

cat > "$OUT" <<'MD'
# Challenge · HTTP transcript

Recorded against a fresh `mvn -q process-classes exec:java`. `Date`, `X-Correlation-Id` and the CORS headers
are left out. The client below never builds an order URL itself: every next step uses a URL taken from
the previous response's `Link` header / `_links` (`pay` → PUT, `cancel` → DELETE).

MD

run "1. Place an order - 201, Location, Link rels, Idempotency-Replayed: false" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: 7c1e' -H "$JSON" -d '{"lines":[{"productId":1,"quantity":2}]}'
run "2. Same key, same body (client retry) - stored 201 replayed" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: 7c1e' -H "$JSON" -d '{"lines":[{"productId":1,"quantity":2}]}'
run "3. Same key, different body - 422" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: 7c1e' -H "$JSON" -d '{"lines":[{"productId":2,"quantity":1}]}'
run "4. No Idempotency-Key - 428" \
    -X POST $BASE/orders -H "$KEY" -H "$JSON" -d '{"lines":[{"productId":1,"quantity":1}]}'
run "5. Stock was reserved once (Kettle 12 → 10)" $BASE/products/1

PAY=$(curl -s -i -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: 7c1e' -H "$JSON" \
      -d '{"lines":[{"productId":1,"quantity":2}]}' | tr -d '\r' | sed -n 's/^Link: <\(.*\)>; rel="pay"$/\1/p')
run "6. Follow rel=\"pay\" - 200, pay and cancel rels are gone" -X PUT "$PAY" -H "$KEY"
run "7. Pay again - 409" -X PUT "$PAY" -H "$KEY"
run "8. Cancel a PAID order - 409" -X DELETE $BASE/orders/o-1001 -H "$KEY"

CANCEL=$(curl -s -i -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: 9a2b' -H "$JSON" \
      -d '{"lines":[{"productId":2,"quantity":3}]}' | tr -d '\r' | sed -n 's/^Link: <\(.*\)>; rel="cancel"$/\1/p')
run "9. Second order (Mug x3), then follow rel=\"cancel\" - 200, stock restored" -X DELETE "$CANCEL" -H "$KEY"
run "10. Mug stock is back to 40" $BASE/products/2
run "11. Insufficient stock - 409" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: c3' -H "$JSON" -d '{"lines":[{"productId":3,"quantity":1}]}'
run "12. Unknown product - 404" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: c4' -H "$JSON" -d '{"lines":[{"productId":999,"quantity":1}]}'
run "13. Invalid quantity - 400" \
    -X POST $BASE/orders -H "$KEY" -H 'Idempotency-Key: c5' -H "$JSON" -d '{"lines":[{"productId":1,"quantity":0}]}'

{
  echo "## 14. OpenAPI document (first lines)"; echo; echo '```'
  echo "\$ curl -si localhost:8080/api/openapi | grep -i '^content-type'"
  curl -si $BASE/openapi | tr -d '\r' | grep -i '^content-type'
  echo "\$ curl -s localhost:8080/api/openapi | head -12"
  curl -s $BASE/openapi | head -12
  echo '```'; echo
} >> "$OUT"

echo "Wrote $OUT"
