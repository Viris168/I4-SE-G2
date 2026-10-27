# I4-SE-G2

## Bootstrapping with Maven and Spring Boot

### What does the `<parent>` give you?

`spring-boot-starter-parent` 4.1.1 sets the dependency versions and the build defaults (Java 25, compiler, tests, and the jar plugin). This project does not pick those versions itself.

### Why do the starters have no `<version>`?

The parent manages them. Maven fills in the version from the parent's `dependencyManagement`. Every starter below resolved to `4.1.1`.

### What does `spring-boot-maven-plugin` do at package time?

`package` first builds a thin jar of this project's classes. The plugin then repackages it into an executable jar. `java -jar` starts `JarLauncher`, which loads `BOOT-INF/lib` and `BOOT-INF/classes` and runs `edu.itc.shop.ShopApplication`.

### Dependency tree (first level)

```text
edu.itc.se:shop:jar:0.0.1-SNAPSHOT
+- org.springframework.boot:spring-boot-h2console:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-actuator:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-jdbc:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-validation:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-aspectj:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-devtools:jar:4.1.1:runtime (optional)
+- com.h2database:h2:jar:2.4.240:runtime
+- org.springframework.boot:spring-boot-starter-actuator-test:jar:4.1.1:test
+- org.springframework.boot:spring-boot-starter-jdbc-test:jar:4.1.1:test
+- org.springframework.boot:spring-boot-starter-validation-test:jar:4.1.1:test
\- org.springframework.boot:spring-boot-starter-webmvc-test:jar:4.1.1:test
```

### Jar size

`target/shop-0.0.1-SNAPSHOT.jar` is 29,980,119 bytes (about 29 MB). The thin jar, `shop-0.0.1-SNAPSHOT.jar.original`, is 5,352 bytes.

The executable jar is large because Tomcat, Spring, H2, and the other starters are copied into `BOOT-INF/lib`. The `.original` file is only this project's classes.

### Actuator health

`GET /actuator/health` returned HTTP 200:

```json
{"groups":["liveness","readiness"],"status":"UP"}
```

With `--debug`, `DataSourceAutoConfiguration` matched because the JDBC classes are on the classpath (`spring-boot-starter-jdbc` and H2):

```text
DataSourceAutoConfiguration matched:
   - @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)
   - @ConditionalOnMissingBean (types: io.r2dbc.spi.ConnectionFactory; SearchStrategy: all) did not find any beans (OnBeanCondition)
```

Startup then connected Hikari to `jdbc:h2:mem:...`.

## JDBC, JdbcClient and transactions

### Schema and data

`schema.sql` creates the `product` table (identity PK, `name VARCHAR(80) NOT NULL UNIQUE`, `price DECIMAL(10,2)`, `stock INT CHECK (stock >= 0)`). `data.sql` inserts five products. H2 runs in memory, so Spring Boot runs both scripts at startup without `spring.sql.init.mode`.

`application.yaml` enables the H2 console at `/h2-console`. No fixed URL is set, so Boot gives each application context its own in-memory database (`jdbc:h2:mem:<uuid>`). Tests that start different contexts then do not share a database, and `schema.sql` does not run twice against the same one. Startup prints the URL to use in the console (user `sa`, empty password):

```text
H2 console available at '/h2-console'. Database available at 'jdbc:h2:mem:<uuid>'
```

### Raw JDBC vs JdbcClient

| | `RawJdbcProductRepository.findAll()` | `ProductRepository.findAll()` |
|---|---|---|
| Lines in the method | 18 | 5 |
| Opens/closes connection, statement, result set | by hand (try-with-resources) | `JdbcClient` |
| Maps a row to `Product` | by hand, column by column | automatic (record components match column names) |
| `SQLException` | checked, wrapped by hand | translated to `DataAccessException` |

The raw version needs 18 lines for one query. Most of them are resource handling and mapping code that would be repeated in every method. `JdbcClient` handles the resources, the row mapping and the exceptions.

All SQL uses bound parameters (`:id`, `:delta`, `:pattern`, …). User input is never concatenated into the SQL string. For `findByNameContaining`, the `%` wildcards are added to the parameter value, not to the SQL.

### Transaction: `ProductService.restock`

