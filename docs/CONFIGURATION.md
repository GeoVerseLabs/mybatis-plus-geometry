# Configuration Reference

English | [简体中文](CONFIGURATION_zh.md)

This document provides a complete reference for all configuration options, auto-configuration behavior, advanced usage patterns, and common troubleshooting tips for mybatis-plus-geometry.

---

## Table of Contents

- [Configuration Properties](#configuration-properties)
- [Auto-Configuration](#auto-configuration)
- [Database Strategy Details](#database-strategy-details)
- [GeoJSON](#geojson)
- [SQL Interceptor (Legacy, Optional)](#sql-interceptor-legacy-optional)
- [Custom Extension](#custom-extension)
- [Multi-DataSource Setup](#multi-datasource-setup)
- [FAQ / Troubleshooting](#faq--troubleshooting)
- [Version Compatibility](#version-compatibility)

---

## Configuration Properties

All properties are under the prefix `mybatis.geometry` in `application.yml` or `application.properties`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `default-srid` | `int` | `4326` | SRID applied to geometries whose SRID is 0. 4326 = WGS84 (GPS coordinates). |
| `database-type` | `enum` | *(auto-detect)* | `MYSQL` (also MariaDB) or `POSTGRESQL`. If not set, detected from the DataSource. |
| `write-validation` | `enum` | `BASIC` | Validation before database writes: `FULL` (OGC `isValid()`), `BASIC` (finite coordinates), `NONE`. |
| `preserve-z` | `boolean` | `false` | Write Z ordinates to PostGIS. MySQL and MariaDB are always written in 2D. |
| `coordinate-sequence` | `enum` | `ARRAY` | Coordinate storage of geometries created by the library: `ARRAY` or `PACKED`. |
| `interceptor-enabled` | `boolean` | `false` | Register the legacy SELECT interceptor. Not needed: TypeHandlers read geometry columns natively. |
| `geojson.coordinate-precision` | `Integer` | *(full)* | Decimal places written per ordinate (0–15). RFC 7946 recommends 6. |
| `geojson.validation` | `enum` | `FULL` | GeoJSON input validation: `FULL` (also OGC `isValid()` for polygons) or `BASIC`. |
| `geojson.coordinate-range-validation` | `Boolean` | *(auto)* | Reject longitudes outside ±180 / latitudes outside ±90. Unset: enabled when `default-srid` is 4326. |
| `geojson.fast-double-parsing` | `boolean` | `true` | Parse coordinates with Jackson's fast double parser (Jackson 2.14+, ignored on older versions). |

### YAML Example (Full)

```yaml
mybatis:
  geometry:
    default-srid: 4326
    database-type: MYSQL
    write-validation: BASIC
    preserve-z: false
    coordinate-sequence: ARRAY
    interceptor-enabled: false
    geojson:
      coordinate-precision: 6
      validation: FULL
      coordinate-range-validation: true
      fast-double-parsing: true
```

### Properties Example

```properties
mybatis.geometry.default-srid=4326
mybatis.geometry.database-type=MYSQL
mybatis.geometry.write-validation=BASIC
mybatis.geometry.geojson.coordinate-precision=6
```

### Property Details

#### `default-srid`

The Spatial Reference System Identifier applied to geometry objects that have no explicit SRID set (SRID = 0).

- **4326** (WGS84): Standard GPS latitude/longitude. Most common choice.
- **3857** (Web Mercator): Used by web maps (Google Maps, OpenStreetMap tiles).
- **0**: Store geometries without a spatial reference (MySQL `SRID 0` columns, Cartesian data).
- Custom SRID: Set any valid EPSG code if your data uses a local coordinate system.

> **Note**: This only affects geometries whose SRID is 0. If your code does `point.setSRID(4326)`, this property has no effect on that geometry. The library never modifies the geometry objects you pass in; the default is applied only to the encoded value.

#### SRID Management Best Practices

The library uses a two-level priority for SRID resolution:

```
Business code explicit setSRID()  (highest priority)
        ↓ fallback if SRID == 0
Global config: mybatis.geometry.default-srid  (lowest priority)
```

**Recommended approach**: Use `GeometryFactoryProvider.getFactory()` to create geometry objects. The factory is pre-configured with your `default-srid` value (and `coordinate-sequence`), so all geometries created through it automatically carry the correct SRID:

```java
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;

// Factory is globally configured via mybatis.geometry.default-srid
GeometryFactory factory = GeometryFactoryProvider.getFactory();

// This point automatically has SRID = 4326 (or whatever you configured)
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 ✓ — no manual setSRID() needed
```

**When to use explicit `setSRID()`**: Only when a specific geometry needs a different SRID than the global default. `GeometryFactoryProvider.getFactory(srid)` returns a cached factory for any SRID:

```java
GeometryFactory utm = GeometryFactoryProvider.getFactory(32650);  // UTM zone 50N
Point localPoint = utm.createPoint(new Coordinate(500000, 4649776));
```

Geometries read from the database are created with a factory carrying their SRID, so every component of a collection and every derived geometry (`buffer()`, `union()`, `getCentroid()`, …) keeps it.

**Summary**:
- Set `mybatis.geometry.default-srid` once in `application.yml` for your project
- Always use `GeometryFactoryProvider.getFactory()` to create geometry objects
- Use `getFactory(srid)` only for exceptional cases with a different coordinate system
- SRID 0 means "unset": a geometry read from a SRID-0 row is written back with `default-srid` unless that is 0 too

---

#### `write-validation`

Validation applied by the TypeHandlers before a geometry is written:

| Level | Checks | Cost |
|-------|--------|------|
| `FULL` | `BASIC` + OGC validity (`Geometry.isValid()`: self-intersections, ring orientation, …) | O(n log n) per geometry |
| `BASIC` *(default)* | X/Y coordinates are finite (no NaN/Infinity) | O(n) |
| `NONE` | Nothing | – |

`BASIC` is the default because databases store OGC-invalid geometries: with `FULL`, an `updateById` that only changes another column fails for a row whose stored polygon is invalid, and large polygons pay the validation cost on every write. GeoJSON input is still fully validated by default (`geojson.validation: FULL`), so applications that receive geometries through REST already reject invalid shapes at the boundary. Use `FULL` if geometries are built in Java code and must be valid in the database.

#### `preserve-z`

JTS geometries can carry a Z (elevation) ordinate. With `preserve-z: true`, geometries that contain at least one non-NaN Z value are written to PostGIS as 3D EWKB; others are written in 2D. MySQL and MariaDB do not support Z, so they always receive 2D WKB (a warning is logged once when Z values are dropped).

The default is `false` for compatibility: earlier versions always wrote 2D, and writing a 3D geometry into a 2D PostGIS column (`geometry(Point,4326)`) fails. Enable it when you use `PointZ`/`PolygonZ` columns.

Geometries with an M (measure) ordinate are rejected with a clear error on read: JTS 1.19 cannot represent M and would silently turn it into Z.

#### `coordinate-sequence`

| Value | Storage | When to use |
|-------|---------|-------------|
| `ARRAY` *(default)* | One `Coordinate` object per point (JTS default) | Code that mutates `geometry.getCoordinates()` in place |
| `PACKED` | One `double[]` per sequence | Large geometries, high throughput: about 2.7x less heap per 2D point and less GC pressure |

With `PACKED`, `getCoordinates()` returns copies, so mutating them does not change the geometry. Use `getCoordinateSequence().setOrdinate(...)` and `geometryChanged()` instead.

#### `interceptor-enabled`

Controls whether the legacy `GeometryFieldInterceptor` is registered as a MyBatis plugin. It is **not needed any more**: TypeHandlers read geometry columns natively (see [Database Strategy Details](#database-strategy-details)), including in hand-written SQL, joins and XML mappers. Enabling it only makes the database convert geometries to hex text, doubling the bytes transferred for MySQL.

- **false** (default): No SQL rewriting.
- **true**: SELECT statements generated for entities with geometry fields wrap geometry columns (`HEX(col)` for MySQL, `encode(ST_AsEWKB(col), 'hex')` for PostGIS). See [SQL Interceptor](#sql-interceptor-legacy-optional).

#### `database-type`

Overrides automatic database detection. Useful when:

- Several DataSources exist (detection needs a single DataSource bean)
- The database is not reachable at startup
- You want deterministic behavior in tests

If not specified, the library opens one connection to the unique `DataSource` bean and inspects:
1. `DatabaseMetaData.getDatabaseProductName()`: `MySQL`/`MariaDB` → `MYSQL`, `PostgreSQL` → `POSTGRESQL`
2. Otherwise the JDBC URL scheme (`jdbc:mysql:`, `jdbc:mariadb:`, `jdbc:postgresql:`, also behind wrappers such as `jdbc:p6spy:` or `jdbc:tc:`). Host and database names are never matched.
3. Otherwise → `MYSQL` (with a warning log)

#### `geojson.*`

See [GeoJSON](#geojson).

---

## Auto-Configuration

### Activation Conditions

The auto-configuration (`GeometryAutoConfiguration`) activates when **both** conditions are met:

1. `com.baomidou.mybatisplus.core.mapper.BaseMapper` is on the classpath
2. `org.locationtech.jts.geom.Geometry` is on the classpath

If either class is missing, the auto-configuration is silently skipped. Jackson is optional: without `jackson-databind` the application starts normally and only the GeoJSON module is skipped.

### Registered Beans

When active, the following beans are registered (unless already defined by the user):

| Bean | Type | Condition |
|------|------|-----------|
| `geometryHandlerStrategy` | `GeometryHandlerStrategy` | `@ConditionalOnMissingBean` |
| `pointTypeHandler`, `lineStringTypeHandler`, `polygonTypeHandler`, `multiPointTypeHandler`, `multiLineStringTypeHandler`, `multiPolygonTypeHandler`, `geometryCollectionTypeHandler`, `geometryTypeHandler` | one TypeHandler per geometry type | `@ConditionalOnMissingBean` (per type) |
| `geometryJacksonModule` | `GeometryJacksonModule` | Jackson on the classpath, no bean of that name |
| `geometryFieldInterceptor` | `GeometryFieldInterceptor` | `@ConditionalOnMissingBean` + `interceptor-enabled=true` |

MyBatis-Plus registers the TypeHandler beans by Java type, so geometry values are also handled where no `@TableField(typeHandler = …)` applies: wrapper parameters such as `lambdaUpdate().set(Warehouse::getBoundary, polygon)` and result maps of entities without `autoResultMap`.

### Process-Wide Defaults

MyBatis-Plus instantiates the TypeHandlers referenced from `@TableField(typeHandler = …)` by reflection, and Jackson does the same for `@JsonSerialize(using = …)`, so these instances cannot receive Spring beans. The auto-configuration therefore also publishes the settings to process-wide defaults that such instances read on every call:

| Setting | Holder |
|---------|--------|
| Database strategy (any `GeometryHandlerStrategy` bean, including your own) | `GeometryStrategyFactory.setDefaultStrategy` |
| `default-srid`, `coordinate-sequence` | `GeometryFactoryProvider` |
| `write-validation`, `preserve-z` | `GeometryDefaults` |
| `geojson.*` | `GeoJsonOptions.setGlobal` |

Without Spring Boot, call these setters yourself. If several application contexts with different settings share one class loader, the last one started wins.

### Spring Boot Compatibility

| Spring Boot Version | Auto-Configuration Mechanism | MyBatis-Plus starter |
|--------------------|------------------------------|----------------------|
| 2.7.x | `META-INF/spring.factories` | `mybatis-plus-boot-starter` |
| 3.0+ | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `mybatis-plus-spring-boot3-starter` |

Both files are included — the library works with Spring Boot 2.7+ and 3.x without any changes.

---

## Database Strategy Details

### MySQL Strategy

| Aspect | Behavior |
|--------|----------|
| **Write format** | `byte[]`: 4-byte little-endian SRID + 2D WKB (`PreparedStatement.setBytes()`), MySQL's internal geometry format |
| **Read** | `ResultSet.getBytes()` on the raw column (same internal format) |
| **Also accepts** | `HEX(column)` text (legacy interceptor / hand-written SQL) |
| **Compatible DBs** | MySQL 8.0+, MariaDB 10.5+ |

### PostGIS Strategy

| Aspect | Behavior |
|--------|----------|
| **Write format** | Hex EWKB `String` with the SRID embedded (`setObject(value, Types.OTHER)`); 3D when `preserve-z` applies |
| **Read** | `ResultSet.getString()` on the raw column (PostgreSQL returns hex EWKB) |
| **Also accepts** | `encode(ST_AsEWKB(col), 'hex')`, `ST_AsEWKB(col)` / `ST_AsBinary(col)` (bytea), plain WKB hex, and the 1.0.x "SRID prefix + WKB" hex format |
| **Compatible DBs** | PostgreSQL 12+ with PostGIS 3.0+ |

Values read through `ST_AsBinary` carry no SRID, so the geometry gets SRID 0; select the column itself or use `ST_AsEWKB` to keep it.

### Detection Priority

1. If `mybatis.geometry.database-type` is explicitly set → use that (no connection is opened)
2. Else, with exactly one `DataSource` bean → product name, then JDBC URL scheme (see [`database-type`](#database-type))
3. Several or no DataSources, or detection fails → `MYSQL` (with warning logged)

---

## GeoJSON

The `GeometryJacksonModule` (registered automatically when Jackson is present) reads and writes all geometry types as RFC 7946 GeoJSON.

**Input**
- One streaming pass over the JSON tokens; `type` may appear before or after `coordinates`; unknown members (`bbox`, `crs`, …) are ignored.
- Positions must contain numbers: strings, `null` or booleans are rejected (1.0.x silently read them as 0). A third number is kept as Z; further numbers are ignored.
- NaN and infinite coordinates are always rejected; WGS84 ranges are checked when `coordinate-range-validation` applies.
- Polygon rings must be closed and have at least 4 positions; rings of Polygons and MultiPolygons are normalised to RFC 7946 orientation (exterior counter-clockwise, holes clockwise).
- `validation: FULL` additionally requires OGC validity for Polygon, MultiPolygon and polygonal GeometryCollection members.
- Empty `coordinates` / `geometries` arrays produce empty geometries; nested GeometryCollections are supported.
- Errors are `JsonMappingException`s (`GeoJsonParseException`, `InvalidCoordinateException`) carrying the JSON location and property path, so Spring MVC answers HTTP 400.

**Output**
- `type` is written first; rings follow the RFC 7946 right-hand rule; Z is written when present; empty geometries are written as `"coordinates": []`.
- `coordinate-precision` rounds every ordinate half-up and trims trailing zeros, without allocating per number. `@GeoJsonPrecision(n)` on a field or getter overrides it, and `@GeoJsonPrecision(GeoJsonPrecision.FULL)` restores full precision for that field.
- Works with Jackson default typing (for example Redis `GenericJackson2JsonRedisSerializer`).

**Programmatic use without Spring**

```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(6)));
```

**Streaming output**: `GeoJsonStreams` writes JSON arrays, FeatureCollections and GeoJSON Text Sequences (`application/geo+json-seq`, RFC 8142) item by item from any `Iterable`, including a MyBatis `Cursor`. See the README for a controller example.

**HTTP compression**: GeoJSON typically shrinks by 60–75% with gzip. Add `application/geo+json` to `server.compression.mime-types`.

---

## SQL Interceptor (Legacy, Optional)

The interceptor was needed in 1.0.x because TypeHandlers could only parse hex text. TypeHandlers now read raw geometry columns, so the interceptor is disabled by default and only kept for applications that want the old SQL shape.

### How It Works

When enabled, the `GeometryFieldInterceptor` intercepts MyBatis `StatementHandler.prepare()` calls:

1. **Only SELECT** statements are processed, and only their top-level select list (subqueries in `exists`, `inSql`, `apply` and derived tables are left alone; `UNION`/`WITH` statements are not rewritten)
2. The entity is resolved from the statement's result map or the mapper's `BaseMapper<T>` type; its geometry columns come from MyBatis-Plus table metadata (any field whose type handler is a geometry TypeHandler)
3. Geometry columns of the main table are wrapped, keeping their alias; `SELECT *` is expanded to the entity's columns when the statement reads a single table, and `alias.*` of the main table is expanded even in joins
4. Rewritten SQL is cached per statement text (`clearCaches()` on the interceptor instance empties the caches)

When the interceptor is registered in MyBatis XML instead of through Spring Boot, pin the database with the `databaseType` property; otherwise it follows the process-wide default strategy:

```xml
<plugins>
    <plugin interceptor="io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor">
        <property name="databaseType" value="POSTGRESQL"/>
    </plugin>
</plugins>
```

### What Gets Rewritten

```sql
-- Original (MyBatis Plus generates)
SELECT id, name, location, boundary FROM warehouse WHERE id = ?

-- After interceptor (MySQL)
SELECT id, name, HEX(location) AS location, HEX(boundary) AS boundary FROM warehouse WHERE id = ?

-- After interceptor (PostGIS)
SELECT id, name, encode(ST_AsEWKB(location), 'hex') AS location, ... FROM warehouse WHERE id = ?
```

### What Is NOT Rewritten

- INSERT / UPDATE / DELETE statements
- Expressions, function calls and subqueries in the select list
- Columns qualified with another table's alias in joins
- Statements whose entity cannot be determined

None of these need rewriting: the TypeHandlers read the raw columns.

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

Since the auto-configuration uses `@ConditionalOnMissingBean`, your bean takes priority, and it is also published as the process-wide default for reflectively created TypeHandlers. A custom strategy controls reading by overriding `read(ResultSet, String)` / `read(ResultSet, int)` / `read(CallableStatement, int)` (the defaults read `getString()` and call `parseFromDatabase`) and writing through `convertForDatabase(Geometry, int srid)`.

### Override TypeHandler

Similarly, you can provide custom TypeHandlers:

```java
@Bean
public PointTypeHandler pointTypeHandler() {
    // Fixed SRID and strategy
    return new PointTypeHandler(3857, myStrategy);
}
```

The no-arg constructors resolve the strategy and default SRID on every call, so handlers created by MyBatis-Plus always follow the current configuration.

### Custom Column Names

The `@*TableField` annotations are shortcuts for `@TableField(typeHandler = …)` and have no attributes. To also set a column name, use `@TableField` directly; the library recognises it:

```java
@TableField(value = "geo_location", typeHandler = PointTypeHandler.class)
private Point location;
```

Do not combine `@TableField("geo_location")` with `@PointTableField` on the same field: MyBatis-Plus uses the first `@TableField` it finds, so one of the two settings is lost (with the direct annotation first, no TypeHandler is bound).

### Custom Geometry Type

A TypeHandler for another geometry type only needs its type and constructors:

```java
@MappedTypes(LinearRing.class)
public class LinearRingTypeHandler extends AbstractGeometryTypeHandler<LinearRing> {
    public LinearRingTypeHandler() {
        super();
    }
}
```

Reading, type checking, SRID handling and validation are inherited. Override `validateGeometry` for additional checks.

### Hand-Written Queries

Select geometry columns as they are; no wrapping is required:

```xml
<select id="findById" resultMap="warehouseResultMap">
    SELECT w.id, w.name, w.location
    FROM warehouse w JOIN region r ON ST_Contains(r.boundary, w.location)
    WHERE w.id = #{id}
</select>
```

Wrapped columns written for 1.0.x (`HEX(location)`, `encode(ST_AsEWKB(location), 'hex')`) keep working.

---

## Multi-DataSource Setup

In a multi-datasource environment, each datasource may connect to a different database type. Database detection needs a single DataSource, so with several DataSources set `database-type` or define the strategy yourself.

### Approach: Same Database Type Everywhere

```yaml
mybatis:
  geometry:
    database-type: POSTGRESQL
```

### Approach: Different Database Types

Give the TypeHandlers of each `SqlSessionFactory` their own strategy:

```java
@Bean
@Primary
public GeometryHandlerStrategy primaryStrategy() {
    return GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL);
}

@Bean
public SqlSessionFactory reportingSqlSessionFactory(@Qualifier("reportingDataSource") DataSource ds) throws Exception {
    MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
    factory.setDataSource(ds);
    GeometryHandlerStrategy postgis = GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
    factory.setTypeHandlers(new PointTypeHandler(4326, postgis), new PolygonTypeHandler(4326, postgis) /* … */);
    return factory.getObject();
}
```

> **Note**: Handlers created by reflection from `@TableField(typeHandler = …)` use the process-wide default strategy. With databases of different types, make sure the explicitly registered handlers are the ones used (for example by registering them by Java type as above).

---

## FAQ / Troubleshooting

### Q: Geometry field always returns `null` in SELECT

**Cause**: Missing `autoResultMap = true` on the entity class (MyBatis-Plus only applies `@TableField(typeHandler)` to results through the auto result map), or a MyBatis-Plus version older than 3.5.6, which ignores the meta-annotated `@TableField` of the `@*TableField` annotations.

**Fix**:
```java
@TableName(value = "your_table", autoResultMap = true)  // ← required!
public class YourEntity { ... }
```

### Q: `column 'x' contains a MultiPolygon but the mapped type is Polygon`

The column holds a different geometry type than the field. Use the matching field type, or a generic `Geometry` field with `@GeometryTableField`.

### Q: `org.apache.ibatis.type.TypeException: Could not set parameters`

**Cause**: JDBC driver cannot handle the geometry format.

**Check**:
1. Verify `database-type` matches your actual database
2. For PostGIS, ensure the PostGIS extension is enabled: `CREATE EXTENSION IF NOT EXISTS postgis;`
3. For MySQL, ensure the column type is `GEOMETRY` or a geometry subtype

### Q: SRID mismatch error on INSERT (MySQL)

**Cause**: Table column has an `SRID 4326` constraint but the geometry has another SRID (or SRID 0 with a different `default-srid`).

**Fix**: Create geometries with `GeometryFactoryProvider.getFactory()`, or configure the default SRID:
```yaml
mybatis:
  geometry:
    default-srid: 4326
```

### Q: `Column has Z dimension but geometry does not` (PostGIS)

Set `mybatis.geometry.preserve-z: true` when you use Z columns (`PointZ`, `PolygonZ`, …).

### Q: Invalid polygons are no longer rejected on write

Since 1.1 the default `write-validation` is `BASIC`. Set it to `FULL` to restore OGC validation on every write. GeoJSON input is still validated with `FULL` by default.

### Q: `HEX()` function not found (PostgreSQL)

**Cause**: The interceptor is enabled and the library uses the MySQL strategy on PostgreSQL.

**Fix**: Disable the interceptor (it is not needed), or set `mybatis.geometry.database-type: POSTGRESQL`.

### Q: Can I use this without Spring Boot?

Yes. Register the TypeHandlers with MyBatis and set the process-wide defaults yourself:

```java
GeometryHandlerStrategy strategy = GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
GeometryStrategyFactory.setDefaultStrategy(strategy);   // used by handlers created via @TableField
GeometryFactoryProvider.setDefaultSrid(4326);

MybatisConfiguration configuration = new MybatisConfiguration();
configuration.getTypeHandlerRegistry().register(new PointTypeHandler(4326, strategy));
// … other handlers, mappers

ObjectMapper mapper = new ObjectMapper().registerModule(new GeometryJacksonModule());
```

---

## Version Compatibility

| mybatis-plus-geometry | Java | Spring Boot | MyBatis Plus | MySQL | PostgreSQL + PostGIS |
|----------------------|------|-------------|-------------|-------|---------------------|
| 1.1.x | 17+ | 2.7+ / 3.x | 3.5.6+ | 8.0+ (MariaDB 10.5+) | 12+ / 3.0+ |
| 1.0.x | 17+ | 2.7+ / 3.x | 3.5.6+ | 8.0+ | 12+ / 3.0+ |

### Dependency Versions Used

| Dependency | Version | Scope |
|-----------|---------|-------|
| jts-core | 1.19.0 | `api` (transitive) |
| slf4j-api | 2.0.9 | `implementation` |
| mybatis-plus-boot-starter | 3.5.7 | `compileOnly` (user provides) |
| jackson-databind | 2.15.3 | `compileOnly` (optional; 2.11+ required at runtime, 2.14+ for fast double parsing) |
| spring-boot-autoconfigure | 3.2.2 | `compileOnly` (user provides) |

> Jackson serializers are optional. If `jackson-databind` is not on the classpath, GeoJSON support is simply unavailable (no errors).
