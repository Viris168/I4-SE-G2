# API session

Recorded against `shop-jpa` started with `--spring.profiles.active=dev`.

Seed data: 3 customers, 12 products, 30 orders.

## GET /api/orders?status=PAID&page=0&size=2&sort=placedAt,desc

```text
$ curl -s "localhost:8080/api/orders?status=PAID&page=0&size=2&sort=placedAt,desc"
{"content":[{"id":30,"status":"PAID","placedAt":"2026-09-02T05:00:00Z","customer":{"name":"Alan Turing"}},{"id":26,"status":"PAID","placedAt":"2026-09-02T01:00:00Z","customer":{"name":"Grace Hopper"}}],"page":{"size":2,"number":0,"totalElements":8,"totalPages":4}}
```

## GET /api/products?q=key&category=HARDWARE&maxPrice=60&inStock=true

```text
$ curl -s "localhost:8080/api/products?q=key&category=HARDWARE&maxPrice=60&inStock=true"
{"content":[{"id":1,"sku":"KB-1","name":"Compact Keyboard","amount":25.00,"stock":12},{"id":2,"sku":"KB-7","name":"Tenkeyless Keyboard","amount":59.00,"stock":7}],"page":{"size":20,"number":0,"totalElements":2,"totalPages":1}}
```

## GET /api/customers/ada@example.com/orders

```text
$ curl -s "localhost:8080/api/customers/ada@example.com/orders"
[{"id":28,"status":"CANCELLED","placedAt":"2026-09-02T03:00:00Z","lines":[{"sku":"MO-1","productName":"Wireless Mouse","quantity":1,"unitPrice":{"amount":20.00,"currency":"EUR"}},{"sku":"MN-1","productName":"Office Monitor","quantity":2,"unitPrice":{"amount":180.00,"currency":"EUR"}}],"total":{"amount":380.00,"currency":"EUR"}},{"id":25,"status":"NEW","placedAt":"2026-09-02T00:00:00Z","lines":[{"sku":"KB-1","productName":"Compact Keyboard","quantity":1,"unitPrice":{"amount":25.00,"currency":"EUR"}},{"sku":"KB-7","productName":"Tenkeyless Keyboard","quantity":2,"unitPrice":{"amount":59.00,"currency":"EUR"}}],"total":{"amount":143.00,"currency":"EUR"}},{"id":22,"status":"PAID","placedAt":"2026-09-01T21:00:00Z","lines":[{"sku":"TY-2","productName":"Puzzle Cube","quantity":1,"unitPrice":{"amount":15.00,"currency":"EUR"}},{"sku":"FD-1","productName":"Green Tea","quantity":2,"unitPrice":{"amount":8.50,"currency":"EUR"}}],"total":{"amount":32.00,"currency":"EUR"}},{"id":19,"status":"SHIPPED","placedAt":"2026-09-01T18:00:00Z","lines":[{"sku":"BK-1","productName":"Domain-Driven Design","quantity":1,"unitPrice":{"amount":45.00,"currency":"EUR"}},{"sku":"BK-2","productName":"Refactoring","quantity":2,"unitPrice":{"amount":40.00,"currency":"EUR"}}],"total":{"amount":125.00,"currency":"EUR"}},{"id":16,"status":"CANCELLED","placedAt":"2026-09-01T15:00:00Z","lines":[{"sku":"MO-1","productName":"Wireless Mouse","quantity":1,"unitPrice":{"amount":20.00,"currency":"EUR"}},{"sku":"MN-1","productName":"Office Monitor","quantity":2,"unitPrice":{"amount":180.00,"currency":"EUR"}}],"total":{"amount":380.00,"currency":"EUR"}},{"id":13,"status":"NEW","placedAt":"2026-09-01T12:00:00Z","lines":[{"sku":"KB-1","productName":"Compact Keyboard","quantity":1,"unitPrice":{"amount":25.00,"currency":"EUR"}},{"sku":"KB-7","productName":"Tenkeyless Keyboard","quantity":2,"unitPrice":{"amount":59.00,"currency":"EUR"}}],"total":{"amount":143.00,"currency":"EUR"}},{"id":10,"status":"PAID","placedAt":"2026-09-01T09:00:00Z","lines":[{"sku":"TY-2","productName":"Puzzle Cube","quantity":1,"unitPrice":{"amount":15.00,"currency":"EUR"}},{"sku":"FD-1","productName":"Green Tea","quantity":2,"unitPrice":{"amount":8.50,"currency":"EUR"}}],"total":{"amount":32.00,"currency":"EUR"}},{"id":7,"status":"SHIPPED","placedAt":"2026-09-01T06:00:00Z","lines":[{"sku":"BK-1","productName":"Domain-Driven Design","quantity":1,"unitPrice":{"amount":45.00,"currency":"EUR"}},{"sku":"BK-2","productName":"Refactoring","quantity":2,"unitPrice":{"amount":40.00,"currency":"EUR"}}],"total":{"amount":125.00,"currency":"EUR"}},{"id":4,"status":"CANCELLED","placedAt":"2026-09-01T03:00:00Z","lines":[{"sku":"MO-1","productName":"Wireless Mouse","quantity":1,"unitPrice":{"amount":20.00,"currency":"EUR"}},{"sku":"MN-1","productName":"Office Monitor","quantity":2,"unitPrice":{"amount":180.00,"currency":"EUR"}}],"total":{"amount":380.00,"currency":"EUR"}},{"id":1,"status":"NEW","placedAt":"2026-09-01T00:00:00Z","lines":[{"sku":"KB-1","productName":"Compact Keyboard","quantity":1,"unitPrice":{"amount":25.00,"currency":"EUR"}},{"sku":"KB-7","productName":"Tenkeyless Keyboard","quantity":2,"unitPrice":{"amount":59.00,"currency":"EUR"}}],"total":{"amount":143.00,"currency":"EUR"}}]
```

