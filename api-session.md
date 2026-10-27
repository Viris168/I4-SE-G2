# API session - Task 4

Recorded against the packaged app (`java -jar target/shop-0.0.1-SNAPSHOT.jar`)
with the five products from `data.sql`. `Date` headers are left out.

## List all products

```
$ curl -i localhost:8080/api/products
HTTP/1.1 200
Content-Type: application/json

[{"id":1,"name":"Wireless mouse","price":49.90,"stock":40},{"id":2,"name":"Keyboard","price":89.00,"stock":3},{"id":3,"name":"Notebook","price":10.00,"stock":120},{"id":4,"name":"USB hub","price":24.50,"stock":30},{"id":5,"name":"Webcam","price":59.00,"stock":15}]
```

## Search by name (case-insensitive)

```
$ curl -i 'localhost:8080/api/products?q=KEY'
HTTP/1.1 200
Content-Type: application/json

[{"id":2,"name":"Keyboard","price":89.00,"stock":3}]
```

## Get one product

```
$ curl -i localhost:8080/api/products/1
HTTP/1.1 200
Content-Type: application/json

{"id":1,"name":"Wireless mouse","price":49.90,"stock":40}
```

## Get a missing product - 404

```
$ curl -i localhost:8080/api/products/999
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"Product 999 not found","instance":"/api/products/999","status":404,"title":"Not Found"}
```

## Create a product - 201 with Location

```
$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
       -d '{"name":"USB-C cable","price":7.90,"stock":25}'
HTTP/1.1 201
Location: /api/products/6
Content-Type: application/json

{"id":6,"name":"USB-C cable","price":7.90,"stock":25}
```

## Create with an invalid body - 400 with field errors

```
$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
       -d '{"name":"","price":0,"stock":-1}'
HTTP/1.1 400
Content-Type: application/problem+json

{"detail":"Validation failed","instance":"/api/products","status":400,"title":"Bad Request","errors":[{"field":"stock","message":"must be greater than or equal to 0"},{"field":"price","message":"must be greater than or equal to 0.01"},{"field":"name","message":"must not be blank"}]}
```

## Create with a duplicate name - 409

```
$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' \
       -d '{"name":"USB-C cable","price":7.90,"stock":25}'
HTTP/1.1 409
Content-Type: application/problem+json

{"detail":"A product with this name already exists","instance":"/api/products","status":409,"title":"Conflict"}
```

## Change stock - 200

```
$ curl -i -X PATCH localhost:8080/api/products/6/stock -H 'Content-Type: application/json' -d '{"delta":-2}'
HTTP/1.1 200
Content-Type: application/json

{"id":6,"name":"USB-C cable","price":7.90,"stock":23}
```

## Remove more stock than available - 409

```
$ curl -i -X PATCH localhost:8080/api/products/6/stock -H 'Content-Type: application/json' -d '{"delta":-30}'
HTTP/1.1 409
Content-Type: application/problem+json

{"detail":"Product 6 has 23 units, cannot remove 30","instance":"/api/products/6/stock","status":409,"title":"Conflict"}
```

## Change stock of a missing product - 404

```
$ curl -i -X PATCH localhost:8080/api/products/999/stock -H 'Content-Type: application/json' -d '{"delta":1}'
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"Product 999 not found","instance":"/api/products/999/stock","status":404,"title":"Not Found"}
```

## Delete - 204, then 404

```
$ curl -i -X DELETE localhost:8080/api/products/6
HTTP/1.1 204

$ curl -i -X DELETE localhost:8080/api/products/6
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"Product 6 not found","instance":"/api/products/6","status":404,"title":"Not Found"}
```

## Malformed JSON - 400, no stack trace

```
$ curl -i -X POST localhost:8080/api/products -H 'Content-Type: application/json' -d '{bad json'
HTTP/1.1 400
Content-Type: application/problem+json

{"detail":"Failed to read request","instance":"/api/products","status":400,"title":"Bad Request"}
```
