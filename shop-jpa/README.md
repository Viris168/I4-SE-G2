# shop-jpa — Lab 03

## 1. Entities and mappings, validated by Flyway

### Deliberate Schema Mismatches (`ddl-auto: validate`)

1. **Missing column (`sku` renamed to `code` in `V1__init.sql` only):**

```text
Caused by: org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing column [sku] in table [product]
	at org.hibernate.tool.schema.internal.AbstractSchemaValidator.validateTable(AbstractSchemaValidator.java:141) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
	at org.hibernate.tool.schema.internal.GroupedSchemaValidatorImpl.validateTables(GroupedSchemaValidatorImpl.java:42) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
	at org.hibernate.tool.schema.internal.AbstractSchemaValidator.performValidation(AbstractSchemaValidator.java:97) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
```

2. **Sequence increment mismatch (`product_seq` declared `INCREMENT BY 1` in `V1__init.sql` while `Product` specifies `allocationSize = 50`):**

```text
Caused by: org.hibernate.MappingException: The increment size of the [product_seq] sequence is set to [50] in the entity mapping but the mapped database sequence increment size is [1]
	at org.hibernate.id.enhanced.SequenceStyleGenerator.validatedIncrementSize(SequenceStyleGenerator.java:312) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
	at org.hibernate.id.enhanced.SequenceStyleGenerator.adjustIncrementSize(SequenceStyleGenerator.java:264) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
	at org.hibernate.id.enhanced.SequenceStyleGenerator.configure(SequenceStyleGenerator.java:208) ~[hibernate-core-7.4.5.Final.jar:7.4.5.Final]
```

## 2. Associations, cascading and entity identity

`OrderLine.order` is the owning side (`order_id` column). `Order.lines` is the inverse side with `mappedBy = "order"`, `cascade = ALL` and `orphanRemoval = true`. `addLine` / `removeLine` keep both sides in sync, and `getLines()` returns a read-only view.

### Persisting an order: 1 + N inserts, no update (`OrderCascadeTest`)

Only `em.persist(order)` is called; the two lines follow through cascade. `order_id` is already set by `addLine`, so no fix-up `UPDATE` is needed.

```text
DEBUG org.hibernate.SQL : insert into orders (customer_id,placed_at,status,version,id) values (?,?,?,?,?)
DEBUG org.hibernate.SQL : insert into order_line (order_id,product_id,quantity,amount,currency,id) values (?,?,?,?,?,?)
DEBUG org.hibernate.SQL : insert into order_line (order_id,product_id,quantity,amount,currency,id) values (?,?,?,?,?,?)
```

### Removing a line: orphan removal

`order.removeLine(order.getLines().get(0))` followed by `flush()`:

```text
DEBUG org.hibernate.SQL : delete from order_line where id=?
```

### Removing the order: cascade REMOVE

The remaining line is deleted before the order, so the foreign key is never violated:

```text
DEBUG org.hibernate.SQL : delete from order_line where id=?
DEBUG org.hibernate.SQL : delete from orders where id=? and version=?
```

### Entity identity (`EntityIdentityTest`)

`equals` compares the class and a non-null `id`; `hashCode` returns a constant per class. A new `Product` added to a `HashSet` before `persist` is still found after `flush` assigns its id, and a copy reloaded in a fresh persistence context is `equals` to the original but not `==`.

### The `toString` pitfall

`Order.toString()` was temporarily changed to print `lines`, and `OrderLine.toString()` to print `order`.

Printing an order loaded outside a transaction (detached, `lines` still a lazy proxy):

```text
org.hibernate.LazyInitializationException: Cannot lazily initialize collection of role 'edu.itc.shop.domain.Order.lines' with key '1' (no session)
```

Printing the same order inside a transaction (lines load, then each line prints its order, which prints its lines, ...):

```text
java.lang.StackOverflowError
```

Reverted: `toString` on every entity uses scalar fields only (`id`, `status`, `quantity`, ...), never associations.

## 3. Repositories, queries, projections and a paginated endpoint

### Boot-time derived query typo

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

### `/api/orders` projection SQL

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

## 4. Entity states, dirty checking, transactions and optimistic locking

Tests: `OrderServiceTest` (managed, detached, read-only), `FlushOrderTest` (flush order), and `OptimisticLockingTest` (optimistic locking and retry).

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

### Optimistic locking and retry (`OptimisticLockingTest`)

