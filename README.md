# MyBatis Plus Geometry Extension

English | [简体中文](README_zh.md)

[![Build Status](https://github.com/GeoVerseLabs/mybatis-plus-geometry/actions/workflows/ci.yml/badge.svg)](https://github.com/GeoVerseLabs/mybatis-plus-geometry/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.geoverselabs/mybatis-plus-geometry-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.geoverselabs/mybatis-plus-geometry-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Spring Boot starter that provides seamless integration between MyBatis Plus and JTS (Java Topology Suite) geometry types. Supports MySQL and PostgreSQL/PostGIS with automatic database detection.

## Features

- 🚀 **Zero Configuration** - Auto-configuration for Spring Boot 2.7+ and 3.x
- 🗄️ **Multi-Database Support** - MySQL and PostgreSQL/PostGIS with auto-detection
- 📍 **Geometry Types** - Point, LineString, Polygon, MultiPoint, MultiLineString, MultiPolygon, GeometryCollection, and generic Geometry support
- 🔄 **GeoJSON Serialization** - Streaming Jackson serializers/deserializers for REST APIs (RFC 7946)
- ⚡ **Native Column Reads** - Geometry columns are read directly, no SQL rewriting; MySQL/MariaDB transfer raw binary WKB
- 📦 **Transfer Efficiency** - Configurable GeoJSON coordinate precision, streaming FeatureCollection / GeoJSON Text Sequence output, optional packed coordinate storage
- 🎯 **Type-Safe Annotations** - `@PointTableField`, `@LineStringTableField`, `@PolygonTableField`, `@MultiPointTableField`, `@MultiLineStringTableField`, `@MultiPolygonTableField`, `@GeometryCollectionTableField`, `@GeometryTableField`

## Requirements

- Java 17+
- Spring Boot 2.7+ or 3.x
- MyBatis Plus 3.5.6+ (the geometry annotations rely on meta-annotated `@TableField`, supported since 3.5.6). On Spring Boot 3 use `mybatis-plus-spring-boot3-starter`; on Spring Boot 2.7 use `mybatis-plus-boot-starter`.
- MySQL 8.0+ or PostgreSQL 12+ with PostGIS

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.geoverselabs</groupId>
    <artifactId>mybatis-plus-geometry-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.geoverselabs:mybatis-plus-geometry-spring-boot-starter:1.0.1'
```

## Quick Start

### 1. Define Entity with Geometry Fields

```java
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PolygonTableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

@TableName(value = "warehouse", autoResultMap = true)
public class Warehouse {
    
    private Long id;
    private String name;
    
    @PointTableField
    private Point location;
    
    @PolygonTableField
    private Polygon boundary;
    
    // getters and setters
}
```

### 2. Create Mapper

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}
```

### 3. Use in Service

```java
@Service
public class WarehouseService {
    
    @Autowired
    private WarehouseMapper warehouseMapper;
    
    public void createWarehouse() {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        
        Warehouse warehouse = new Warehouse();
        warehouse.setName("Main Warehouse");
        warehouse.setLocation(factory.createPoint(new Coordinate(121.5, 31.2)));
        
        warehouseMapper.insert(warehouse);
    }
}
```

## GeoJSON Support

GeoJSON serialization is automatically enabled when Jackson is on the classpath. The `GeometryJacksonModule` is registered via Spring Boot auto-configuration — no manual setup required.

### Automatic Serialization (Recommended)

With `GeometryJacksonModule` auto-registered, any geometry field (`Point`, `LineString`, `Polygon`, `MultiPoint`, `MultiLineString`, `MultiPolygon`, `GeometryCollection`, or generic `Geometry`) in your DTOs or entities will be serialized/deserialized as GeoJSON automatically:

```java
public class WarehouseDTO {
    
    private Long id;
    private String name;
    private Point location;  // Automatically serialized as GeoJSON
    
    // getters and setters
}
```

### Explicit Annotation (Optional)

If you prefer explicit control, or if auto-configuration is disabled, you can use annotations:

```java
import io.github.geoverselabs.mybatis.geometry.jackson.PointSerializer;
import io.github.geoverselabs.mybatis.geometry.jackson.PointDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class WarehouseDTO {
    
    private Long id;
    private String name;
    
    @JsonSerialize(using = PointSerializer.class)
    @JsonDeserialize(using = PointDeserializer.class)
    private Point location;
    
    // getters and setters
}
```

### GeoJSON Format Examples

**Point:**
```json
{
  "type": "Point",
  "coordinates": [121.5, 31.2]
}
```

**Polygon:**
```json
{
  "type": "Polygon",
  "coordinates": [
    [[121.0, 31.0], [122.0, 31.0], [122.0, 32.0], [121.0, 32.0], [121.0, 31.0]]
  ]
}
```

**LineString:**
```json
{
  "type": "LineString",
  "coordinates": [[121.0, 31.0], [121.5, 31.5], [122.0, 32.0]]
}
```

**MultiPoint:**
```json
{
  "type": "MultiPoint",
  "coordinates": [[121.5, 31.2], [120.1, 30.3], [119.8, 29.9]]
}
```

**MultiLineString:**
```json
{
  "type": "MultiLineString",
  "coordinates": [
    [[121.0, 31.0], [121.5, 31.5]],
    [[122.0, 32.0], [122.5, 32.5]]
  ]
}
```

**MultiPolygon:**
```json
{
  "type": "MultiPolygon",
  "coordinates": [
    [[[121.0, 31.0], [122.0, 31.0], [122.0, 32.0], [121.0, 32.0], [121.0, 31.0]]],
    [[[119.0, 30.0], [120.0, 30.0], [120.0, 31.0], [119.0, 31.0], [119.0, 30.0]]]
  ]
}
```

**GeometryCollection:**
```json
{
  "type": "GeometryCollection",
  "geometries": [
    { "type": "Point", "coordinates": [121.5, 31.2] },
    { "type": "LineString", "coordinates": [[121.0, 31.0], [122.0, 32.0]] }
  ]
}
```

## Configuration

Configure in `application.yml`:

```yaml
mybatis:
  geometry:
    # Default SRID for geometries without one (default: 4326 for WGS84)
    default-srid: 4326

    # Database type (auto-detected from the DataSource if not specified)
    # Supported values: MYSQL, POSTGRESQL
    database-type: MYSQL

    # Validation before database writes: FULL (OGC isValid), BASIC (finite coordinates), NONE
    write-validation: BASIC

    # Write Z values to PostGIS (MySQL/MariaDB are 2D only)
    preserve-z: false

    # Coordinate storage of geometries created by the library: ARRAY or PACKED (double[], less heap)
    coordinate-sequence: ARRAY

    # Legacy SELECT rewriting (HEX()/encode(ST_AsEWKB())); not needed any more (default: false)
    interceptor-enabled: false

    geojson:
      # Decimal places written per ordinate; unset = full double precision. RFC 7946 recommends 6
      coordinate-precision: 6
      # Validation of GeoJSON input: FULL (also OGC isValid for polygons) or BASIC
      validation: FULL
      # WGS84 range checks on input; unset = enabled only when default-srid is 4326
      coordinate-range-validation: true
```

> **Note:** When `default-srid` is 4326 (WGS84), GeoJSON deserializers validate coordinate ranges (longitude -180~180, latitude -90~90) unless `geojson.coordinate-range-validation` says otherwise. NaN and infinite coordinates are always rejected.

See the [Configuration Reference](docs/CONFIGURATION.md) for every property.

## Transfer Efficiency

Measured on a 5,000-point polygon (JDK 21, single thread), compared with 1.0.1:

| Optimization | How to enable | Effect |
|---|---|---|
| Native binary column reads | default (no SQL interceptor) | MySQL/MariaDB send raw WKB instead of HEX() text: half the bytes, decoding 2x faster (437 → 208 µs) |
| Streaming GeoJSON parser | default | 42% less CPU and 67% less allocation per request body; 86% less CPU with `geojson.validation: BASIC` |
| GeoJSON coordinate precision | `mybatis.geometry.geojson.coordinate-precision: 6` | 42% smaller JSON (57% smaller after gzip), 45% less CPU and 85% less allocation when writing |
| Read-only endpoint (DB → GeoJSON) | native reads + precision 6 | 44% less CPU, 76% less allocation |
| HTTP compression | `server.compression.*` (below) | GeoJSON shrinks 60–75% |
| Packed coordinates | `mybatis.geometry.coordinate-sequence: PACKED` | 2.7x less heap per decoded geometry (215 → 79 KB) |
| Streaming large results | `GeoJsonStreams` + MyBatis `Cursor` | constant memory instead of one object per row |

### HTTP compression

GeoJSON compresses well because coordinates repeat their leading digits. Enable compression for the GeoJSON media types in Spring Boot:

```yaml
server:
  compression:
    enabled: true
    mime-types: application/json,application/geo+json,application/geo+json-seq
    min-response-size: 2KB
```

### Per-field precision

```java
public class WarehouseDTO {
    @GeoJsonPrecision(7)                     // about 1 cm
    private Point location;

    @GeoJsonPrecision(GeoJsonPrecision.FULL) // ignore the global setting
    private Polygon boundary;
}
```

### Streaming large result sets

`GeoJsonStreams` writes items one by one, so a MyBatis `Cursor` never has to be loaded into memory. Keep the cursor's session open while writing (for example inside a `@Transactional` method):

```java
public interface WarehouseMapper extends BaseMapper<Warehouse> {
    @Select("SELECT * FROM warehouse")
    @Options(fetchSize = Integer.MIN_VALUE)          // MySQL: stream rows instead of buffering
    @ResultMap("mybatis-plus_Warehouse")             // reuse the autoResultMap (geometry TypeHandlers)
    Cursor<Warehouse> streamAll();
}
```

```java
@GetMapping(value = "/warehouses", produces = GeoJsonMediaTypes.GEO_JSON)
@Transactional(readOnly = true)
public void export(HttpServletResponse response) throws IOException {
    response.setContentType(GeoJsonMediaTypes.GEO_JSON);
    try (Cursor<Warehouse> cursor = warehouseMapper.streamAll()) {
        GeoJsonStreams.writeFeatureCollection(objectMapper, cursor,
            Warehouse::getBoundary,                          // geometry
            w -> Map.of("name", w.getName()),                // properties
            response.getOutputStream());
    }
}
```

`GeoJsonStreams.writeFeatureSequence(...)` produces a GeoJSON Text Sequence (`application/geo+json-seq`, RFC 8142) that clients can parse record by record. For MySQL, declare the cursor query with `@Options(fetchSize = Integer.MIN_VALUE)` so the driver streams rows; for PostgreSQL use a positive `fetchSize` inside a transaction.

## Database Support

| Database | Version | Status |
|----------|---------|--------|
| MySQL | 8.0+ | ✅ Full Support |
| MariaDB | 10.5+ | ✅ Full Support |
| PostgreSQL + PostGIS | 12+ / 3.0+ | ✅ Full Support |

### MySQL Table Example

```sql
CREATE TABLE warehouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT SRID 4326,
    boundary POLYGON SRID 4326,
    created_time DATETIME
);
```

### MariaDB Table Example

```sql
-- MariaDB does not support inline SRID constraint; SRID is enforced by the application layer
CREATE TABLE warehouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT NOT NULL,
    boundary POLYGON NOT NULL,
    created_time DATETIME
);
CREATE SPATIAL INDEX idx_warehouse_location ON warehouse(location);
```

### PostgreSQL + PostGIS Table Example

```sql
CREATE TABLE warehouse (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    location GEOMETRY(POINT, 4326),
    boundary GEOMETRY(POLYGON, 4326),
    created_time TIMESTAMP
);
```

## How It Works

### Insert/Update Flow

```
Java geometry object
    ↓ (TypeHandler.setNonNullParameter: validate, never mutates the object)
MySQL/MariaDB: SRID + WKB bytes (setBytes)   |   PostGIS: hex EWKB (setObject)
    ↓
Database GEOMETRY column
```

### Select Flow

```
Database GEOMETRY column (selected as-is, no SQL rewriting)
    ↓ MySQL/MariaDB: getBytes() → SRID + WKB   |   PostGIS: getBytes() → hex EWKB
    ↓ (TypeHandler.getNullableResult → strategy.read)
Java geometry object (SRID kept on every component)
```

The same TypeHandlers also accept the values produced by the legacy interceptor (`HEX(col)`, `encode(ST_AsEWKB(col), 'hex')`), so hand-written SQL that wraps columns keeps working.

## API Reference

### Annotations

| Annotation | Description |
|------------|-------------|
| `@PointTableField` | Marks a field as JTS Point type |
| `@LineStringTableField` | Marks a field as JTS LineString type |
| `@PolygonTableField` | Marks a field as JTS Polygon type |
| `@MultiPointTableField` | Marks a field as JTS MultiPoint type |
| `@MultiLineStringTableField` | Marks a field as JTS MultiLineString type |
| `@MultiPolygonTableField` | Marks a field as JTS MultiPolygon type |
| `@GeometryCollectionTableField` | Marks a field as JTS GeometryCollection type |
| `@GeometryTableField` | Marks a field as generic JTS Geometry (any subtype) |

### Jackson Serializers

| Class | Description |
|-------|-------------|
| `GeometryJacksonModule` | Auto-registered Jackson Module (zero-config) |
| `PointSerializer` / `PointDeserializer` | GeoJSON Point serialization |
| `LineStringSerializer` / `LineStringDeserializer` | GeoJSON LineString serialization |
| `PolygonSerializer` / `PolygonDeserializer` | GeoJSON Polygon serialization |
| `MultiPointSerializer` / `MultiPointDeserializer` | GeoJSON MultiPoint serialization |
| `MultiLineStringSerializer` / `MultiLineStringDeserializer` | GeoJSON MultiLineString serialization |
| `MultiPolygonSerializer` / `MultiPolygonDeserializer` | GeoJSON MultiPolygon serialization |
| `GeometryCollectionSerializer` / `GeometryCollectionDeserializer` | GeoJSON GeometryCollection serialization |
| `GenericGeometrySerializer` / `GenericGeometryDeserializer` | GeoJSON serialization for any geometry type |
| `GeoJsonOptions` | Precision, validation and parsing options for the serializers |
| `@GeoJsonPrecision` | Per-field coordinate precision |

### Streaming

| Class | Description |
|-------|-------------|
| `GeoJsonStreams` | Stream JSON arrays, FeatureCollections and GeoJSON Text Sequences from any `Iterable` (including MyBatis `Cursor`) |
| `GeoJsonMediaTypes` | `application/geo+json` and `application/geo+json-seq` constants |

### Utility Classes

| Class | Description |
|-------|-------------|
| `WkbUtil` | WKB format conversion utilities |
| `GeometryFactoryProvider` | Thread-safe, per-SRID cached GeometryFactory provider |
| `GeometryDefaults` | Process-wide write validation and Z handling defaults |

## Sponsor

If this project helps you, consider supporting its continued development!

👉 [All payment options](.github/SPONSOR.md)

| PayPal | WeChat |
|--------|--------|
| <img src=".github/sponsor/paypal-qr.png" width="150" /> | <img src=".github/sponsor/wechat-pay.png" width="150" /> |

## Contributing

Contributions are welcome! Please read our [Contributing Guide](CONTRIBUTING.md) for details.

### Setting Up Development Environment

1. Fork and clone the repository:
```bash
git clone https://github.com/YOUR_USERNAME/mybatis-plus-geometry.git
cd mybatis-plus-geometry
```

2. Initialize Git configuration (optional):
```bash
# On Linux/Mac
./init-git.sh

# On Windows
init-git.bat
```

3. Build the project:
```bash
./gradlew build
```

4. Run tests:
```bash
./gradlew test
```

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [JTS Topology Suite](https://github.com/locationtech/jts) - Java geometry library
- [MyBatis Plus](https://github.com/baomidou/mybatis-plus) - MyBatis enhancement framework
- [Spring Boot](https://spring.io/projects/spring-boot) - Application framework

## Related Documentation

- [Quick Start Guide](.github/QUICK_START.md)
- [Configuration Reference](docs/CONFIGURATION.md)
- [Git Setup Guide](docs/GIT_SETUP.md)
- [Security Policy](SECURITY.md)
- [Contributing Guide](CONTRIBUTING.md)
