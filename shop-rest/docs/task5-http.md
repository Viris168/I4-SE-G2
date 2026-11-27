# Task 5 · HTTP transcript

Recorded against a fresh `mvn -q compile exec:java`. `Date` and the CORS headers of Task 4 are omitted
to keep the listing concise. All errors return `application/problem+json` (RFC 9457).

## 1. Paginated list (page 1, size 10) - 200 with X-Total-Count and Link headers

```
$ curl -i 'localhost:8080/api/products?page=1&size=10'
HTTP/1.1 200 OK
Link: <http://localhost:8080/api/products?size=10&page=0>; rel="first"
Link: <http://localhost:8080/api/products?size=10&page=0>; rel="prev"
Link: <http://localhost:8080/api/products?size=10&page=2>; rel="next"
Link: <http://localhost:8080/api/products?size=10&page=3>; rel="last"
X-Total-Count: 33
X-Correlation-Id: 34a0c87a-8442-42ac-adc9-54d24c22169f
Content-Type: application/json
Content-Length: 653

[{"id":14,"name":"Item 11","price":13.25,"sku":"IT-11","stock":3},{"id":15,"name":"Item 12","price":14.00,"sku":"IT-12","stock":6},{"id":16,"name":"Item 13","price":14.75,"sku":"IT-13","stock":9},{"id":17,"name":"Item 14","price":15.50,"sku":"IT-14","stock":12},{"id":18,"name":"Item 15","price":16.25,"sku":"IT-15","stock":0},{"id":19,"name":"Item 16","price":17.00,"sku":"IT-16","stock":3},{"id":20,"name":"Item 17","price":17.75,"sku":"IT-17","stock":6},{"id":21,"name":"Item 18","price":18.50,"sku":"IT-18","stock":9},{"id":22,"name":"Item 19","price":19.25,"sku":"IT-19","stock":12},{"id":23,"name":"Item 20","price":20.00,"sku":"IT-20","stock":0}]

```

## 2. Single product with ETag and Cache-Control - 200

```
$ curl -i 'localhost:8080/api/products/1'
HTTP/1.1 200 OK
ETag: "1"
Cache-Control: private, no-transform, max-age=60
X-Correlation-Id: 6518f20a-f327-4f1c-bb6e-be8ae9107f2d
Content-Type: application/json
Content-Length: 63

{"id":1,"name":"Kettle","price":24.90,"sku":"KT-01","stock":12}

```

## 3. Conditional GET with matching If-None-Match - 304 Not Modified

```
$ curl -i 'localhost:8080/api/products/1' '-H' 'If-None-Match: "1"'
HTTP/1.1 304 Not Modified
ETag: "1"
X-Correlation-Id: d8f63de7-2431-4648-9cfb-b498ddca474c


```

## 4. PUT without If-Match - 428 Precondition Required (problem+json)

```
$ curl -i '-X' 'PUT' 'localhost:8080/api/products/1' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
HTTP/1.1 428 Precondition Required
X-Correlation-Id: b74876d4-19a7-4d9f-ae99-9b767eeba1ea
Content-Type: application/problem+json
Content-Length: 135

{"detail":"If-Match header is required","instance":"/api/products/1","status":428,"title":"Precondition Required","type":"about:blank"}

```

## 5. PUT with stale If-Match - 412 Precondition Failed (problem+json)

```
$ curl -i '-X' 'PUT' 'localhost:8080/api/products/1' '-H' 'X-Api-Key: demo-123' '-H' 'If-Match: "0"' '-H' 'Content-Type: application/json' '-d' '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
HTTP/1.1 412 Precondition Failed
ETag: "1"
X-Correlation-Id: 6c12992b-4172-4763-bc4e-81b8fb7d7454
Content-Type: application/problem+json
Content-Length: 141

{"detail":"ETag does not match current version","instance":"/api/products/1","status":412,"title":"Precondition Failed","type":"about:blank"}

```

## 6. PUT with matching If-Match - 200 OK with new ETag