`Order.version` (`@Version`, `BIGINT NOT NULL DEFAULT 0` in `V1__init.sql`) makes Hibernate include `and version=?` in every `UPDATE`. When two threads load the same `Order` at `version = 0` (`CountDownLatch(2)` in `secondWriterFailsWithOptimisticLock`), the first commit increments `version` to `1`, and the second finds `0` matching rows and rolls back with `ObjectOptimisticLockingFailureException`.

`OrderFacade.changeStatusWithRetry` lives in a separate `@Service` bean outside `OrderService`, because Spring's `@Transactional` proxy commits and throws `ObjectOptimisticLockingFailureException` when `OrderService.changeStatus` returns. Retrying up to 3 times lets both concurrent writers succeed and ends at `version = versionBefore + 2`.

Excerpt from `OptimisticLockingTest` (`org.springframework.orm.jpa=DEBUG`, `org.springframework.transaction.interceptor=TRACE` in `application-test.yml`):

```text
TRACE o.s.t.i.TransactionInterceptor : Getting transaction for [edu.itc.shop.service.OrderService.changeStatus]
DEBUG org.hibernate.SQL              : select o1_0.id,o1_0.customer_id,o1_0.placed_at,o1_0.status,o1_0.version from orders o1_0 where o1_0.id=?
DEBUG org.hibernate.SQL              : update orders set customer_id=?,placed_at=?,status=?,version=? where id=? and version=?
DEBUG o.s.orm.jpa.JpaTransactionManager : Initiating transaction rollback after commit exception
org.springframework.orm.ObjectOptimisticLockingFailureException: Batch update returned unexpected row count from update 0 (expected row count 1 but was 0) [update orders set customer_id=?,placed_at=?,status=?,version=? where id=? and version=?] for entity [edu.itc.shop.domain.Order with id '2']
DEBUG o.s.orm.jpa.JpaTransactionManager : Rolling back JPA transaction on EntityManager [SessionImpl(1169592289<open>)]
TRACE o.s.t.i.TransactionInterceptor : Getting transaction for [edu.itc.shop.service.OrderService.changeStatus]
DEBUG org.hibernate.SQL              : update orders set customer_id=?,placed_at=?,status=?,version=? where id=? and version=?
```

## 5. N+1, performance tuning and a second migration

<!-- Task 5 -->

Tests: `NPlusOneTest`, `ProductNameReportTest`, `BatchInsertTest`, `OpenInViewTest`, `FlywayMigrationTest`, and `ReportTimingTest` (tagged `perf`). Statements are counted with `QueryCounter` (`Statistics.getPrepareStatementCount()`). The seed data is 50 PAID orders × 3 lines over 30 products.

### Deviation from the handout: where `@BatchSize` lives

In Hibernate 7 a `@BatchSize` on a collection overrides every other setting (`LoadQueryInfluencers.effectiveBatchSize`). With `@BatchSize(16)` on `Order.lines`, the "naive" loop would also run 5 statements, and the 51-statement demo could never pass. Fix C is therefore on a second, read-only mapping of the same rows:

```java
@OneToMany(mappedBy = "order")
@BatchSize(size = 16)
private List<OrderLine> batchedLines;
```

For the same reason `default_batch_fetch_size=32` is not set globally. Only `ProductNameReportTest.WithDefaultBatchFetchSize32` switches it on.

### Lines per PAID order: statements and median time

Median of 20 runs after 20 warm-up runs, SQL logging off, in-memory H2:

| variant                 | statements | median ms |
|-------------------------|-----------:|----------:|
| naive (lazy loop)       |         51 |       4.2 |
| join fetch              |          1 |       1.6 |
| @EntityGraph            |          1 |       3.1 |
| @BatchSize(16)          |          5 |       1.9 |

The times are small because H2 runs in the same JVM with no network round trip. On a real database every extra statement costs a round trip, so the gap between 51 and 1 grows a lot.

### Adding `line.getProduct().getName()`

| variant           | without global batch size | with `default_batch_fetch_size=32` |
|-------------------|--------------------------:|-----------------------------------:|
| naive             |       81 (1 + 50 + 30)    |                  4 (1 + 2 + 1)     |
| join fetch        |       31 (1 + 30)         |                  2 (1 + 1)         |
| @EntityGraph      |        1                  |                  1                 |
| @BatchSize(16)    |       35 (5 + 30)         |                  6 (5 + 1)         |

Join fetch and `@BatchSize` regress into N+1 on `product` (one SELECT per distinct product). Only the entity graph, which also fetches `lines.product`, stays at 1. `default_batch_fetch_size=32` caps every lazy association at `ceil(n / 32)` extra statements. The explicit `@BatchSize(16)` on `batchedLines` still wins over the global 32.

