#!/usr/bin/env bash
# Captures the live Task 5 HTTP calls against a running server on localhost:8080.
# Requires SHOP_SEED_CUSTOMER_PASSWORD to be set in the environment (the password alice was seeded with).
set -u
BASE="${BASE:-http://localhost:8080}"
ALICE_PASS="${SHOP_SEED_CUSTOMER_PASSWORD:?set SHOP_SEED_CUSTOMER_PASSWORD}"

echo "=== 1. CORS preflight (allowed origin) ==="
curl -si -X OPTIONS "$BASE/api/orders" \
  -H 'Origin: http://localhost:5173' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: authorization,content-type' | tr -d '\r'
echo

echo "=== 2. CORS preflight (rejected evil origin) ==="
curl -si -X OPTIONS "$BASE/api/orders" \
  -H 'Origin: http://evil.example' \
  -H 'Access-Control-Request-Method: POST' | tr -d '\r'
echo

echo "=== 3. Security headers (HEAD /api/products) ==="
curl -sI "$BASE/api/products" | grep -iE '^HTTP/|x-frame|strict-transport|content-security|referrer|x-content|x-request-id' | tr -d '\r'
echo

echo "=== 4. Failed login (triggers SECURITY login.failed) ==="
curl -si -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"wrong"}' | tr -d '\r'
echo

echo "=== 5. Login as alice (triggers SECURITY login.ok) ==="
LOGIN_JSON=$(curl -s -X POST "$BASE/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"alice\",\"password\":\"$ALICE_PASS\"}")
TOKEN=$(printf '%s' "$LOGIN_JSON" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
echo "Got token (${#TOKEN} chars)"
echo

echo "=== 6. Post XSS payload review as alice ==="
curl -si -X POST "$BASE/api/reviews" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"productId":2,"stars":1,"text":"<img src=x onerror=\"document.title='\''pwned'\''\">"}' | tr -d '\r'
echo

echo "=== 7a. JSON API response (application/json + nosniff) ==="
curl -si "$BASE/api/products/2/reviews" | grep -iE '^HTTP/|content-type|x-content-type-options|^\[|^$' | tr -d '\r'
echo

echo "=== 7b. Thymeleaf HTML page (th:text escaping) ==="
curl -si "$BASE/products/2/reviews.html" | tr -d '\r'
echo

echo "=== 8. Access denied as alice (triggers SECURITY access.denied) ==="
curl -si "$BASE/api/orders?customer=bob" -H "Authorization: Bearer $TOKEN" | tr -d '\r'
echo
curl -si -X POST "$BASE/api/orders/1/ship" -H "Authorization: Bearer $TOKEN" | tr -d '\r'
echo
