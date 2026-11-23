# Task 3 · HTTP transcript

Recorded against a fresh `mvn -q compile exec:java`. `Date` and the CORS headers of Task 4 are left out
to keep the listing short. Every error body is `application/problem+json` (RFC 9457).

## 1. Filtered and sorted list - 200

```
$ curl -i 'localhost:8080/api/products?minPrice=5%20EUR&inStock=true&sort=price,desc'
HTTP/1.1 200 OK
X-Correlation-Id: 1823aeef-86ef-4578-9adb-d3ef4db1bf2a
Content-Type: application/json
Content-Length: 125

[{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":12},{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40}]

```

## 1b. Text search, case-insensitive - 200

```
$ curl -i 'localhost:8080/api/products?q=MU'
HTTP/1.1 200 OK
X-Correlation-Id: d569ce0a-aa2d-4544-85ad-971622415eb3
Content-Type: application/json
Content-Length: 61

[{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":40}]

```

## 2. Unparsable money - 400 from the ParamConverter

```
$ curl -i 'localhost:8080/api/products?minPrice=abc'
HTTP/1.1 400 Bad Request
X-Correlation-Id: 89127276-25b6-434c-93c2-2a88a2d6aade
Content-Type: application/problem+json
Connection: close
Content-Length: 125

{"detail":"Cannot parse Money from 'abc'","instance":"/api/products","status":400,"title":"Bad Request","type":"about:blank"}

```

## 2b. Money in another currency - 400

```
$ curl -i 'localhost:8080/api/products?maxPrice=10%20USD'
HTTP/1.1 400 Bad Request
X-Correlation-Id: ec8aa9fc-19ca-4474-95ed-d10091a02399
Content-Type: application/problem+json
Connection: close
Content-Length: 203

{"detail":"Validation failed","errors":[{"message":"minPrice and maxPrice must be in EUR","field":"priceCurrency"}],"instance":"/api/products","status":400,"title":"Invalid request","type":"about:blank"}

```

## 3. Sort by a field outside the whitelist - 400

```
$ curl -i 'localhost:8080/api/products?sort=colour,asc'
HTTP/1.1 400 Bad Request
X-Correlation-Id: d908034c-95f9-466d-9e71-5ed37ecb4cd9
Content-Type: application/problem+json
Connection: close
Content-Length: 222

{"detail":"Validation failed","errors":[{"message":"use field,asc or field,desc with field one of name, price, stock","field":"sort"}],"instance":"/api/products","status":400,"title":"Invalid request","type":"about:blank"}

```

## 4. Invalid POST - 400 with one error per violated constraint

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"name":"","sku":"kettle","price":0,"stock":-1}'
HTTP/1.1 400 Bad Request
X-Correlation-Id: 827a7463-3174-4ad2-9754-646246bf075f
Content-Type: application/problem+json
Connection: close
Content-Length: 367

{"detail":"Validation failed","errors":[{"message":"must not be blank","field":"name"},{"message":"must be greater than or equal to 0.01","field":"price"},{"message":"must match \"[A-Z]{2}-\\d{2}\"","field":"sku"},{"message":"must be greater than or equal to 0","field":"stock"}],"instance":"/api/products","status":400,"title":"Invalid request","type":"about:blank"}

```

## 4b. Invalid PATCH body - 400

```
$ curl -i '-X' 'PATCH' 'localhost:8080/api/products/2/stock' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"delta":5000}'
HTTP/1.1 400 Bad Request
X-Correlation-Id: 81c811d6-b4f6-4f60-bc88-ec7f7391eb6c
Content-Type: application/problem+json
Connection: close
Content-Length: 201

{"detail":"Validation failed","errors":[{"message":"must be less than or equal to 1000","field":"delta"}],"instance":"/api/products/2/stock","status":400,"title":"Invalid request","type":"about:blank"}

```

## 4c. Malformed JSON body - 400, not 500

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"name":'
HTTP/1.1 400 Bad Request
X-Correlation-Id: e87bcf47-3944-4207-a181-b86eda582112
Content-Type: application/problem+json
Connection: close
Content-Length: 144

{"detail":"Request body is not valid JSON for this resource","instance":"/api/products","status":400,"title":"Bad Request","type":"about:blank"}

```

## 5. Unknown product - 404 as problem+json

```
$ curl -i 'localhost:8080/api/products/99'
HTTP/1.1 404 Not Found
X-Correlation-Id: 9d5d5562-4a07-49a1-973f-93c98dc330c0
Content-Type: application/problem+json
Content-Length: 117

{"detail":"Product 99 not found","instance":"/api/products/99","status":404,"title":"Not Found","type":"about:blank"}

```

## 5b. Duplicate SKU - 409 as problem+json (no change in the resource)

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"name":"Mug","sku":"MG-01","price":6.50,"stock":1}'
HTTP/1.1 409 Conflict
X-Correlation-Id: c3e08aad-9e1f-4bb8-a392-e031de8c5480
Content-Type: application/problem+json
Content-Length: 132

{"detail":"A product with SKU MG-01 already exists","instance":"/api/products","status":409,"title":"Conflict","type":"about:blank"}

```

## 5c. Stock below zero - 409 as problem+json

```
$ curl -i '-X' 'PATCH' 'localhost:8080/api/products/3/stock' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"delta":-1}'
HTTP/1.1 409 Conflict
X-Correlation-Id: 7905db04-18d4-41c6-bf90-517150d94ab5
Content-Type: application/problem+json
Content-Length: 145

{"detail":"Product 3 has stock 0, cannot apply delta -1","instance":"/api/products/3/stock","status":409,"title":"Conflict","type":"about:blank"}

```

## 5d. Wrong Content-Type - 415 as problem+json

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: text/plain' '-d' 'Teapot'
HTTP/1.1 415 Unsupported Media Type
X-Correlation-Id: ef2dacf9-7e03-46e0-92bb-52a172b3c239
Content-Type: application/problem+json
Content-Length: 138

{"detail":"HTTP 415 Unsupported Media Type","instance":"/api/products","status":415,"title":"Unsupported Media Type","type":"about:blank"}

```

## 6. The 500 experiment - no stack trace leaks

For this call only, `ProductResource.one` temporarily started with
`throw new IllegalStateException("experiment: secret internals");` (reverted afterwards).
The body carries only a reference id; the exception message and stack trace appear in the server log under the same id.

```
$ curl -i 'localhost:8080/api/products/1'
HTTP/1.1 500 Internal Server Error
X-Correlation-Id: 6c0be3ac-78c7-4e06-b855-a4098c4ca39d
Content-Type: application/problem+json
Connection: close
Content-Length: 154

{"detail":"reference 6aa8185e-c9c4-4afd-8f56-a3a968f2b8ac","instance":"/api/products/1","status":500,"title":"Internal Server Error","type":"about:blank"}

```

Server log (first lines):

```
SEVERE: Unhandled error on /api/products/1, reference 6aa8185e-c9c4-4afd-8f56-a3a968f2b8ac
java.lang.IllegalStateException: experiment: secret internals
	at edu.itc.shop.api.ProductResource.one(ProductResource.java:39)
```
