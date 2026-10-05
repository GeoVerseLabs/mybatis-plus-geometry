# 配置参考

[English](CONFIGURATION.md) | 简体中文

本文档完整介绍 mybatis-plus-geometry 的全部配置项、自动配置行为、进阶用法以及常见问题排查。

---

## 目录

- [配置属性](#配置属性)
- [自动配置](#自动配置)
- [数据库策略详情](#数据库策略详情)
- [GeoJSON](#geojson)
- [SQL 拦截器（旧版，可选）](#sql-拦截器旧版可选)
- [自定义扩展](#自定义扩展)
- [多数据源配置](#多数据源配置)
- [常见问题](#常见问题)
- [版本兼容性](#版本兼容性)

---

## 配置属性

所有属性位于 `application.yml` 或 `application.properties` 的 `mybatis.geometry` 前缀下。

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `default-srid` | `int` | `4326` | SRID 为 0 的几何所使用的 SRID。4326 = WGS84（GPS 坐标）。 |
| `database-type` | `enum` | *（自动检测）* | `MYSQL`（含 MariaDB）或 `POSTGRESQL`。不设置时从 DataSource 检测。 |
| `write-validation` | `enum` | `BASIC` | 写库前的校验：`FULL`（OGC `isValid()`）、`BASIC`（坐标为有限数值）、`NONE`。 |
| `preserve-z` | `boolean` | `false` | 向 PostGIS 写入 Z 值。MySQL 与 MariaDB 始终按二维写入。 |
| `coordinate-sequence` | `enum` | `ARRAY` | 库内创建几何时的坐标存储方式：`ARRAY` 或 `PACKED`。 |
| `interceptor-enabled` | `boolean` | `false` | 注册旧版 SELECT 拦截器。TypeHandler 已能直接读取几何列，无需开启。 |
| `geojson.coordinate-precision` | `Integer` | *（完整精度）* | 每个坐标值输出的小数位数（0–15）。RFC 7946 建议 6 位。 |
| `geojson.validation` | `enum` | `FULL` | GeoJSON 输入校验：`FULL`（多边形额外做 OGC `isValid()`）或 `BASIC`。 |
| `geojson.coordinate-range-validation` | `Boolean` | *（自动）* | 拒绝经度超出 ±180、纬度超出 ±90 的输入。不设置时仅在 `default-srid` 为 4326 时启用。 |
| `geojson.fast-double-parsing` | `boolean` | `true` | 使用 Jackson 快速 double 解析器解析坐标（Jackson 2.14+，更低版本自动忽略）。 |

### YAML 示例（完整）

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

### Properties 示例

```properties
mybatis.geometry.default-srid=4326
mybatis.geometry.database-type=MYSQL
mybatis.geometry.write-validation=BASIC
mybatis.geometry.geojson.coordinate-precision=6
```

### 属性详解

#### `default-srid`

空间参考系统标识符，应用于未显式设置 SRID（SRID = 0）的几何对象。

- **4326**（WGS84）：标准 GPS 经纬度，最常用。
- **3857**（Web Mercator）：Web 地图（Google Maps、OpenStreetMap 瓦片）使用。
- **0**：不带空间参考存储（MySQL `SRID 0` 列、笛卡尔坐标数据）。
- 自定义 SRID：数据使用本地坐标系时，可设置任意有效的 EPSG 代码。

> **注意**：仅影响 SRID 为 0 的几何。如果代码中执行了 `point.setSRID(4326)`，该属性对这个几何不起作用。库不会修改传入的几何对象，默认值只作用于编码结果。

#### SRID 管理最佳实践

SRID 按两级优先级解析：

```
业务代码显式 setSRID()（最高优先级）
        ↓ SRID == 0 时回退
全局配置：mybatis.geometry.default-srid（最低优先级）
```

**推荐做法**：使用 `GeometryFactoryProvider.getFactory()` 创建几何对象。该工厂已按 `default-srid`（以及 `coordinate-sequence`）预先配置，通过它创建的几何自动带有正确的 SRID：

```java
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;

// 工厂由 mybatis.geometry.default-srid 全局配置
GeometryFactory factory = GeometryFactoryProvider.getFactory();

// 该点自动带有 SRID = 4326（或你配置的值）
Point point = factory.createPoint(new Coordinate(121.5, 31.2));
// point.getSRID() == 4326 ✓ —— 无需手动 setSRID()
```

**何时使用其他 SRID**：仅当某个几何需要与全局默认不同的 SRID 时。`GeometryFactoryProvider.getFactory(srid)` 会为任意 SRID 返回缓存的工厂：

```java
GeometryFactory utm = GeometryFactoryProvider.getFactory(32650);  // UTM 50N 带
Point localPoint = utm.createPoint(new Coordinate(500000, 4649776));
```

从数据库读出的几何使用带有其 SRID 的工厂创建，因此集合中的每个子几何以及派生几何（`buffer()`、`union()`、`getCentroid()` 等）都会保留 SRID。

**小结**：
- 在 `application.yml` 中为项目统一设置一次 `mybatis.geometry.default-srid`
- 始终使用 `GeometryFactoryProvider.getFactory()` 创建几何对象
- 仅在坐标系不同的特殊情况下使用 `getFactory(srid)`
- SRID 0 表示"未设置"：从 SRID 为 0 的行读出的几何写回时会使用 `default-srid`，除非它也是 0

---

#### `write-validation`

TypeHandler 在写入几何前执行的校验：

| 级别 | 检查内容 | 开销 |
|------|----------|------|
| `FULL` | `BASIC` + OGC 有效性（`Geometry.isValid()`：自相交、环方向等） | 每个几何 O(n log n) |
| `BASIC` *（默认）* | X/Y 坐标为有限数值（无 NaN/无穷值） | O(n) |
| `NONE` | 不校验 | – |

默认使用 `BASIC` 的原因：数据库本身允许存储不满足 OGC 规范的几何。若使用 `FULL`，对一行已存有不合规多边形的数据执行只修改其他列的 `updateById` 也会失败，而且大多边形每次写入都要付出校验开销。GeoJSON 输入默认仍做完整校验（`geojson.validation: FULL`），通过 REST 接收几何的应用已在边界处拒绝不合规的形状。如果几何在 Java 代码中构造且必须在数据库中保持有效，请使用 `FULL`。

#### `preserve-z`

JTS 几何可以携带 Z（高程）坐标。设置 `preserve-z: true` 后，至少含一个非 NaN Z 值的几何会以三维 EWKB 写入 PostGIS，其余按二维写入。MySQL 与 MariaDB 不支持 Z，始终按二维 WKB 写入（丢弃 Z 值时会记录一次警告日志）。

出于兼容性默认为 `false`：早期版本始终写二维，而向二维 PostGIS 列（`geometry(Point,4326)`）写入三维几何会报错。使用 `PointZ`/`PolygonZ` 等列时请开启。

带 M（测量值）坐标的几何在读取时会被明确拒绝：JTS 1.19 无法表示 M，否则会被悄悄当作 Z。

#### `coordinate-sequence`

| 取值 | 存储方式 | 适用场景 |
|------|----------|----------|
| `ARRAY` *（默认）* | 每个点一个 `Coordinate` 对象（JTS 默认） | 需要原地修改 `geometry.getCoordinates()` 的代码 |
| `PACKED` | 每个坐标序列一个 `double[]` | 大几何、高吞吐：每个二维点的堆内存约降为 1/2.7，GC 压力更小 |

使用 `PACKED` 时，`getCoordinates()` 返回的是副本，修改它不会改变几何本身。请改用 `getCoordinateSequence().setOrdinate(...)` 并调用 `geometryChanged()`。

#### `interceptor-enabled`

控制是否将旧版 `GeometryFieldInterceptor` 注册为 MyBatis 插件。**现在已不需要它**：TypeHandler 能直接读取几何列（见[数据库策略详情](#数据库策略详情)），手写 SQL、关联查询和 XML 映射同样适用。开启它只会让数据库把几何转成十六进制文本，MySQL 下传输字节数翻倍。

- **false**（默认）：不改写 SQL。
- **true**：为含几何字段的实体生成的 SELECT 语句会包装几何列（MySQL 为 `HEX(col)`，PostGIS 为 `encode(ST_AsEWKB(col), 'hex')`）。参见 [SQL 拦截器](#sql-拦截器旧版可选)。

#### `database-type`

覆盖自动数据库检测。适用于：

- 存在多个 DataSource（自动检测要求唯一的 DataSource Bean）
- 启动时数据库不可达
- 希望测试中行为确定

不设置时，库会从唯一的 `DataSource` Bean 获取一个连接并检查：
1. `DatabaseMetaData.getDatabaseProductName()`：`MySQL`/`MariaDB` → `MYSQL`，`PostgreSQL` → `POSTGRESQL`
2. 否则检查 JDBC URL 协议（`jdbc:mysql:`、`jdbc:mariadb:`、`jdbc:postgresql:`，也支持 `jdbc:p6spy:`、`jdbc:tc:` 等包装形式）。不会匹配主机名或库名。
3. 仍无法判断 → `MYSQL`（并记录警告日志）

#### `geojson.*`

见 [GeoJSON](#geojson)。

---

## 自动配置

### 激活条件

自动配置（`GeometryAutoConfiguration`）在**同时**满足以下条件时生效：

1. classpath 中存在 `com.baomidou.mybatisplus.core.mapper.BaseMapper`
2. classpath 中存在 `org.locationtech.jts.geom.Geometry`

缺少任一类时自动配置会被静默跳过。Jackson 是可选的：没有 `jackson-databind` 时应用照常启动，只是不注册 GeoJSON 模块。

### 注册的 Bean

生效时注册以下 Bean（用户已定义的除外）：

| Bean | 类型 | 条件 |
|------|------|------|
| `geometryHandlerStrategy` | `GeometryHandlerStrategy` | `@ConditionalOnMissingBean` |
| `pointTypeHandler`、`lineStringTypeHandler`、`polygonTypeHandler`、`multiPointTypeHandler`、`multiLineStringTypeHandler`、`multiPolygonTypeHandler`、`geometryCollectionTypeHandler`、`geometryTypeHandler` | 每种几何类型一个 TypeHandler | `@ConditionalOnMissingBean`（按类型） |
| `geometryJacksonModule` | `GeometryJacksonModule` | classpath 中有 Jackson，且不存在同名 Bean |
| `geometryFieldInterceptor` | `GeometryFieldInterceptor` | `@ConditionalOnMissingBean` + `interceptor-enabled=true` |

MyBatis-Plus 会按 Java 类型注册这些 TypeHandler Bean，因此在没有 `@TableField(typeHandler = …)` 的地方几何值同样能被处理，例如 `lambdaUpdate().set(Warehouse::getBoundary, polygon)` 之类的 Wrapper 参数，以及未开启 `autoResultMap` 的实体结果映射。

### 进程级默认值

MyBatis-Plus 通过反射实例化 `@TableField(typeHandler = …)` 引用的 TypeHandler，Jackson 对 `@JsonSerialize(using = …)` 也是如此，这些实例无法注入 Spring Bean。因此自动配置还会把配置发布到进程级默认值中，这些实例每次调用时读取：

| 配置 | 存放位置 |
|------|----------|
| 数据库策略（任意 `GeometryHandlerStrategy` Bean，包括自定义的） | `GeometryStrategyFactory.setDefaultStrategy` |
| `default-srid`、`coordinate-sequence` | `GeometryFactoryProvider` |
| `write-validation`、`preserve-z` | `GeometryDefaults` |
| `geojson.*` | `GeoJsonOptions.setGlobal` |

不使用 Spring Boot 时，请自行调用这些 setter。若多个配置不同的应用上下文共享同一个类加载器，以最后启动的为准。

### Spring Boot 兼容性

| Spring Boot 版本 | 自动配置机制 | MyBatis-Plus starter |
|-----------------|-------------|----------------------|
| 2.7.x | `META-INF/spring.factories` | `mybatis-plus-boot-starter` |
| 3.0+ | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | `mybatis-plus-spring-boot3-starter` |

两个文件都已包含——库无需任何修改即可在 Spring Boot 2.7+ 和 3.x 中使用。

---

## 数据库策略详情

### MySQL 策略

| 方面 | 行为 |
|------|------|
| **写入格式** | `byte[]`：4 字节小端 SRID + 二维 WKB（`PreparedStatement.setBytes()`），即 MySQL 内部几何格式 |
| **读取** | 对原始列调用 `ResultSet.getBytes()`（同样是内部格式） |
| **同样可解析** | `HEX(column)` 文本（旧版拦截器或手写 SQL） |
| **兼容数据库** | MySQL 8.0+、MariaDB 10.5+ |

### PostGIS 策略

| 方面 | 行为 |
|------|------|
| **写入格式** | 内嵌 SRID 的十六进制 EWKB `String`（`setObject(value, Types.OTHER)`）；满足 `preserve-z` 条件时为三维 |
| **读取** | 对原始列调用 `ResultSet.getString()`（PostgreSQL 返回十六进制 EWKB） |
| **同样可解析** | `encode(ST_AsEWKB(col), 'hex')`、`ST_AsEWKB(col)` / `ST_AsBinary(col)`（bytea）、普通 WKB 十六进制，以及 1.0.x 的"SRID 前缀 + WKB"十六进制格式 |
| **兼容数据库** | PostgreSQL 12+ 及 PostGIS 3.0+ |

通过 `ST_AsBinary` 读取的值不带 SRID，几何的 SRID 为 0；请直接查询列本身或使用 `ST_AsEWKB` 以保留 SRID。

### 检测优先级

1. 显式设置了 `mybatis.geometry.database-type` → 直接使用（不会打开连接）
2. 否则，存在唯一 `DataSource` Bean 时 → 先看产品名，再看 JDBC URL 协议（见 [`database-type`](#database-type)）
3. 存在多个或没有 DataSource，或检测失败 → `MYSQL`（记录警告日志）

---

## GeoJSON

`GeometryJacksonModule`（存在 Jackson 时自动注册）按 RFC 7946 GeoJSON 读写所有几何类型。

**输入**
- 对 JSON token 单次流式遍历；`type` 可以出现在 `coordinates` 之前或之后；未知成员（`bbox`、`crs` 等）会被忽略。
- 坐标位置必须是数字：字符串、`null`、布尔值会被拒绝（1.0.x 会悄悄当作 0）。第三个数字作为 Z 保留，更多的数字被忽略。
- NaN 和无穷值始终被拒绝；满足 `coordinate-range-validation` 条件时校验 WGS84 范围。
- 多边形的环必须闭合且至少有 4 个位置；Polygon 和 MultiPolygon 的环会被规范为 RFC 7946 方向（外环逆时针，内环顺时针）。
- `validation: FULL` 还要求 Polygon、MultiPolygon 以及 GeometryCollection 中的面状成员满足 OGC 有效性。
- 空的 `coordinates` / `geometries` 数组产生空几何；支持嵌套的 GeometryCollection。
- 错误类型为 `JsonMappingException`（`GeoJsonParseException`、`InvalidCoordinateException`），带有 JSON 位置和属性路径，Spring MVC 会返回 HTTP 400。

**输出**
- 先写 `type`；环遵循 RFC 7946 右手规则；有 Z 值时输出 Z；空几何输出为 `"coordinates": []`。
- `coordinate-precision` 对每个坐标值四舍五入并去掉末尾的 0，每个数字的格式化不产生对象分配。字段或 getter 上的 `@GeoJsonPrecision(n)` 可覆盖全局设置，`@GeoJsonPrecision(GeoJsonPrecision.FULL)` 让该字段恢复完整精度。
- 支持 Jackson 默认类型（default typing），例如 Redis 的 `GenericJackson2JsonRedisSerializer`。

**不使用 Spring 时的用法**

```java
ObjectMapper mapper = new ObjectMapper()
    .registerModule(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(6)));
```

**流式输出**：`GeoJsonStreams` 可从任意 `Iterable`（包括 MyBatis `Cursor`）逐条写出 JSON 数组、FeatureCollection 和 GeoJSON 文本序列（`application/geo+json-seq`，RFC 8142）。控制器示例见 README。

**HTTP 压缩**：gzip 通常能把 GeoJSON 压缩 60–75%。请把 `application/geo+json` 加入 `server.compression.mime-types`。

---

## SQL 拦截器（旧版，可选）

1.0.x 需要拦截器，是因为 TypeHandler 只能解析十六进制文本。现在 TypeHandler 能直接读取原始几何列，因此拦截器默认关闭，仅为希望保留旧 SQL 形态的应用保留。

### 工作原理

开启后，`GeometryFieldInterceptor` 拦截 MyBatis 的 `StatementHandler.prepare()` 调用：

1. **只处理 SELECT** 语句，且只改写最外层的查询列表（`exists`、`inSql`、`apply` 中的子查询和派生表保持不变；`UNION`/`WITH` 语句不改写）
2. 从语句的结果映射或 Mapper 的 `BaseMapper<T>` 类型确定实体；几何列来自 MyBatis-Plus 的表元数据（类型处理器为几何 TypeHandler 的字段）
3. 包装主表的几何列并保留其别名；单表查询时 `SELECT *` 会展开为实体的列，主表的 `alias.*` 在关联查询中同样会展开
4. 改写结果按 SQL 文本缓存（调用拦截器实例的 `clearCaches()` 可清空缓存）

如果在 MyBatis XML 中注册拦截器而不是通过 Spring Boot，请用 `databaseType` 属性固定数据库类型；否则拦截器使用进程级默认策略：

```xml
<plugins>
    <plugin interceptor="io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor">
        <property name="databaseType" value="POSTGRESQL"/>
    </plugin>
</plugins>
```

### 改写示例

```sql
-- 原始（MyBatis Plus 生成）
SELECT id, name, location, boundary FROM warehouse WHERE id = ?

-- 拦截器改写后（MySQL）
SELECT id, name, HEX(location) AS location, HEX(boundary) AS boundary FROM warehouse WHERE id = ?

-- 拦截器改写后（PostGIS）
SELECT id, name, encode(ST_AsEWKB(location), 'hex') AS location, ... FROM warehouse WHERE id = ?
```

### 不会改写的情况

- INSERT / UPDATE / DELETE 语句
- 查询列表中的表达式、函数调用和子查询
- 关联查询中以其他表别名限定的列
- 无法确定实体的语句

这些情况都无需改写：TypeHandler 会直接读取原始列。

---

## 自定义扩展

### 覆盖默认策略

定义自己的 `GeometryHandlerStrategy` Bean 以替换自动配置的策略：

```java
@Configuration
public class CustomGeometryConfig {

    @Bean
    public GeometryHandlerStrategy geometryHandlerStrategy() {
        // 你的自定义实现
        return new MyCustomStrategy();
    }
}
```

由于自动配置使用 `@ConditionalOnMissingBean`，你的 Bean 优先生效，并同时被发布为反射创建的 TypeHandler 所用的进程级默认策略。自定义策略可以覆盖 `read(ResultSet, String)` / `read(ResultSet, int)` / `read(CallableStatement, int)` 控制读取（默认实现调用 `getString()` 再交给 `parseFromDatabase`），并通过 `convertForDatabase(Geometry, int srid)` 控制写入。

### 覆盖 TypeHandler

同样可以提供自定义 TypeHandler：

```java
@Bean
public PointTypeHandler pointTypeHandler() {
    // 固定 SRID 与策略
    return new PointTypeHandler(3857, myStrategy);
}
```

无参构造函数每次调用都会解析当前的策略和默认 SRID，因此由 MyBatis-Plus 创建的 handler 总是跟随当前配置。

### 自定义列名

`@*TableField` 注解是 `@TableField(typeHandler = …)` 的简写，没有属性。如需同时指定列名，请直接使用 `@TableField`，库同样能识别：

```java
@TableField(value = "geo_location", typeHandler = PointTypeHandler.class)
private Point location;
```

不要在同一字段上同时使用 `@TableField("geo_location")` 和 `@PointTableField`：MyBatis-Plus 只采用找到的第一个 `@TableField`，其中一项设置会丢失（直接注解在前时不会绑定任何 TypeHandler）。

### 自定义几何类型

为其他几何类型编写 TypeHandler 只需声明类型和构造函数：

```java
@MappedTypes(LinearRing.class)
public class LinearRingTypeHandler extends AbstractGeometryTypeHandler<LinearRing> {
    public LinearRingTypeHandler() {
        super();
    }
}
```

读取、类型检查、SRID 处理和校验都由基类提供。如需额外检查，覆盖 `validateGeometry` 即可。

### 手写查询

直接查询几何列，无需包装：

```xml
<select id="findById" resultMap="warehouseResultMap">
    SELECT w.id, w.name, w.location
    FROM warehouse w JOIN region r ON ST_Contains(r.boundary, w.location)
    WHERE w.id = #{id}
</select>
```

为 1.0.x 编写的包装列（`HEX(location)`、`encode(ST_AsEWKB(location), 'hex')`）依然可用。

---

## 多数据源配置

多数据源环境下，各数据源可能连接不同类型的数据库。自动检测要求唯一的 DataSource，因此存在多个 DataSource 时请设置 `database-type` 或自行定义策略。

### 方式一：所有数据源类型相同

```yaml
mybatis:
  geometry:
    database-type: POSTGRESQL
```

### 方式二：数据源类型不同

为每个 `SqlSessionFactory` 的 TypeHandler 指定各自的策略：

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

> **注意**：通过 `@TableField(typeHandler = …)` 反射创建的 handler 使用进程级默认策略。数据库类型不同时，请确保实际使用的是上面显式注册的 handler（例如像示例那样按 Java 类型注册）。

---

## 常见问题

### Q：SELECT 查询中几何字段总是返回 `null`

**原因**：实体类缺少 `autoResultMap = true`（MyBatis-Plus 只通过自动结果映射对查询结果应用 `@TableField(typeHandler)`），或 MyBatis-Plus 版本低于 3.5.6，无法识别 `@*TableField` 注解上的元注解 `@TableField`。

**解决**：
```java
@TableName(value = "your_table", autoResultMap = true)  // ← 必需！
public class YourEntity { ... }
```

### Q：`column 'x' contains a MultiPolygon but the mapped type is Polygon`

列中存储的几何类型与字段类型不一致。请使用匹配的字段类型，或使用 `@GeometryTableField` 标注的通用 `Geometry` 字段。

### Q：`org.apache.ibatis.type.TypeException: Could not set parameters`

**原因**：JDBC 驱动无法处理几何格式。

**检查**：
1. 确认 `database-type` 与实际数据库一致
2. PostGIS 需启用扩展：`CREATE EXTENSION IF NOT EXISTS postgis;`
3. MySQL 需确认列类型为 `GEOMETRY` 或几何子类型

### Q：MySQL 插入时报 SRID 不匹配

**原因**：列上有 `SRID 4326` 约束，但几何的 SRID 不同（或为 0 且 `default-srid` 不是 4326）。

**解决**：使用 `GeometryFactoryProvider.getFactory()` 创建几何，或配置默认 SRID：
```yaml
mybatis:
  geometry:
    default-srid: 4326
```

### Q：PostGIS 报 `Column has Z dimension but geometry does not`

使用 Z 列（`PointZ`、`PolygonZ` 等）时请设置 `mybatis.geometry.preserve-z: true`。

### Q：写库时不再拒绝不合规的多边形

自 1.1 起 `write-validation` 默认为 `BASIC`。设置为 `FULL` 可恢复每次写入的 OGC 校验。GeoJSON 输入默认仍做 `FULL` 校验。

### Q：PostgreSQL 报 `HEX()` 函数不存在

**原因**：开启了拦截器，且在 PostgreSQL 上使用了 MySQL 策略。

**解决**：关闭拦截器（已不需要），或设置 `mybatis.geometry.database-type: POSTGRESQL`。

### Q：不使用 Spring Boot 能用吗？

可以。在 MyBatis 中注册 TypeHandler，并自行设置进程级默认值：

```java
GeometryHandlerStrategy strategy = GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL);
GeometryStrategyFactory.setDefaultStrategy(strategy);   // 供 @TableField 创建的 handler 使用
GeometryFactoryProvider.setDefaultSrid(4326);

MybatisConfiguration configuration = new MybatisConfiguration();
configuration.getTypeHandlerRegistry().register(new PointTypeHandler(4326, strategy));
// … 其他 handler 与 mapper

ObjectMapper mapper = new ObjectMapper().registerModule(new GeometryJacksonModule());
```

---

## 版本兼容性

| mybatis-plus-geometry | Java | Spring Boot | MyBatis Plus | MySQL | PostgreSQL + PostGIS |
|----------------------|------|-------------|-------------|-------|---------------------|
| 1.1.x | 17+ | 2.7+ / 3.x | 3.5.6+ | 8.0+（MariaDB 10.5+） | 12+ / 3.0+ |
| 1.0.x | 17+ | 2.7+ / 3.x | 3.5.6+ | 8.0+ | 12+ / 3.0+ |

### 使用的依赖版本

| 依赖 | 版本 | 作用域 |
|------|------|--------|
| jts-core | 1.19.0 | `api`（传递依赖） |
| slf4j-api | 2.0.9 | `implementation` |
| mybatis-plus-boot-starter | 3.5.7 | `compileOnly`（由用户提供） |
| jackson-databind | 2.15.3 | `compileOnly`（可选；运行时需 2.11+，快速 double 解析需 2.14+） |
| spring-boot-autoconfigure | 3.2.2 | `compileOnly`（由用户提供） |

> Jackson 序列化器是可选的。如果 classpath 中没有 `jackson-databind`，GeoJSON 功能不可用但不会报错。
