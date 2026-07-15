# MyBatis Plus Geometry 扩展

[![构建状态](https://github.com/GeoVerseLabs/mybatis-plus-geometry/actions/workflows/ci.yml/badge.svg)](https://github.com/GeoVerseLabs/mybatis-plus-geometry/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.geoverselabs/mybatis-plus-geometry-spring-boot-starter.svg)](https://search.maven.org/artifact/io.github.geoverselabs/mybatis-plus-geometry-spring-boot-starter)
[![许可证](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

[English](README.md) | 简体中文

一个 Spring Boot starter，提供 MyBatis Plus 与 JTS（Java Topology Suite）几何类型的无缝集成。支持 MySQL 和 PostgreSQL/PostGIS，具有自动数据库检测功能。

## 特性

- 🚀 **零配置** - Spring Boot 2.7+ 和 3.x 自动配置
- 🗄️ **多数据库支持** - MySQL 和 PostgreSQL/PostGIS，自动检测
- 📍 **几何类型** - 支持 Point、LineString、Polygon、MultiPoint、MultiLineString、MultiPolygon、GeometryCollection 及通用 Geometry
- 🔄 **GeoJSON 序列化** - 为 REST API 提供 Jackson 序列化器/反序列化器
- ⚡ **SQL 拦截器** - SELECT 查询自动添加 HEX() 包装
- 🎯 **类型安全注解** - `@PointTableField`、`@LineStringTableField`、`@PolygonTableField`、`@MultiPointTableField`、`@MultiLineStringTableField`、`@MultiPolygonTableField`、`@GeometryCollectionTableField`、`@GeometryTableField`

## 系统要求

- Java 17+
- Spring Boot 2.7+ 或 3.x
- MyBatis Plus 3.5+
- MySQL 8.0+ 或 PostgreSQL 12+ with PostGIS

## 安装

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

## 快速开始

### 1. 定义包含几何字段的实体

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

### 2. 创建 Mapper

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WarehouseMapper extends BaseMapper<Warehouse> {
}
```

### 3. 在 Service 中使用

```java
@Service
public class WarehouseService {
    
    @Autowired
    private WarehouseMapper warehouseMapper;
    
    public void createWarehouse() {
        GeometryFactory factory = new GeometryFactory(new PrecisionModel(), 4326);
        
        Warehouse warehouse = new Warehouse();
        warehouse.setName("主仓库");
        warehouse.setLocation(factory.createPoint(new Coordinate(121.5, 31.2)));
        
        warehouseMapper.insert(warehouse);
    }
}
```

## GeoJSON 支持

当 Jackson 存在于 classpath 时，GeoJSON 序列化自动启用。`GeometryJacksonModule` 通过 Spring Boot 自动配置注册，无需手动配置。

### 自动序列化（推荐）

`GeometryJacksonModule` 自动注册后，DTO 或实体中的 `Point`、`LineString`、`Polygon`、`MultiPoint`、`MultiLineString`、`MultiPolygon`、`GeometryCollection` 及通用 `Geometry` 字段会自动进行 GeoJSON 格式的序列化/反序列化：

```java
public class WarehouseDTO {
    
    private Long id;
    private String name;
    private Point location;  // 自动序列化为 GeoJSON
    
    // getters and setters
}
```

### 显式注解（可选）

如果需要显式控制，或者自动配置被禁用，可以使用注解：

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

### GeoJSON 格式示例

**Point（点）：**
```json
{
  "type": "Point",
  "coordinates": [121.5, 31.2]
}
```

**Polygon（多边形）：**
```json
{
  "type": "Polygon",
  "coordinates": [
    [[121.0, 31.0], [122.0, 31.0], [122.0, 32.0], [121.0, 32.0], [121.0, 31.0]]
  ]
}
```

**LineString（线串）：**
```json
{
  "type": "LineString",
  "coordinates": [[121.0, 31.0], [121.5, 31.5], [122.0, 32.0]]
}
```

**MultiPoint（多点）：**
```json
{
  "type": "MultiPoint",
  "coordinates": [[121.5, 31.2], [120.1, 30.3], [119.8, 29.9]]
}
```

**MultiLineString（多线串）：**
```json
{
  "type": "MultiLineString",
  "coordinates": [
    [[121.0, 31.0], [121.5, 31.5]],
    [[122.0, 32.0], [122.5, 32.5]]
  ]
}
```

**MultiPolygon（多多边形）：**
```json
{
  "type": "MultiPolygon",
  "coordinates": [
    [[[121.0, 31.0], [122.0, 31.0], [122.0, 32.0], [121.0, 32.0], [121.0, 31.0]]],
    [[[119.0, 30.0], [120.0, 30.0], [120.0, 31.0], [119.0, 31.0], [119.0, 30.0]]]
  ]
}
```

**GeometryCollection（几何集合）：**
```json
{
  "type": "GeometryCollection",
  "geometries": [
    { "type": "Point", "coordinates": [121.5, 31.2] },
    { "type": "LineString", "coordinates": [[121.0, 31.0], [122.0, 32.0]] }
  ]
}
```

## 配置

在 `application.yml` 中配置：

```yaml
mybatis:
  geometry:
    # 默认 SRID（默认值：4326，WGS84 坐标系）
    default-srid: 4326
    
    # 启用 SQL 拦截器自动添加 HEX() 包装（默认值：true）
    interceptor-enabled: true
    
    # 数据库类型（如果不指定则自动检测）
    # 支持的值：MYSQL、POSTGRESQL
    database-type: MYSQL
```

> **说明：** 当 `default-srid` 为 4326（WGS84）时，GeoJSON 反序列化器自动校验坐标范围（经度 -180~180，纬度 -90~90）。当使用其他 SRID（如 3857）时，范围校验自动禁用，仅检查坐标是否为有限数值。

## 数据库支持

| 数据库 | 版本 | 状态 |
|--------|------|------|
| MySQL | 8.0+ | ✅ 完全支持 |
| MariaDB | 10.5+ | ✅ 完全支持 |
| PostgreSQL + PostGIS | 12+ / 3.0+ | ✅ 完全支持 |

### MySQL 表示例

```sql
CREATE TABLE warehouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT SRID 4326,
    boundary POLYGON SRID 4326,
    created_time DATETIME
);
```

### MariaDB 表示例

```sql
-- MariaDB 不支持内联 SRID 约束，SRID 由应用层保证
CREATE TABLE warehouse (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT NOT NULL,
    boundary POLYGON NOT NULL,
    created_time DATETIME
);
CREATE SPATIAL INDEX idx_warehouse_location ON warehouse(location);
```

### PostgreSQL + PostGIS 表示例

```sql
CREATE TABLE warehouse (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    location GEOMETRY(POINT, 4326),
    boundary GEOMETRY(POLYGON, 4326),
    created_time TIMESTAMP
);
```

## 工作原理

### 插入/更新流程

```
Java Point/Polygon 对象
    ↓ (TypeHandler.setNonNullParameter)
WKB 字节数组
    ↓ (JDBC setBytes)
数据库 GEOMETRY 列
```

### 查询流程

```
数据库 GEOMETRY 列
    ↓ (SQL 拦截器添加 HEX())
WKB 十六进制字符串
    ↓ (TypeHandler.getNullableResult)
Java Point/Polygon 对象
```

## API 参考

### 注解

| 注解 | 说明 |
|------|------|
| `@PointTableField` | 标记字段为 JTS Point 类型 |
| `@LineStringTableField` | 标记字段为 JTS LineString 类型 |
| `@PolygonTableField` | 标记字段为 JTS Polygon 类型 |
| `@MultiPointTableField` | 标记字段为 JTS MultiPoint 类型 |
| `@MultiLineStringTableField` | 标记字段为 JTS MultiLineString 类型 |
| `@MultiPolygonTableField` | 标记字段为 JTS MultiPolygon 类型 |
| `@GeometryCollectionTableField` | 标记字段为 JTS GeometryCollection 类型 |
| `@GeometryTableField` | 标记字段为通用 JTS Geometry 类型（任意子类型） |

### Jackson 序列化器

| 类 | 说明 |
|----|------|
| `GeometryJacksonModule` | 自动注册的 Jackson Module（零配置） |
| `PointSerializer` / `PointDeserializer` | GeoJSON Point 序列化 |
| `LineStringSerializer` / `LineStringDeserializer` | GeoJSON LineString 序列化 |
| `PolygonSerializer` / `PolygonDeserializer` | GeoJSON Polygon 序列化 |
| `MultiPointSerializer` / `MultiPointDeserializer` | GeoJSON MultiPoint 序列化 |
| `MultiLineStringSerializer` / `MultiLineStringDeserializer` | GeoJSON MultiLineString 序列化 |
| `MultiPolygonSerializer` / `MultiPolygonDeserializer` | GeoJSON MultiPolygon 序列化 |
| `GeometryCollectionSerializer` / `GeometryCollectionDeserializer` | GeoJSON GeometryCollection 序列化 |
| `GenericGeometrySerializer` / `GenericGeometryDeserializer` | 通用几何类型 GeoJSON 序列化 |

### 工具类

| 类 | 说明 |
|----|------|
| `WkbUtil` | WKB 格式转换工具 |
| `GeometryFactoryProvider` | 线程安全的 GeometryFactory 提供者 |

## 赞助支持

如果这个项目对你有帮助，欢迎赞助支持持续开发！

👉 [查看所有付款方式](.github/SPONSOR.md)

| PayPal | 微信 |
|--------|------|
| <img src=".github/sponsor/paypal-qr.png" width="150" /> | <img src=".github/sponsor/wechat-pay.png" width="150" /> |

## 贡献

欢迎贡献！请阅读我们的[贡献指南](CONTRIBUTING.md)了解详情。

### 设置开发环境

1. Fork 并克隆仓库：
```bash
git clone https://github.com/YOUR_USERNAME/mybatis-plus-geometry.git
cd mybatis-plus-geometry
```

2. 初始化 Git 配置（可选）：
```bash
# Linux/Mac
./init-git.sh

# Windows
init-git.bat
```

3. 构建项目：
```bash
./gradlew build
```

4. 运行测试：
```bash
./gradlew test
```

## 许可证

本项目采用 Apache License 2.0 许可证 - 详见 [LICENSE](LICENSE) 文件。

## 致谢

- [JTS Topology Suite](https://github.com/locationtech/jts) - Java 几何库
- [MyBatis Plus](https://github.com/baomidou/mybatis-plus) - MyBatis 增强框架
- [Spring Boot](https://spring.io/projects/spring-boot) - 应用框架

## 相关文档

- [快速开始指南](.github/QUICK_START_zh.md)
- [配置参考](docs/CONFIGURATION_zh.md)
- [Git 设置指南](docs/GIT_SETUP_zh.md)
- [安全策略](SECURITY_zh.md)
- [贡献指南](CONTRIBUTING_zh.md)
