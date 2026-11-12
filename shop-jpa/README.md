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

## 2. Repositories, queries, projections and a paginated endpoint

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
