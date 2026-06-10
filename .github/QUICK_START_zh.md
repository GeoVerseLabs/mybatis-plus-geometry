# 快速开始指南

5 分钟快速上手 mybatis-plus-geometry！

[English](QUICK_START.md) | 简体中文

## 1. 添加依赖

### Maven
```xml
<dependency>
    <groupId>io.github.yoy0o</groupId>
    <artifactId>mybatis-plus-geometry-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.yoy0o:mybatis-plus-geometry-spring-boot-starter:1.0.1'
```

## 2. 创建实体类

```java
import io.github.yoy0o.mybatis.geometry.annotation.PointTableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.locationtech.jts.geom.Point;

@TableName(value = "store", autoResultMap = true)
public class Store {
    private Long id;
    private String name;
    
    @PointTableField
    private Point location;
    
    // getters and setters
}
```

## 3. 创建 Mapper

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoreMapper extends BaseMapper<Store> {
}
```

## 4. 创建数据库表

### MySQL
```sql
CREATE TABLE store (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT SRID 4326
);
```

### MariaDB
```sql
CREATE TABLE store (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255),
    location POINT NOT NULL
);
CREATE SPATIAL INDEX idx_store_location ON store(location);
```

### PostgreSQL + PostGIS
```sql
CREATE TABLE store (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    location GEOMETRY(POINT, 4326)
);
```

## 5. 在 Service 中使用

```java
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

@Service
public class StoreService {
    
    private final StoreMapper storeMapper;
    private final GeometryFactory geometryFactory = 
        new GeometryFactory(new PrecisionModel(), 4326);
    
    public StoreService(StoreMapper storeMapper) {
        this.storeMapper = storeMapper;
    }
    
    public void createStore(String name, double longitude, double latitude) {
        Store store = new Store();
        store.setName(name);
        store.setLocation(geometryFactory.createPoint(
            new Coordinate(longitude, latitude)
        ));
        
        storeMapper.insert(store);
    }
    
    public Store findById(Long id) {
        return storeMapper.selectById(id);
    }
}
```

## 6. REST API 使用 GeoJSON

### DTO
```java
import io.github.yoy0o.mybatis.geometry.jackson.PointSerializer;
import io.github.yoy0o.mybatis.geometry.jackson.PointDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

public class StoreDTO {
    private Long id;
    private String name;
    
    @JsonSerialize(using = PointSerializer.class)
    @JsonDeserialize(using = PointDeserializer.class)
    private Point location;
    
    // getters and setters
}
```

### Controller
```java
@RestController
@RequestMapping("/api/stores")
public class StoreController {
    
    private final StoreService storeService;
    
    @PostMapping
    public StoreDTO create(@RequestBody StoreDTO dto) {
        // Service 逻辑
        return dto;
    }
}
```

### 请求/响应示例
```json
{
  "name": "主店",
  "location": {
    "type": "Point",
    "coordinates": [121.5, 31.2]
  }
}
```

## 配置（可选）

```yaml
mybatis:
  geometry:
    default-srid: 4326
    interceptor-enabled: true
    database-type: MYSQL  # 或 POSTGRESQL
```

## 完成！

现在您可以在 MyBatis Plus 应用中使用几何类型了！

更多详情请查看[完整 README](../README_zh.md)。

## 常见问题

### Q: 为什么需要 `autoResultMap = true`？

A: 这是 MyBatis Plus 的要求，用于启用自定义 TypeHandler。没有这个配置，几何字段将无法正确映射。

### Q: 支持哪些几何类型？

A: 目前支持：
- Point（点）
- Polygon（多边形）
- LineString（线串）

### Q: 如何处理空间查询？

A: 可以在 Mapper 中编写自定义 SQL：

```java
@Select("SELECT * FROM store " +
        "WHERE ST_Distance(location, ST_GeomFromText(#{wkt}, 4326)) < #{distance}")
List<Store> findNearby(@Param("wkt") String wkt, @Param("distance") double distance);
```

### Q: 如何更改默认 SRID？

A: 在配置文件中设置：

```yaml
mybatis:
  geometry:
    default-srid: 3857  # 例如使用 Web Mercator
```

### Q: 数据库中存储的是什么格式？

A: 使用 WKB（Well-Known Binary）格式，这是 OGC 标准的二进制格式，被所有主流 GIS 数据库支持。

## 下一步

- 查看[完整文档](../README_zh.md)
- 阅读[贡献指南](../CONTRIBUTING.md)
- 浏览[示例代码](../examples/)（如果有）
- 加入[讨论区](https://github.com/yoy0o/mybatis-plus-geometry/discussions)
