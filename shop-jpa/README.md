# shop-jpa

Lab 03, task 3: Spring Data repositories, queries, projections, and paged endpoints.

## Boot-time derived query typo

Spring Data parses every repository method name while the application starts. A misspelled property fails the boot, before any request is served.

Method that was added to `OrderRepository`, then removed:

```java
List<Order> findByCustomerEmaliAndStatus(String email, OrderStatus status);
```

`./mvnw spring-boot:run` stopped with:

```text
org.springframework.beans.factory.UnsatisfiedDependencyException: Error creating bean with name 'orderService' ...
Error creating bean with name 'orderRepository' ...
No property 'emali' found for type 'Customer'; Did you mean 'email'; Traversed path: Order.customer

Caused by: org.springframework.data.repository.query.QueryCreationException:
Cannot create query for method [OrderRepository.findByCustomerEmaliAndStatus(java.lang.String,edu.itc.shop.domain.OrderStatus)];
No property 'emali' found for type 'Customer'; Did you mean 'email'; Traversed path: Order.customer

Caused by: org.springframework.data.core.PropertyReferenceException:
No property 'emali' found for type 'Customer'; Did you mean 'email'; Traversed path: Order.customer
```

`findByCustomerEmali…` walks `Order.customer` and then looks for `emali` on `Customer`. The property is `email`, so the repository bean is never created and the application does not start. The method was removed after this run. The correct query is `findByCustomerEmailAndStatusOrderByPlacedAtDesc`.

## `/api/orders` projection SQL

`GET /api/orders?status=PAID&page=0&size=2&sort=placedAt,desc` returns Spring Data's `page` envelope. The query selects only the columns the summary exposes:

```text
DEBUG org.hibernate.SQL : select o1_0.id,o1_0.status,o1_0.placed_at,c1_0.name
                          from orders o1_0
                          join customer c1_0 on c1_0.id=o1_0.customer_id
                          where o1_0.status=?
                          order by o1_0.placed_at desc
                          fetch first ? rows only
DEBUG org.hibernate.SQL : select count(o1_0.id) from orders o1_0 where o1_0.status=?
```

`findByStatus` uses `select new OrderSummaryRow(...)`, which implements the `OrderSummary` interface. A derived query for that interface selects every column of `customer`; the constructor query keeps the select list to `id`, `status`, `placed_at`, and `name`.

`Page<ProductView> findByCategory` is also a constructor query, because `amount` is `price.amount` on the embedded `Money`, not a field of `Product`.

The three HTTP transcripts, including the read-only transaction for `GET /api/customers/{email}/orders`, are in `api-session.md`.

## Task 4 · Entity states and dirty checking

Tests: `OrderServiceTest` (managed, detached, read-only) and `FlushOrderTest` (flush order). Optimistic locking is the other half of Task 4.

### Managed change: no `save()` needed

`OrderService.markPaid(id)` runs in `@Transactional`, loads the order and calls `setStatus(PAID)`. There is no `save()`. At commit, Hibernate compares the entity with the snapshot it took when loading it (dirty checking) and writes the change itself. The test clears `Statistics` before the call and asserts exactly one entity update and two statements:

```text
DEBUG org.hibernate.SQL : select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status,o1_0.version from orders o1_0 where o1_0.id=?
DEBUG org.hibernate.SQL : update orders set customer_id=?,placed_at=?,status=?,version=? where id=? and version=?
```

### Detached change: nothing happens until `save()`, which is a merge

`products.findById(id)` runs in the repository's own short transaction. When it returns, the context is closed and the product is **detached**. `p.setStock(99)` then only changes a Java object, so a reload still shows the old stock. `products.save(p)` on an entity that already has an id is a **merge**: Hibernate first SELECTs the current row, copies the detached state onto it, then UPDATEs. The test asserts one load and one update:

```text
DEBUG org.hibernate.SQL : select p1_0.id,p1_0.category,p1_0.name,p1_0.amount,p1_0.currency,p1_0.sku,p1_0.stock from product p1_0 where p1_0.id=?
DEBUG org.hibernate.SQL : update product set category=?,name=?,amount=?,currency=?,sku=?,stock=? where id=?
```

### The read-only trap

Under `@Transactional(readOnly = true)`, Spring sets Hibernate's flush mode to `MANUAL` and loads entities read-only. A method that modifies an entity there does not fail: the change is silently dropped. `changeInsideReadOnlyTransactionIsNeverFlushed` calls a deliberately wrong test-only method (`ReadOnlyTrap.cancel`) and asserts zero updates and an unchanged status. Read-only is right for queries such as `OrderService.view(id)`, and wrong for anything that writes.

### Flush order: INSERT before DELETE

In one transaction, deleting the tag `sale` and saving a new tag `sale` fails at `flush()`, even though the delete was called first. Hibernate does not run statements in call order: at flush it executes all INSERTs, then UPDATEs, then DELETEs. So the new row hits the unique index while the old one still exists:

```text
DEBUG org.hibernate.SQL : insert into tag (name,id) values (?,?)
WARN  org.hibernate.orm.jdbc.error : Unique index or primary key violation: "PUBLIC.CONSTRAINT_143 INDEX PUBLIC.CONSTRAINT_INDEX_14 ON PUBLIC.TAG(NAME NULLS FIRST) VALUES ( /* 1 */ 'sale' )"
→ org.springframework.dao.DataIntegrityViolationException
```

Fix: call `tags.flush()` right after `delete`, so the DELETE reaches the database before the INSERT is queued:

```text
DEBUG org.hibernate.SQL : delete from tag where id=?
DEBUG org.hibernate.SQL : insert into tag (name,id) values (?,?)
```

Both variants are kept as tests: `insertRunsBeforeDeleteSoReusingTheUniqueNameFails` and `flushingTheDeleteFirstFixesIt`.
