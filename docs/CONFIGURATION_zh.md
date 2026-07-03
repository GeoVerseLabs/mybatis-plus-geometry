# 配置参考

[English](CONFIGURATION.md) | 简体中文

本文档提供 mybatis-plus-geometry 所有配置选项、自动配置行为、高级用法和常见问题的完整参考。

---

## 目录

- [配置属性](#配置属性)
- [自动配置](#自动配置)
- [数据库策略详解](#数据库策略详解)
- [SQL 拦截器](#sql-拦截器)
- [自定义扩展](#自定义扩展)
- [多数据源配置](#多数据源配置)
- [常见问题](#常见问题)
- [版本兼容性](#版本兼容性)

---

## 配置属性

所有属性的前缀为 `mybatis.geometry`，在 `application.yml` 或 `application.properties` 中配置：

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `default-srid` | `int` | `4326` | 几何对象的默认 SRID。4326 = WGS84（GPS 坐标） |
| `interceptor-enabled` | `boolean` | `true` | 是否启用 SQL 拦截器自动包装几何列 |
| `database-type` | `enum` | *(自动检测)* | 强制指定数据库类型。可选值：`MYSQL`、`POSTGRESQL` |

### YAML 完整示例

```yaml
mybatis:
  geometry:
    default-srid: 4326
    interceptor-enabled: true
    database-type: MYSQL
```

### Properties 示例

```properties
mybatis.geometry.default-srid=4326
mybatis.geometry.interceptor-enabled=true
mybatis.geometry.database-type=MYSQL
```

### 属性详细说明

#### `default-srid`

应用于未设置 SRID（SRID = 0）的几何对象的空间参考标识符。

- **4326**（WGS84）：标准 GPS 经纬度坐标，最常用
- **3857**（Web Mercator）：Web 地图使用（如 Google Maps、OpenStreetMap）
- 自定义 SRID：设置任何有效的 EPSG 代码

> **注意**：此属性仅影响未显式设置 SRID 的几何对象。如果代码中已执行 `point.setSRID(4326)`，此属性对该对象无效。

#### SRID 管理最佳实践

库使用两层优先级来确定 SRID：

```
业务代码显式 setSRID()（最高优先级）
        ↓ 当 SRID == 0 时回退
全局配置：mybatis.geometry.default-srid（最低优先级）
```

**推荐做法**：使用 `GeometryFactoryProvider.getFactory()` 创建几何对象。该工厂已根据 `default-srid` 配置预设了 SRID，通过它创建的所有几何对象自动携带正确的 SRID：

```java
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;

// 工厂通过 mybatis.geometry.default-srid 全局配置
GeometryFactory factory = GeometryFactoryProvider.getFactory();

// 此 point 自动具有 SRID = 4326（或你配置的值）
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 ✓ — 无需手动 setSRID()
```

**何时使用显式 `setSRID()`**：仅当某个几何对象需要与全局默认不同的 SRID 时：

```java
// 全局默认 4326，但此几何对象使用本地坐标系
Point localPoint = factory.createPoint(new Coordinate(500000, 4649776));
localPoint.setSRID(32650);  // UTM 50N 区
```

**应避免的反模式**：直接使用 `new GeometryFactory()` 创建几何对象 — 会产生 SRID=0 的对象，依赖运行时回退：

```java
// ❌ 不推荐：SRID=0，依赖写入时的 ensureSrid() 回退
GeometryFactory rawFactory = new GeometryFactory();
Point point = rawFactory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 0 — 能工作但脆弱

// ✅ 推荐：创建时即设定 SRID，显式且可追踪
GeometryFactory factory = GeometryFactoryProvider.getFactory();
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 — 有保证
```

**总结**：
- 在 `application.yml` 中设置一次 `mybatis.geometry.default-srid`
- 始终使用 `GeometryFactoryProvider.getFactory()` 创建几何对象
- 仅在需要不同坐标系的特殊场景使用 `setSRID()`
- TypeHandler 的 `ensureSrid()` 是安全兜底，不是 SRID 的主要来源

---

#### `interceptor-enabled`

控制是否注册 `GeometryFieldInterceptor` 为 MyBatis 插件。

- **true**（默认）：拦截器自动改写 SELECT SQL，用数据库函数包装几何列（MySQL 使用 `HEX()`，PostGIS 使用 `encode(ST_AsBinary(...))`）
- **false**：不改写 SQL。需要在 mapper XML 或注解查询中手动包装几何列

禁用后，SELECT 几何列会返回原始二进制数据，TypeHandler 无法解析。需要手动处理：

```sql
-- MySQL：手动包装
SELECT id, name, HEX(location) AS location FROM warehouse

-- PostGIS：手动包装
SELECT id, name, encode(ST_AsBinary(location), 'hex') AS location FROM warehouse
```

#### `database-type`

覆盖自动数据库检测。适用场景：

- 连接池 URL 无法在启动时读取
- 使用非标准 JDBC URL 格式
- 测试环境需要确定性行为

不指定时，库会检查 `DataSource` 的 JDBC URL：
- URL 包含 `mysql` 或 `mariadb` → `MYSQL`
- URL 包含 `postgresql` 或 `postgres` → `POSTGRESQL`
- 都不匹配 → 默认 `MYSQL`（打印警告日志）

---

## 自动配置

### 激活条件

`GeometryAutoConfiguration` 在以下条件**同时满足**时激活：

1. classpath 存在 `com.baomidou.mybatisplus.core.mapper.BaseMapper`
2. classpath 存在 `org.locationtech.jts.geom.Geometry`

如果任一类缺失，自动配置静默跳过。

### 注册的 Bean

激活后，以下 Bean 会自动注册（除非用户已自定义同类型 Bean）：

| Bean | 类型 | 注册条件 |
|------|------|----------|
| `geometryHandlerStrategy` | `GeometryHandlerStrategy` | `@ConditionalOnMissingBean` |
| `pointTypeHandler` | `PointTypeHandler` | `@ConditionalOnMissingBean` |
| `polygonTypeHandler` | `PolygonTypeHandler` | `@ConditionalOnMissingBean` |
| `lineStringTypeHandler` | `LineStringTypeHandler` | `@ConditionalOnMissingBean` |
| `geometryFieldInterceptor` | `GeometryFieldInterceptor` | `@ConditionalOnMissingBean` + `interceptor-enabled=true` |

所有 Bean 使用 `@ConditionalOnMissingBean`，**用户定义的同类型 Bean 优先级更高**，会覆盖自动配置的 Bean。

### Spring Boot 版本兼容

| Spring Boot 版本 | 自动配置机制 |
|-----------------|-------------|
| 2.7.x | `META-INF/spring.factories` |
| 3.0+ | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` |

库中同时包含两种配置文件，无需任何修改即可在 Spring Boot 2.7+ 和 3.x 中使用。

---

## 数据库策略详解

### MySQL 策略

| 方面 | 行为 |
|------|------|
| **写入格式** | WKB `byte[]`（4 字节小端 SRID 前缀 + 标准 WKB） |
| **读取包装** | `HEX(column) AS column` |
| **输入函数** | `?`（通过 `PreparedStatement.setBytes()` 直接写入二进制） |
| **兼容数据库** | MySQL 8.0+、MariaDB 10.5+ |

### PostGIS 策略

| 方面 | 行为 |
|------|------|
| **写入格式** | Hex WKB `String`（4 字节小端 SRID 前缀 + 标准 WKB 十六进制） |
| **读取包装** | `(srid_le_hex \|\| encode(ST_AsBinary(column), 'hex')) AS column` |
| **输入函数** | `?`（PostGIS 自动识别 hex WKB 字符串） |
| **兼容数据库** | PostgreSQL 12+ with PostGIS 3.0+ |

### 检测优先级

1. 如果 `mybatis.geometry.database-type` 已显式配置 → 直接使用
2. 否则，检查 `DataSource.getConnection().getMetaData().getURL()`
3. URL 检查失败时，尝试 `getMetaData().getDriverName()`
4. 都失败 → 默认 `MYSQL`（记录警告日志）

---

## SQL 拦截器

### 工作原理

`GeometryFieldInterceptor` 拦截 MyBatis 的 `StatementHandler.prepare()` 调用：

1. **仅处理 SELECT** 查询（INSERT/UPDATE/DELETE 直接跳过）
2. 扫描实体类上标注了 `@PointTableField`、`@PolygonTableField`、`@LineStringTableField` 的字段
3. 改写 SQL 中的几何列引用，用数据库函数包装
4. 字段元数据按实体类缓存，后续查询无需重复反射

### 前提条件

拦截器正确工作需要满足：

- 实体类**必须**有 `@TableName(autoResultMap = true)`
- Mapper **必须**继承 `BaseMapper<实体类>`
- 几何字段**必须**使用 `@*TableField` 注解标记

### 会被改写的 SQL

```sql
-- 原始 SQL（MyBatis Plus 生成）
SELECT id, name, location, boundary FROM warehouse WHERE id = ?

-- 改写后（MySQL）
SELECT id, name, HEX(location) AS location, HEX(boundary) AS boundary FROM warehouse WHERE id = ?

-- 改写后（PostGIS）
SELECT id, name, (srid_hex || encode(ST_AsBinary(location), 'hex')) AS location, ... FROM warehouse WHERE id = ?
```

### 不会被改写的情况

- XML mapper 中的自定义 SQL
- `@Select` 注解查询
- 无法从 mapper 确定实体类的查询
- INSERT / UPDATE / DELETE 语句

---

## 自定义扩展

### 覆盖默认策略

定义自己的 `GeometryHandlerStrategy` Bean 即可替换自动配置的默认策略：

```java
@Configuration
public class CustomGeometryConfig {
    
    @Bean
    public GeometryHandlerStrategy geometryHandlerStrategy() {
        // 自定义实现
        return new MyCustomStrategy();
    }
}
```

由于自动配置使用 `@ConditionalOnMissingBean`，用户自定义的 Bean 优先。

### 覆盖 TypeHandler

同样方式覆盖 TypeHandler：

```java
@Bean
public PointTypeHandler pointTypeHandler() {
    // 使用自定义 SRID 或策略
    return new PointTypeHandler(3857, myCustomStrategy);
}
```

### 添加新几何类型

扩展新的几何类型（如 `MultiPoint`）：

1. 创建 `MultiPointTypeHandler` 继承 `AbstractGeometryTypeHandler<MultiPoint>`
2. 创建 `@MultiPointTableField` 注解
3. 在配置类中注册

```java
public class MultiPointTypeHandler extends AbstractGeometryTypeHandler<MultiPoint> {
    
    @Override
    protected MultiPoint parseGeometry(String hexString) {
        if (hexString == null || hexString.isEmpty()) return null;
        return WkbUtil.fromWkbAsMultiPoint(hexString);
    }
    
    @Override
    protected void validateGeometry(MultiPoint geometry) throws SQLException {
        // 自定义验证逻辑
    }
    
    @Override
    protected String getGeometryTypeName() {
        return "MultiPoint";
    }
}
```

### 禁用拦截器使用手动 SQL

如果希望完全控制 SQL：

```yaml
mybatis:
  geometry:
    interceptor-enabled: false
```

然后在 mapper XML 中手动包装：

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

## 多数据源配置

自动配置绑定到主 DataSource 上的单一策略。多数据源环境需要手动配置。

### 方式一：按 DataSource 配置

```java
@Configuration
public class GeometryMultiDsConfig {
    
    @Bean
    @Primary
    public GeometryHandlerStrategy primaryStrategy(
            @Qualifier("primaryDataSource") DataSource ds) {
        return GeometryStrategyFactory.detectStrategy(ds);
    }
    
    // 为第二数据源创建单独的 TypeHandler
    @Bean
    public PointTypeHandler secondaryPointTypeHandler(
            @Qualifier("secondaryDataSource") DataSource ds) {
        GeometryHandlerStrategy strategy = GeometryStrategyFactory.detectStrategy(ds);
        return new PointTypeHandler(4326, strategy);
    }
}
```

### 方式二：显式指定类型

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

> **注意**：SQL 拦截器使用单一策略。如果不同数据源需要不同的包装方式，建议禁用拦截器并使用手动 SQL。

---

## 常见问题

### Q：几何字段查询始终返回 null

**原因**：实体类缺少 `autoResultMap = true`。

**解决**：
```java
@TableName(value = "your_table", autoResultMap = true)  // ← 必须加上
public class YourEntity { ... }
```

### Q：INSERT 报 `org.apache.ibatis.type.TypeException: Could not set parameters`

**原因**：JDBC 驱动无法处理几何格式。

**排查**：
1. 确认 `database-type` 与实际数据库一致
2. PostGIS 需启用扩展：`CREATE EXTENSION IF NOT EXISTS postgis;`
3. MySQL 确认列类型为 `GEOMETRY`、`POINT`、`POLYGON` 或 `LINESTRING`

### Q：INSERT 报 SRID 不匹配错误（MySQL）

**原因**：表列有 `SRID 4326` 约束但几何对象的 SRID 为 0。

**解决**：确保几何对象有正确的 SRID：
```java
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
point.setSRID(4326);  // ← 显式设置 SRID
```

或配置默认 SRID：
```yaml
mybatis:
  geometry:
    default-srid: 4326
```

### Q：PostgreSQL 报 HEX() 函数不存在

**原因**：库默认使用 MySQL 策略（调用 `HEX()`），但实际连接的是 PostgreSQL。

**解决**：
1. 设置 `mybatis.geometry.database-type: POSTGRESQL`
2. 或确保 JDBC URL 包含 `postgresql`/`postgres` 以便自动检测

### Q：PostGIS 报 geometry 类型不存在

**原因**：PostgreSQL 未启用 PostGIS 扩展。

**解决**：
```sql
CREATE EXTENSION IF NOT EXISTS postgis;
```

### Q：拦截器不改写我的自定义 SQL

**正常行为**：拦截器只处理 MyBatis Plus 内置方法（`selectById`、`selectList` 等）生成的查询。自定义 `@Select` 或 XML 查询不会被改写。

**解决**：在自定义 SQL 中手动包装几何列（参见[禁用拦截器使用手动 SQL](#禁用拦截器使用手动-sql)）。

### Q：拦截器对性能有影响吗？

拦截器开销极小：
- 字段元数据按实体类缓存（一次性反射成本）
- SQL 改写使用简单字符串操作
- 仅拦截 SELECT 语句

实测开销 < 1ms/查询。

### Q：不用 Spring Boot 能用吗？

自动配置依赖 Spring Boot，但核心类（`WkbUtil`、TypeHandler、Strategy）可以独立使用：

```java
// 不使用 Spring 的手动配置
PointTypeHandler handler = new PointTypeHandler(4326, new MySQLGeometryStrategy());
// 在 MyBatis Configuration 中手动注册 TypeHandler
```

---

## 版本兼容性

| mybatis-plus-geometry | Java | Spring Boot | MyBatis Plus | MySQL | PostgreSQL + PostGIS |
|----------------------|------|-------------|-------------|-------|---------------------|
| 1.0.x | 17+ | 2.7+ / 3.x | 3.5+ | 8.0+ | 12+ / 3.0+ |

### 使用的依赖版本

| 依赖 | 版本 | 作用域 |
|------|------|--------|
| jts-core | 1.19.0 | `api`（传递给用户） |
| commons-codec | 1.16.0 | `implementation` |
| slf4j-api | 2.0.9 | `implementation` |
| mybatis-plus-boot-starter | 3.5.7 | `compileOnly`（用户提供） |
| jackson-databind | 2.15.3 | `compileOnly`（可选） |
| spring-boot-autoconfigure | 3.2.2 | `compileOnly`（用户提供） |

> Jackson 序列化器是可选的。如果 classpath 中没有 `jackson-databind`，GeoJSON 功能不可用但不会报错。