```
$ curl -i '-X' 'PUT' 'localhost:8080/api/products/1' '-H' 'X-Api-Key: demo-123' '-H' 'If-Match: "1"' '-H' 'Content-Type: application/json' '-d' '{"name":"Kettle XL","sku":"KT-01","price":29.90,"stock":12}'
HTTP/1.1 200 OK
ETag: "2"
X-Correlation-Id: b70b8056-9853-458a-b287-e59beb444b80
Content-Type: application/json
Content-Length: 66

{"id":1,"name":"Kettle XL","price":29.90,"sku":"KT-01","stock":12}

```

## 7. Media-type versioning: v1 (default application/json)

```
$ curl -i 'localhost:8080/api/products/1'
HTTP/1.1 200 OK
ETag: "2"
Cache-Control: private, no-transform, max-age=60
X-Correlation-Id: 59fb65ec-403a-4cac-9a38-52da172f0250
Content-Type: application/json
Content-Length: 66

{"id":1,"name":"Kettle XL","price":29.90,"sku":"KT-01","stock":12}

```

## 7b. Media-type versioning: v1 (explicit application/vnd.shop.v1+json)

```
$ curl -i 'localhost:8080/api/products/1' '-H' 'Accept: application/vnd.shop.v1+json'
HTTP/1.1 200 OK
ETag: "2"
Cache-Control: private, no-transform, max-age=60
X-Correlation-Id: 400b26a7-bca3-4657-b086-d4b5f9ed8435
Content-Type: application/vnd.shop.v1+json
Content-Length: 66

{"id":1,"name":"Kettle XL","price":29.90,"sku":"KT-01","stock":12}

```

## 8. Media-type versioning: v2 (application/vnd.shop.v2+json)

```
$ curl -i 'localhost:8080/api/products/1' '-H' 'Accept: application/vnd.shop.v2+json'
HTTP/1.1 200 OK
ETag: "2"
Cache-Control: private, no-transform, max-age=60
X-Correlation-Id: 8977e6f4-7b90-467f-bee3-b698d3ca0c30
Content-Type: application/vnd.shop.v2+json
Content-Length: 111

{"available":true,"id":1,"name":"Kettle XL","price":{"amount":29.90,"currency":"EUR"},"sku":"KT-01","stock":12}

```

## 9. Sub-resource: POST a review - 201 Created with Location

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products/1/reviews' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"stars":5,"author":"Ana","text":"Boils fast."}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/1/reviews/1
X-Correlation-Id: 0f912e15-9b0c-4eb8-b983-2f229c21905b
Content-Type: application/json
Content-Length: 68

{"author":"Ana","id":1,"productId":1,"stars":5,"text":"Boils fast."}

```

## 9b. Sub-resource: POST an invalid review - 400 problem+json

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products/1/reviews' '-H' 'X-Api-Key: demo-123' '-H' 'Content-Type: application/json' '-d' '{"stars":6,"author":"","text":"Too high"}'
HTTP/1.1 400 Bad Request
X-Correlation-Id: 36726878-8f1c-469b-ab15-3b0d29ef03bb
Content-Type: application/problem+json
Connection: close
Content-Length: 249

{"detail":"Validation failed","errors":[{"message":"must not be blank","field":"author"},{"message":"must be less than or equal to 5","field":"stars"}],"instance":"/api/products/1/reviews","status":400,"title":"Invalid request","type":"about:blank"}

```

## 10. Sub-resource: GET reviews for product 1 - 200 OK

```
$ curl -i 'localhost:8080/api/products/1/reviews'
HTTP/1.1 200 OK
X-Correlation-Id: ad984ccc-93ec-4924-8e32-c8572fc9350e
Content-Type: application/json
Content-Length: 70

[{"author":"Ana","id":1,"productId":1,"stars":5,"text":"Boils fast."}]

```

## 11. Sub-resource: GET reviews for unknown product 999 - 404 Not Found

```
$ curl -i 'localhost:8080/api/products/999/reviews'
HTTP/1.1 404 Not Found
X-Correlation-Id: 4cf707da-7fe1-4933-9d90-68ebf06da117
Content-Type: application/problem+json
Content-Length: 127

{"detail":"Product 999 not found","instance":"/api/products/999/reviews","status":404,"title":"Not Found","type":"about:blank"}

```

