# Configuration Reference

English | [简体中文](CONFIGURATION_zh.md)

This document provides a complete reference for all configuration options, auto-configuration behavior, advanced usage patterns, and common troubleshooting tips for mybatis-plus-geometry.

---

## Table of Contents

- [Configuration Properties](#configuration-properties)
- [Auto-Configuration](#auto-configuration)
- [Database Strategy Details](#database-strategy-details)
- [SQL Interceptor](#sql-interceptor)
- [Custom Extension](#custom-extension)
- [Multi-DataSource Setup](#multi-datasource-setup)
- [FAQ / Troubleshooting](#faq--troubleshooting)
- [Version Compatibility](#version-compatibility)

---

## Configuration Properties

All properties are under the prefix `mybatis.geometry` in `application.yml` or `application.properties`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-srid` | `int` | `4326` | Default SRID for geometry objects without explicit SRID. 4326 = WGS84 (GPS coordinates). |
| `interceptor-enabled` | `boolean` | `true` | Enable/disable the SQL interceptor that automatically wraps geometry columns in SELECT queries. |
| `database-type` | `enum` | *(auto-detect)* | Force a specific database type. Values: `MYSQL`, `POSTGRESQL`. If not set, auto-detected from DataSource URL. |

### YAML Example (Full)

```yaml
mybatis:
  geometry:
    default-srid: 4326
    interceptor-enabled: true
    database-type: MYSQL
```

### Properties Example

```properties
mybatis.geometry.default-srid=4326
mybatis.geometry.interceptor-enabled=true
mybatis.geometry.database-type=MYSQL
```

### Property Details

#### `default-srid`

The Spatial Reference System Identifier applied to geometry objects that have no explicit SRID set (SRID = 0).

- **4326** (WGS84): Standard GPS latitude/longitude. Most common choice.
- **3857** (Web Mercator): Used by web maps (Google Maps, OpenStreetMap tiles).
- Custom SRID: Set any valid EPSG code if your data uses a local coordinate system.

> **Note**: This only affects geometries created without an explicit SRID. If your code does `point.setSRID(4326)`, this property has no effect on that geometry.

#### SRID Management Best Practices

The library uses a two-level priority for SRID resolution:

```
Business code explicit setSRID()  (highest priority)
        ↓ fallback if SRID == 0
Global config: mybatis.geometry.default-srid  (lowest priority)
```

**Recommended approach**: Use `GeometryFactoryProvider.getFactory()` to create geometry objects. The factory is pre-configured with your `default-srid` value, so all geometries created through it automatically carry the correct SRID:

```java
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;

// Factory is globally configured via mybatis.geometry.default-srid
GeometryFactory factory = GeometryFactoryProvider.getFactory();

// This point automatically has SRID = 4326 (or whatever you configured)
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 ✓ — no manual setSRID() needed
```

**When to use explicit `setSRID()`**: Only when a specific geometry needs a different SRID than the global default:

```java
// Global default is 4326, but this specific geometry uses a local CRS
Point localPoint = factory.createPoint(new Coordinate(500000, 4649776));
localPoint.setSRID(32650);  // UTM zone 50N
```

**Anti-pattern to avoid**: Creating geometry with `new GeometryFactory()` directly — this produces SRID=0 objects that rely on runtime fallback:

```java
// ❌ Bad: SRID=0, relies on ensureSrid() fallback at write time
GeometryFactory rawFactory = new GeometryFactory();
Point point = rawFactory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 0 — works but fragile

// ✅ Good: SRID set at creation, explicit and traceable
GeometryFactory factory = GeometryFactoryProvider.getFactory();
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 — guaranteed
```

**Summary**:
- Set `mybatis.geometry.default-srid` once in `application.yml` for your project
- Always use `GeometryFactoryProvider.getFactory()` to create geometry objects
- Use `setSRID()` only for exceptional cases with a different coordinate system
- The TypeHandler's `ensureSrid()` is a safety net, not the primary SRID source

---

#### `interceptor-enabled`

Controls whether the `GeometryFieldInterceptor` is registered as a MyBatis plugin.

- **true** (default): The interceptor automatically rewrites SELECT SQL to wrap geometry columns with the appropriate function (`HEX()` for MySQL, `encode(ST_AsBinary(...))` for PostGIS).
- **false**: No SQL rewriting. You must manually wrap geometry columns in your mapper XML or annotation queries.

When disabled, a SELECT on a geometry column returns raw binary which TypeHandlers cannot parse. You would need:

```sql
-- MySQL: manual wrapping
SELECT id, name, HEX(location) AS location FROM warehouse

-- PostGIS: manual wrapping
SELECT id, name, encode(ST_AsBinary(location), 'hex') AS location FROM warehouse
```

#### `database-type`

Overrides automatic database detection. Useful when:

- Your connection pool URL cannot be inspected at startup
- You are using a non-standard JDBC URL format
- You want deterministic behavior in tests

If not specified, the library inspects the `DataSource` JDBC URL:
- URL contains `mysql` or `mariadb` → `MYSQL`
- URL contains `postgresql` or `postgres` → `POSTGRESQL`
- Otherwise → defaults to `MYSQL` (with a warning log)

---

## Auto-Configuration

### Activation Conditions

The auto-configuration (`GeometryAutoConfiguration`) activates when **both** conditions are met:

1. `com.baomidou.mybatisplus.core.mapper.BaseMapper` is on the classpath
2. `org.locationtech.jts.geom.Geometry` is on the classpath

If either class is missing, the auto-configuration is silently skipped.

### Registered Beans

When active, the following beans are registered (unless already defined by the user):

| Bean | Type | Condition |
|------|------|-----------|
| `geometryHandlerStrategy` | `GeometryHandlerStrategy` | `@ConditionalOnMissingBean` |
| `pointTypeHandler` | `PointTypeHandler` | `@ConditionalOnMissingBean` |
| `polygonTypeHandler` | `PolygonTypeHandler` | `@ConditionalOnMissingBean` |
| `lineStringTypeHandler` | `LineStringTypeHandler` | `@ConditionalOnMissingBean` |
| `geometryFieldInterceptor` | `GeometryFieldInterceptor` | `@ConditionalOnMissingBean` + `interceptor-enabled=true` |

### Spring Boot Compatibility

| Spring Boot Version | Auto-Configuration Mechanism |
|--------------------|------------------------------|
| 2.7.x | `META-INF/spring.factories` |
| 3.0+ | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

Both files are included — the library works with Spring Boot 2.7+ and 3.x without any changes.

---

## Database Strategy Details

### MySQL Strategy

| Aspect | Behavior |
|--------|----------|
| **Write format** | WKB `byte[]` (4-byte LE SRID prefix + standard WKB) |
| **Read wrapper** | `HEX(column) AS column` |
| **Input function** | `?` (direct binary via `PreparedStatement.setBytes()`) |
| **Compatible DBs** | MySQL 8.0+, MariaDB 10.5+ |

### PostGIS Strategy

| Aspect | Behavior |
|--------|----------|
| **Write format** | Hex WKB `String` (4-byte LE SRID prefix + standard WKB hex) |
| **Read wrapper** | `(srid_le_hex \|\| encode(ST_AsBinary(column), 'hex')) AS column` |
| **Input function** | `?` (PostGIS auto-recognizes hex WKB string) |
| **Compatible DBs** | PostgreSQL 12+ with PostGIS 3.0+ |

### Detection Priority

1. If `mybatis.geometry.database-type` is explicitly set → use that
2. Else, inspect `DataSource.getConnection().getMetaData().getURL()`
3. If URL inspection fails, try `getMetaData().getDriverName()`
4. Fallback: `MYSQL` (with warning logged)

---

## SQL Interceptor

### How It Works

The `GeometryFieldInterceptor` intercepts MyBatis `StatementHandler.prepare()` calls:

1. **Only SELECT** queries are processed (INSERT/UPDATE/DELETE are skipped)
2. Scans the entity class for fields annotated with `@PointTableField`, `@PolygonTableField`, or `@LineStringTableField`
3. Rewrites `SELECT *` or specific column references to wrap geometry columns
4. Caches field metadata per entity class for performance

### Requirements

For the interceptor to work correctly:

- Entity class **must** have `@TableName(autoResultMap = true)`
- Mapper **must** extend `BaseMapper<YourEntity>`
- Geometry fields **must** be annotated with one of the `@*TableField` annotations

### What Gets Rewritten

```sql
-- Original (MyBatis Plus generates)
SELECT id, name, location, boundary FROM warehouse WHERE id = ?

-- After interceptor (MySQL)
SELECT id, name, HEX(location) AS location, HEX(boundary) AS boundary FROM warehouse WHERE id = ?

-- After interceptor (PostGIS)
SELECT id, name, (srid_hex || encode(ST_AsBinary(location), 'hex')) AS location, ... FROM warehouse WHERE id = ?
```

### What Is NOT Rewritten

- Custom SQL in XML mappers (manual queries)
- `@Select` annotation queries
- Queries where entity class cannot be determined from mapper
- INSERT / UPDATE / DELETE statements

---

## Custom Extension

### Override Default Strategy

Define your own `GeometryHandlerStrategy` bean to replace the auto-configured one:

```java
@Configuration
public class CustomGeometryConfig {
    
    @Bean
    public GeometryHandlerStrategy geometryHandlerStrategy() {
        // Your custom implementation
        return new MyCustomStrategy();
    }
}
```

Since the auto-configuration uses `@ConditionalOnMissingBean`, your bean takes priority.

### Override TypeHandler

Similarly, you can provide custom TypeHandlers:

```java
@Bean
public PointTypeHandler pointTypeHandler() {
    // Custom SRID or strategy
    return new PointTypeHandler(3857, myStrategy);
}
```

### Custom Geometry Type

To support a new geometry type (e.g., `MultiPoint`):

1. Create a `MultiPointTypeHandler` extending `AbstractGeometryTypeHandler<MultiPoint>`
2. Create a `@MultiPointTableField` annotation
3. Register in your configuration

```java
public class MultiPointTypeHandler extends AbstractGeometryTypeHandler<MultiPoint> {
    
    @Override
    protected MultiPoint parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) return null;
        return WkbUtil.fromWkbAsMultiPoint(hexString);
    }
    
    @Override
    protected void validateGeometry(MultiPoint geometry) throws SQLException {
        // Custom validation
    }
    
    @Override
    protected String getGeometryTypeName() {
        return "MultiPoint";
    }
}
```

### Disable Interceptor and Use Manual Queries

If you prefer full control over SQL:

```yaml
mybatis:
  geometry:
    interceptor-enabled: false
```

Then in your mapper XML:

```xml
<!-- MySQL -->
<select id="findById" resultMap="warehouseResultMap">
    SELECT id, name, HEX(location) AS location 
    FROM warehouse WHERE id = #{id}
</select>

<!-- PostGIS -->
<select id="findById" resultMap="warehouseResultMap">
    SELECT id, name, encode(ST_AsBinary(location), 'hex') AS location 
    FROM warehouse WHERE id = #{id}
</select>
```

---

## Multi-DataSource Setup

In a multi-datasource environment, each datasource may connect to a different database type. The auto-configuration binds a single strategy to the primary DataSource.

### Approach: Per-DataSource Configuration

```java
@Configuration
public class GeometryMultiDsConfig {
    
    @Bean
    @Primary
    public GeometryHandlerStrategy primaryStrategy(@Qualifier("primaryDataSource") DataSource ds) {
        return GeometryStrategyFactory.detectStrategy(ds);
    }
    
    // For secondary datasource, create separate TypeHandlers
    @Bean
    public PointTypeHandler secondaryPointTypeHandler(
            @Qualifier("secondaryDataSource") DataSource ds) {
        GeometryHandlerStrategy strategy = GeometryStrategyFactory.detectStrategy(ds);
        return new PointTypeHandler(4326, strategy);
    }
}
```

### Approach: Explicit Type per DataSource

```java
@Bean
public GeometryHandlerStrategy mysqlStrategy() {
    return GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL);
}

@Bean
public GeometryHandlerStrategy postgisStrategy() {
    return GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
}
```

> **Note**: The SQL interceptor uses a single strategy. If you need different wrapping for different datasources, disable the interceptor and use manual SQL.

---

## FAQ / Troubleshooting

### Q: Geometry field always returns `null` in SELECT

**Cause**: Missing `autoResultMap = true` on the entity class.

**Fix**:
```java
@TableName(value = "your_table", autoResultMap = true)  // ← required!
public class YourEntity { ... }
```

### Q: `org.apache.ibatis.type.TypeException: Could not set parameters`

**Cause**: JDBC driver cannot handle the geometry format.

**Check**:
1. Verify `database-type` matches your actual database
2. For PostGIS, ensure the PostGIS extension is enabled: `CREATE EXTENSION IF NOT EXISTS postgis;`
3. For MySQL, ensure the column type is `GEOMETRY`, `POINT`, `POLYGON`, or `LINESTRING`

### Q: SRID mismatch error on INSERT (MySQL)

**Cause**: Table column has `SRID 4326` constraint but geometry object has SRID 0.

**Fix**: Ensure geometry has correct SRID:
```java
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
point.setSRID(4326);  // ← explicitly set SRID
```

Or configure the default SRID:
```yaml
mybatis:
  geometry:
    default-srid: 4326
```

### Q: `HEX()` function not found (PostgreSQL)

**Cause**: The library defaults to MySQL strategy (using `HEX()`) but you're on PostgreSQL.

**Fix**: Either:
1. Set `mybatis.geometry.database-type: POSTGRESQL`
2. Or ensure your JDBC URL contains `postgresql`/`postgres` for auto-detection

### Q: Interceptor does not rewrite my custom SQL

**Expected behavior**: The interceptor only processes queries generated by MyBatis Plus's built-in methods (`selectById`, `selectList`, etc.). Custom `@Select` or XML queries are not rewritten.

**Solution**: Manually wrap geometry columns in your custom SQL (see [Disable Interceptor](#disable-interceptor-and-use-manual-queries)).

### Q: Performance concern with interceptor

The interceptor adds minimal overhead:
- Field metadata is cached per entity class (one-time reflection cost)
- SQL rewriting uses simple string operations
- Only SELECT statements are intercepted

In benchmarks, the overhead is < 1ms per query.

### Q: Can I use this without Spring Boot?

The auto-configuration requires Spring Boot, but the core classes (`WkbUtil`, TypeHandlers, Strategy) can be used standalone:

```java
// Manual setup without Spring
PointTypeHandler handler = new PointTypeHandler(4326, new MySQLGeometryStrategy());
// Register handler manually in MyBatis configuration
```

---

## Version Compatibility

| mybatis-plus-geometry | Java | Spring Boot | MyBatis Plus | MySQL | PostgreSQL + PostGIS |
|----------------------|------|-------------|-------------|-------|---------------------|
| 1.0.x | 17+ | 2.7+ / 3.x | 3.5+ | 8.0+ | 12+ / 3.0+ |

### Dependency Versions Used

| Dependency | Version | Scope |
|-----------|---------|-------|
| jts-core | 1.19.0 | `api` (transitive) |
| commons-codec | 1.16.0 | `implementation` |
| slf4j-api | 2.0.9 | `implementation` |
| mybatis-plus-boot-starter | 3.5.7 | `compileOnly` (user provides) |
| jackson-databind | 2.15.3 | `compileOnly` (optional) |
| spring-boot-autoconfigure | 3.2.2 | `compileOnly` (user provides) |

> Jackson serializers are optional. If `jackson-databind` is not on the classpath, GeoJSON support is simply unavailable (no errors).
