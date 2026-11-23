# Challenge · HTTP transcript

Recorded against a fresh `mvn -q process-classes exec:java`. `Date`, `X-Correlation-Id` and the CORS headers
are left out. The client below never builds an order URL itself: every next step uses a URL taken from
the previous response's `Link` header / `_links` (`pay` → PUT, `cancel` → DELETE).

## 1. Place an order - 201, Location, Link rels, Idempotency-Replayed: false

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: 7c1e' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":1,"quantity":2}]}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/orders/o-1001
Link: <http://localhost:8080/api/orders/o-1001>; rel="self"
Link: <http://localhost:8080/api/orders/o-1001/payment>; rel="pay"
Link: <http://localhost:8080/api/orders/o-1001>; rel="cancel"
Link: <http://localhost:8080/api/products/1>; rel="product"
Idempotency-Replayed: false
Content-Type: application/json
Content-Length: 301

{"id":"o-1001","status":"NEW","total":49.80,"lines":[{"productId":1,"quantity":2}],"_links":{"self":"http://localhost:8080/api/orders/o-1001","pay":"http://localhost:8080/api/orders/o-1001/payment","cancel":"http://localhost:8080/api/orders/o-1001","product-1":"http://localhost:8080/api/products/1"}}

```

## 2. Same key, same body (client retry) - stored 201 replayed

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: 7c1e' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":1,"quantity":2}]}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/orders/o-1001
Link: <http://localhost:8080/api/orders/o-1001>; rel="self"
Link: <http://localhost:8080/api/orders/o-1001/payment>; rel="pay"
Link: <http://localhost:8080/api/orders/o-1001>; rel="cancel"
Link: <http://localhost:8080/api/products/1>; rel="product"
Idempotency-Replayed: true
Content-Type: application/json
Content-Length: 301

{"id":"o-1001","status":"NEW","total":49.80,"lines":[{"productId":1,"quantity":2}],"_links":{"self":"http://localhost:8080/api/orders/o-1001","pay":"http://localhost:8080/api/orders/o-1001/payment","cancel":"http://localhost:8080/api/orders/o-1001","product-1":"http://localhost:8080/api/products/1"}}

```

## 3. Same key, different body - 422

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: 7c1e' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":2,"quantity":1}]}'
HTTP/1.1 422 Unprocessable Content
Content-Type: application/problem+json
Content-Length: 148

{"detail":"Idempotency-Key reused with a different body","instance":"/api/orders","status":422,"title":"Unprocessable Content","type":"about:blank"}

```

## 4. No Idempotency-Key - 428

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":1,"quantity":1}]}'
HTTP/1.1 428 Precondition Required
Content-Type: application/problem+json
Content-Length: 135

{"detail":"Idempotency-Key header required","instance":"/api/orders","status":428,"title":"Precondition Required","type":"about:blank"}

```

## 5. Stock was reserved once (Kettle 12 → 10)

```
$ curl -i 'localhost:8080/api/products/1'
HTTP/1.1 200 OK
ETag: "2"
Cache-Control: private, no-transform, max-age=60
Content-Type: application/json
Content-Length: 63

{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":10}

```

## 6. Follow rel="pay" - 200, pay and cancel rels are gone

```
$ curl -i '-X' 'PUT' 'http://localhost:8080/api/orders/o-1001/payment' '-H' 'X-Api-Key: demo-123'
HTTP/1.1 200 OK
Link: <http://localhost:8080/api/orders/o-1001>; rel="self"
Link: <http://localhost:8080/api/products/1>; rel="product"
Content-Type: application/json
Content-Length: 195

{"id":"o-1001","status":"PAID","total":49.80,"lines":[{"productId":1,"quantity":2}],"_links":{"self":"http://localhost:8080/api/orders/o-1001","product-1":"http://localhost:8080/api/products/1"}}

```

## 7. Pay again - 409

```
$ curl -i '-X' 'PUT' 'http://localhost:8080/api/orders/o-1001/payment' '-H' 'X-Api-Key: demo-123'
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
Content-Length: 145

{"detail":"Order o-1001 is PAID and cannot be paid","instance":"/api/orders/o-1001/payment","status":409,"title":"Conflict","type":"about:blank"}

```

## 8. Cancel a PAID order - 409

```
$ curl -i '-X' 'DELETE' 'localhost:8080/api/orders/o-1001' '-H' 'X-Api-Key: demo-123'
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
Content-Length: 142

{"detail":"Order o-1001 is PAID and cannot be cancelled","instance":"/api/orders/o-1001","status":409,"title":"Conflict","type":"about:blank"}

```

## 9. Second order (Mug x3), then follow rel="cancel" - 200, stock restored

```
$ curl -i '-X' 'DELETE' 'http://localhost:8080/api/orders/o-1002' '-H' 'X-Api-Key: demo-123'
HTTP/1.1 200 OK
Link: <http://localhost:8080/api/orders/o-1002>; rel="self"
Link: <http://localhost:8080/api/products/2>; rel="product"
Content-Type: application/json
Content-Length: 200

{"id":"o-1002","status":"CANCELLED","total":19.50,"lines":[{"productId":2,"quantity":3}],"_links":{"self":"http://localhost:8080/api/orders/o-1002","product-2":"http://localhost:8080/api/products/2"}}

```

## 10. Mug stock is back to 40

```
$ curl -i 'localhost:8080/api/products/2'
HTTP/1.1 200 OK
ETag: "3"
Cache-Control: private, no-transform, max-age=60
Content-Type: application/json
Content-Length: 59

{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40}

```

## 11. Insufficient stock - 409

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: c3' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":3,"quantity":1}]}'
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
Content-Length: 135

{"detail":"Product 3 has stock 0, cannot apply delta -1","instance":"/api/orders","status":409,"title":"Conflict","type":"about:blank"}

```

## 12. Unknown product - 404

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: c4' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":999,"quantity":1}]}'
HTTP/1.1 404 Not Found
Content-Type: application/problem+json
Content-Length: 113

{"detail":"Product 999 not found","instance":"/api/orders","status":404,"title":"Not Found","type":"about:blank"}

```

## 13. Invalid quantity - 400

```
$ curl -i '-X' 'POST' 'localhost:8080/api/orders' '-H' 'X-Api-Key: demo-123' '-H' 'Idempotency-Key: c5' '-H' 'Content-Type: application/json' '-d' '{"lines":[{"productId":1,"quantity":0}]}'
HTTP/1.1 400 Bad Request
Content-Type: application/problem+json
Connection: close
Content-Length: 194

{"detail":"Validation failed","errors":[{"message":"must be greater than or equal to 1","field":"quantity"}],"instance":"/api/orders","status":400,"title":"Invalid request","type":"about:blank"}

```

## 14. OpenAPI document (first lines)

```
$ curl -si localhost:8080/api/openapi | grep -i '^content-type'
Content-Type: application/yaml
$ curl -s localhost:8080/api/openapi | head -12
---
openapi: 3.1.0
tags:
- name: OpenAPI
- name: Orders
  description: "Place, pay and cancel orders by following links"
- name: Products
  description: Product catalogue
paths:
  /openapi:
    get:
      summary: This API's OpenAPI 3.1 document
```