## SQL and transaction log

`GET /api/orders` selects only the projected columns, then counts the page:

```text
DEBUG org.hibernate.SQL : select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name
                          from orders o1_0
                          join customer c1_0 on c1_0.id=o1_0.customer_id
                          where o1_0.status=?
                          order by o1_0.placed_at desc
                          fetch first ? rows only
TRACE org.hibernate.orm.jdbc.bind : binding parameter (1:ENUM) <- [PAID]
DEBUG org.hibernate.SQL : select count(o1_0.id) from orders o1_0 where o1_0.status=?
```

`GET /api/products` combines the present filters in one read-only transaction. Absent filters are left out:

```text
DEBUG o.s.orm.jpa.JpaTransactionManager : Creating new transaction with name
    [edu.itc.shop.service.ProductService.search]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
DEBUG org.hibernate.SQL : select p1_0.id,p1_0.category,p1_0.name,p1_0.amount,p1_0.currency,p1_0.sku,p1_0.stock
                          from product p1_0
                          where lower(p1_0.name) like ? escape ''
                            and p1_0.category=?
                            and p1_0.amount<=?
                            and p1_0.stock>?
                          offset ? rows fetch first ? rows only
TRACE org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [%key%]
TRACE org.hibernate.orm.jdbc.bind : binding parameter (2:ENUM) <- [HARDWARE]
TRACE org.hibernate.orm.jdbc.bind : binding parameter (3:NUMERIC) <- [60]
```

`GET /api/customers/{email}/orders` loads lines and products with one join fetch, inside a read-only service transaction. The controller returns `OrderDto` records, not `Order` entities:

```text
DEBUG o.s.orm.jpa.JpaTransactionManager : Creating new transaction with name
    [edu.itc.shop.service.OrderService.findWithLinesByCustomerEmail]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
DEBUG org.hibernate.SQL : select distinct o1_0.id,o1_0.customer_id,l1_0.order_id,l1_0.id,l1_0.product_id,
                          p1_0.id,p1_0.category,p1_0.name,p1_0.amount,p1_0.currency,p1_0.sku,p1_0.stock,
                          l1_0.quantity,l1_0.amount,l1_0.currency,o1_0.placed_at,o1_0.status,o1_0.version
                          from orders o1_0
                          join order_line l1_0 on o1_0.id=l1_0.order_id
                          join product p1_0 on p1_0.id=l1_0.product_id
                          join customer c1_0 on c1_0.id=o1_0.customer_id
                          where c1_0.email=?
                          order by o1_0.placed_at desc
TRACE org.hibernate.orm.jdbc.bind : binding parameter (1:VARCHAR) <- [ada@example.com]
```
