# MyBatis Plus Geometry Extension

English | [简体中文](README_zh.md)

[![Build Status](https://github.com/yoy0o/mybatis-plus-geometry/actions/workflows/ci.yml/badge.svg)](https://github.com/yoy0o/mybatis-plus-geometry/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.yoy0o/mybatis-plus-geometry-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.yoy0o/mybatis-plus-geometry-spring-boot-starter)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Spring Boot starter that provides seamless integration between MyBatis Plus and JTS (Java Topology Suite) geometry types. Supports MySQL and PostgreSQL/PostGIS with automatic database detection.

## Features

- 🚀 **Zero Configuration** - Auto-configuration for Spring Boot 2.7+ and 3.x
- 🗄️ **Multi-Database Support** - MySQL and PostgreSQL/PostGIS with auto-detection
- 📍 **Geometry Types** - Point, Polygon, LineString support
- 🔄 **GeoJSON Serialization** - Jackson serializers/deserializers for REST APIs
- ⚡ **SQL Interceptor** - Automatic HEX() wrapping for SELECT queries
- 🎯 **Type-Safe Annotations** - `@PointTableField`, `@PolygonTableField`, `@LineStringTableField`

## Requirements

- Java 17+
- Spring Boot 2.7+ or 3.x
- MyBatis Plus 3.5+
- MySQL 8.0+ or PostgreSQL 12+ with PostGIS

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.yoy0o</groupId>
    <artifactId>mybatis-plus-geometry-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.yoy0o:mybatis-plus-geometry-spring-boot-starter:1.0.0'
```

## Quick Start

### 1. Define Entity with Geometry Fields

```java
import io.github.yoy0o.mybatis.geometry.annotation.PointTableField;
import io.github.yoy0o.mybatis.geometry.annotation.PolygonTableField;
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

For REST APIs, use the provided Jackson serializers:

### DTO Definition

```java
import io.github.yoy0o.mybatis.geometry.jackson.PointSerializer;
import io.github.yoy0o.mybatis.geometry.jackson.PointDeserializer;
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

## Configuration

Configure in `application.yml`:

```yaml
mybatis:
  geometry:
    # Default SRID (default: 4326 for WGS84)
    default-srid: 4326
    
    # Enable SQL interceptor for automatic HEX() wrapping (default: true)
    interceptor-enabled: true
    
    # Database type (auto-detected if not specified)
    # Supported values: MYSQL, POSTGRESQL
    database-type: MYSQL
```

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
Java Point/Polygon object
    ↓ (TypeHandler.setNonNullParameter)
WKB byte array
    ↓ (JDBC setBytes)
Database GEOMETRY column
```

### Select Flow

```
Database GEOMETRY column
    ↓ (SQL Interceptor adds HEX())
WKB hex string
    ↓ (TypeHandler.getNullableResult)
Java Point/Polygon object
```

## API Reference

### Annotations

| Annotation | Description |
|------------|-------------|
| `@PointTableField` | Marks a field as JTS Point type |
| `@PolygonTableField` | Marks a field as JTS Polygon type |
| `@LineStringTableField` | Marks a field as JTS LineString type |

### Jackson Serializers

| Class | Description |
|-------|-------------|
| `PointSerializer` / `PointDeserializer` | GeoJSON Point serialization |
| `PolygonSerializer` / `PolygonDeserializer` | GeoJSON Polygon serialization |
| `LineStringSerializer` / `LineStringDeserializer` | GeoJSON LineString serialization |

### Utility Classes

| Class | Description |
|-------|-------------|
| `WkbUtil` | WKB format conversion utilities |
| `GeometryFactoryProvider` | Thread-safe GeometryFactory provider |

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
- [Git Setup Guide](docs/GIT_SETUP.md)
- [Security Policy](SECURITY.md)
- [Contributing Guide](CONTRIBUTING.md)

## Sponsor / 赞助

If this project helps you, consider supporting its continued development!

如果这个项目对你有帮助，欢迎赞助支持！

👉 [View all payment options / 查看所有付款方式](.github/SPONSOR.md)

| PayPal | 微信 |
|--------|------|
| <img src=".github/sponsor/paypal-qr.png" width="150" /> | <img src=".github/sponsor/wechat-pay.png" width="150" /> |
