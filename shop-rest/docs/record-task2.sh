#!/usr/bin/env bash
# Records the Task 2 transcript. Start the server first, on a fresh run
# (mvn -q compile exec:java), then run: bash docs/record-task2.sh
set -u
BASE=localhost:8080/api
OUT="$(dirname "$0")/task2-http.md"
JSON='Content-Type: application/json'
KEY='X-Api-Key: demo-123'          # writes need it since Task 4

run() {   # run <title> <curl args...>
  local title="$1"; shift
  {
    echo "## $title"; echo; echo '```'
    printf '$ curl -i'; printf " '%s'" "$@"; echo
    curl -s -i "$@" | grep -v -i '^date:' | tr -d '\r'
    echo; echo '```'; echo
  } >> "$OUT"
}

echo "# Task 2 · HTTP transcript" > "$OUT"
echo >> "$OUT"
echo "Recorded against a fresh \`mvn -q compile exec:java\`. \`Date\` headers are left out." >> "$OUT"
echo >> "$OUT"

run "Create a product - 201 with Location" -X POST $BASE/products -H "$KEY" -H "$JSON" \
    -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
run "Create with a duplicate SKU - 409" -X POST $BASE/products -H "$KEY" -H "$JSON" \
    -d '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
run "XML round trip - POST XML" -X POST $BASE/products -H "$KEY" -H 'Content-Type: application/xml' -H 'Accept: application/xml' \
    -d '<product><name>Strainer</name><sku>ST-01</sku><price>3.90</price><stock>25</stock></product>'
run "XML round trip - GET XML" $BASE/products/5 -H 'Accept: application/xml'
run "Replace a product - PUT 200" -X PUT $BASE/products/4 -H "$KEY" -H "$JSON" \
    -d '{"name":"Teapot XL","sku":"TP-01","price":34.00,"stock":5}'
run "Replace a missing product - PUT 404" -X PUT $BASE/products/99 -H "$KEY" -H "$JSON" \
    -d '{"name":"Ghost","sku":"GH-01","price":1.00,"stock":1}'
run "Change stock - PATCH 200" -X PATCH $BASE/products/2/stock -H "$KEY" -H "$JSON" -d '{"delta":-5}'
run "Change stock below zero - PATCH 409" -X PATCH $BASE/products/3/stock -H "$KEY" -H "$JSON" -d '{"delta":-1}'
run "Delete - 204" -X DELETE $BASE/products/4 -H "$KEY"
run "Delete again - 404" -X DELETE $BASE/products/4 -H "$KEY"
run "Wrong Content-Type - 415 (Jersey)" -X POST $BASE/products -H "$KEY" -H 'Content-Type: text/plain' -d 'Teapot'

echo "Wrote $OUT"