### JDBC batching

`hibernate.jdbc.batch_size=50`, `order_inserts=true`, `order_updates=true`. `BatchInsertTest` saves 1 000 products with `saveAll` in one transaction:

```text
BatchInsertTest: inserts=1000, prepared statements=22
TRACE org.hibernate.orm.jdbc.batch : Executing JDBC batch (50 / 50) - [edu.itc.shop.domain.Product#INSERT]
... (20 batches)
```

In Hibernate 7 the logger is `org.hibernate.orm.jdbc.batch`; the handout's `org.hibernate.engine.jdbc.batch` is the Hibernate 6 name.

**IDENTITY experiment** (scratch copy, not committed): `Product` switched to `GenerationType.IDENTITY` and `id BIGINT GENERATED BY DEFAULT AS IDENTITY`:

```text
BatchInsertTest: inserts=1000, prepared statements=1000
```

No "Executing JDBC batch" lines at all. With IDENTITY, Hibernate needs the database to generate each id at insert time, so every row is its own INSERT and batching is disabled. A sequence with `allocationSize = 50` lets Hibernate hand out ids in memory and send the INSERTs in batches.

### `open-in-view=false`

`GET /api/orders/{id}/lines-broken` loads the order with `findById` and touches `getLines()` in the controller, after the repository transaction has closed:

```text
$ curl -s -o /dev/null -w "%{http_code}" localhost:8080/api/orders/1/lines-broken
500
ERROR ... Request processing failed: org.hibernate.LazyInitializationException: Cannot lazily initialize collection of role 'edu.itc.shop.domain.Order.lines' ...
```

`OpenInViewTest` asserts the same `LazyInitializationException` by calling the controller method. The fix is `GET /api/orders/{id}/lines`, which delegates to `OrderService.findLines`, so the mapping runs inside `@Transactional(readOnly = true)`.

### `V2__indexes.sql`

```text
INFO o.f.core.internal.command.DbValidate : Successfully validated 2 migrations
INFO o.f.core.internal.command.DbMigrate  : Migrating schema "PUBLIC" to version "1 - init"
INFO o.f.core.internal.command.DbMigrate  : Migrating schema "PUBLIC" to version "2 - indexes"
INFO o.f.core.internal.command.DbMigrate  : Successfully applied 2 migrations to schema "PUBLIC", now at version v2
```

The database is in-memory, so each start applies both migrations. `flyway_schema_history` holds the two migrations, plus the row Flyway 11 adds when it creates the table (`FlywayMigrationTest`):

```text
installed_rank=-1  version=null  << Flyway Schema History table created >>
installed_rank=1   version=1     init
installed_rank=2   version=2     indexes
```

**EXPLAIN of the `/api/orders` query, before (V1 only):**

```text
FROM "PUBLIC"."ORDERS" "O1_0"
    /* PUBLIC.ORDERS.tableScan */
    /* WHERE O1_0.STATUS = 'PAID' */
INNER JOIN "PUBLIC"."CUSTOMER" "C1_0"
    /* PUBLIC.PRIMARY_KEY_5: ID = O1_0.CUSTOMER_ID */
ORDER BY 3 DESC
FETCH FIRST 2 ROWS ONLY
```

**After (V1 + V2):**

```text
FROM "PUBLIC"."ORDERS" "O1_0"
    /* PUBLIC.ORDERS.tableScan */
    /* WHERE O1_0.STATUS = 'PAID' */
INNER JOIN "PUBLIC"."CUSTOMER" "C1_0"
    /* PUBLIC.PRIMARY_KEY_5: ID = O1_0.CUSTOMER_ID */
ORDER BY 3 DESC
FETCH FIRST 2 ROWS ONLY
```

The plan does not change: `/api/orders` filters on `status` and sorts on `placed_at`, and none of the three V2 indexes starts with `status`. An index on `orders (status, placed_at DESC)` would serve it. The new indexes do help the customer queries, for example `WHERE customer_id = ? ORDER BY placed_at DESC`:

```text
before: /* PUBLIC.FK_ORDERS_CUSTOMER_INDEX_8: CUSTOMER_ID = CAST(1 AS BIGINT) */   then sorts on placed_at
after:  /* PUBLIC.IDX_ORDERS_CUSTOMER_PLACED: CUSTOMER_ID = CAST(1 AS BIGINT) */   index already in placed_at order
```
