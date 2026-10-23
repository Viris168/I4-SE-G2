# I4-SE-G2

## Bootstrapping with Maven and Spring Boot

### What does the `<parent>` give you?

`spring-boot-starter-parent` 4.1.1 sets the dependency versions and the build defaults (Java 25, compiler, tests, and the jar plugin). This project does not pick those versions itself.

### Why do the starters have no `<version>`?

The parent manages them. Maven fills in the version from the parent's `dependencyManagement`. Every starter below resolved to `4.1.1`.

### What does `spring-boot-maven-plugin` do at package time?

`package` first builds a thin jar of this project's classes. The plugin then repackages it into an executable jar. `java -jar` starts `JarLauncher`, which loads `BOOT-INF/lib` and `BOOT-INF/classes` and runs `com.example.shop.ShopApplication`.

### Dependency tree (first level)

```text
com.example:shop:jar:0.0.1-SNAPSHOT
+- org.springframework.boot:spring-boot-h2console:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-actuator:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-session-jdbc:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-validation:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-webmvc:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-starter-aspectj:jar:4.1.1:compile
+- org.springframework.boot:spring-boot-devtools:jar:4.1.1:runtime (optional)
+- com.h2database:h2:jar:2.4.240:runtime
+- org.springframework.boot:spring-boot-starter-actuator-test:jar:4.1.1:test
+- org.springframework.boot:spring-boot-starter-session-jdbc-test:jar:4.1.1:test
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

With `--debug`, `DataSourceAutoConfiguration` matched because the JDBC classes are on the classpath (`spring-boot-starter-session-jdbc` and H2):

```text
DataSourceAutoConfiguration matched:
   - @ConditionalOnClass found required classes 'javax.sql.DataSource', 'org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType' (OnClassCondition)
   - @ConditionalOnMissingBean (types: io.r2dbc.spi.ConnectionFactory; SearchStrategy: all) did not find any beans (OnBeanCondition)
```

Startup then connected Hikari to `jdbc:h2:mem:...`.
