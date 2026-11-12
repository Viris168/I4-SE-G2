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
