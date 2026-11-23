# Task 2 · HTTP transcript

Recorded against a fresh `mvn -q compile exec:java`. `Date` headers are left out.

## Create a product - 201 with Location

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'Content-Type: application/json' '-d' '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/4
Content-Type: application/json
Content-Length: 62

{"id":4,"name":"Teapot","price":31.00,"sku":"TP-01","stock":5}

```

## Create with a duplicate SKU - 409

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'Content-Type: application/json' '-d' '{"name":"Teapot","sku":"TP-01","price":31.00,"stock":5}'
HTTP/1.1 409 Conflict
Content-Length: 0


```

## XML round trip - POST XML

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'Content-Type: application/xml' '-H' 'Accept: application/xml' '-d' '<product><name>Strainer</name><sku>ST-01</sku><price>3.90</price><stock>25</stock></product>'
HTTP/1.1 201 Created
Location: http://localhost:8080/api/products/5
Content-Type: application/xml
Content-Length: 154

<?xml version="1.0" encoding="UTF-8" standalone="yes"?><product id="5"><name>Strainer</name><sku>ST-01</sku><price>3.90</price><stock>25</stock></product>

```

## XML round trip - GET XML

```
$ curl -i 'localhost:8080/api/products/5' '-H' 'Accept: application/xml'
HTTP/1.1 200 OK
Content-Type: application/xml
Content-Length: 154

<?xml version="1.0" encoding="UTF-8" standalone="yes"?><product id="5"><name>Strainer</name><sku>ST-01</sku><price>3.90</price><stock>25</stock></product>

```

## Replace a product - PUT 200

```
$ curl -i '-X' 'PUT' 'localhost:8080/api/products/4' '-H' 'Content-Type: application/json' '-d' '{"name":"Teapot XL","sku":"TP-01","price":34.00,"stock":5}'
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 65

{"id":4,"name":"Teapot XL","price":34.00,"sku":"TP-01","stock":5}

```

## Replace a missing product - PUT 404

```
$ curl -i '-X' 'PUT' 'localhost:8080/api/products/99' '-H' 'Content-Type: application/json' '-d' '{"name":"Ghost","sku":"GH-01","price":1.00,"stock":1}'
HTTP/1.1 404 Not Found
Content-Length: 0


```

## Change stock - PATCH 200

```
$ curl -i '-X' 'PATCH' 'localhost:8080/api/products/2/stock' '-H' 'Content-Type: application/json' '-d' '{"delta":-5}'
HTTP/1.1 200 OK
Content-Type: application/json
Content-Length: 59

{"id":2,"name":"Mug","price":6.50,"sku":"MG-01","stock":35}

```

## Change stock below zero - PATCH 409

```
$ curl -i '-X' 'PATCH' 'localhost:8080/api/products/3/stock' '-H' 'Content-Type: application/json' '-d' '{"delta":-1}'
HTTP/1.1 409 Conflict
Content-Length: 0


```

## Delete - 204

```
$ curl -i '-X' 'DELETE' 'localhost:8080/api/products/4'
HTTP/1.1 204 No Content


```

## Delete again - 404

```
$ curl -i '-X' 'DELETE' 'localhost:8080/api/products/4'
HTTP/1.1 404 Not Found
Content-Length: 0


```

## Wrong Content-Type - 415 (Jersey)

```
$ curl -i '-X' 'POST' 'localhost:8080/api/products' '-H' 'Content-Type: text/plain' '-d' 'Teapot'
HTTP/1.1 415 Unsupported Media Type
Content-Length: 0


```