`restock(fromId, toId, qty)` is `@Transactional`. It runs two `UPDATE`s on one connection with `autoCommit=false`:

1. `stock - qty` on `fromId`. The `WHERE ... AND stock + :delta >= 0` guard makes it affect 0 rows when there is not enough stock, and the method then throws `IllegalStateException`.
2. `stock + qty` on `toId`. If that product does not exist, it affects 0 rows and the method throws `IllegalStateException`.

`IllegalStateException` is a `RuntimeException`, so the transaction proxy rolls back. `ProductServiceTest` (`@SpringBootTest`) proves this:

- `restockRollsBackWhenSourceHasTooLittleStock`: qty 1000 fails, and both stocks are unchanged.
- `restockRollsBackFirstUpdateWhenTargetDoesNotExist`: the first `UPDATE` succeeds, the second hits a missing product, and the first update is undone. This is the test that actually needs the rollback.
- `restockMovesStockWhenEnoughUnits`: the happy path.

The test turns on DEBUG logging for `org.springframework.jdbc` and `org.springframework.transaction`. The output shows the rollback:

```text
DEBUG o.s.jdbc.support.JdbcTransactionManager : Creating new transaction with name [edu.itc.shop.service.ProductService.restock]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT
DEBUG o.s.jdbc.core.JdbcTemplate              : Executing prepared SQL statement [UPDATE product SET stock = stock + ? WHERE id = ? AND stock + ? >= 0]
DEBUG o.s.jdbc.support.JdbcTransactionManager : Initiating transaction rollback
DEBUG o.s.jdbc.support.JdbcTransactionManager : Rolling back JDBC transaction on Connection [HikariProxyConnection@1648668612 wrapping conn0: url=jdbc:h2:mem:<uuid> user=SA]
```

(Boot 4 configures `JdbcTransactionManager`, a subclass of `DataSourceTransactionManager`.)

`restock` must be called from outside `ProductService`. A call from another method of the same class would skip the proxy and run without a transaction.

`ProductRepositoryTest` (`@JdbcTest` + `@Import`) tests the repository on its own: find all/by id/by name, insert with a generated id, the negative-stock guard, delete, a check that the raw and `JdbcClient` versions return the same rows, and the duplicate-name case below.

```text
$ ./mvnw -q test
Tests run: 8  -- ProductRepositoryTest
Tests run: 3  -- ProductServiceTest
Tests run: 1  -- ShopApplicationTests
```

### Duplicate name: which exception and where it is translated

Inserting a second product named `Keyboard` throws **`org.springframework.dao.DuplicateKeyException`** (a `DataAccessException`). Its cause is H2's `JdbcSQLIntegrityConstraintViolationException`.

The stack trace shows where the translation happens:

```text
org.springframework.dao.DuplicateKeyException <- cause org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException
  at o.s.jdbc.support.SQLExceptionSubclassTranslator.doTranslate
  at o.s.jdbc.support.AbstractFallbackSQLExceptionTranslator.translate
  at o.s.jdbc.core.JdbcTemplate.translateException
  at o.s.jdbc.core.JdbcTemplate.update
  at o.s.jdbc.core.simple.DefaultJdbcClient$DefaultStatementSpec.update
  at edu.itc.shop.repository.ProductRepository.insert
  at o.s.dao.support.PersistenceExceptionTranslationInterceptor.invoke
  at edu.itc.shop.repository.ProductRepository$$SpringCGLIB$$0.insert
```

- `JdbcClient` delegates to `JdbcTemplate`. `JdbcTemplate` catches the `SQLException` and passes it to its `SQLExceptionTranslator`, which maps the SQL state to `DuplicateKeyException`. So the translation happens inside the JDBC template, before the exception leaves `insert()`.
- `@Repository` also wraps the bean in a proxy (`ProductRepository$$SpringCGLIB$$0`) with `PersistenceExceptionTranslationInterceptor`. That proxy translates any native persistence exception that is not yet a `DataAccessException` (for example from JPA or plain JDBC code). Here the exception is already a `DataAccessException`, so the proxy lets it through unchanged.

Because of this, callers only see Spring's database-independent `DataAccessException` hierarchy and never H2-specific classes.
