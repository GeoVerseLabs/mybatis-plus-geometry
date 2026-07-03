# Quick Start Guide

Get up and running with mybatis-plus-geometry in 5 minutes!

## 1. Add Dependency

### Maven
```xml
<dependency>
    <groupId>io.github.geoverselabs</groupId>
    <artifactId>mybatis-plus-geometry-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Gradle
```groovy
implementation 'io.github.geoverselabs:mybatis-plus-geometry-spring-boot-starter:1.0.0'
```

## 2. Create Entity

```java
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
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

## 3. Create Mapper

```java
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StoreMapper extends BaseMapper<Store> {
}
```

## 4. Create Database Table

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

## 5. Use in Service

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

## 6. REST API with GeoJSON

GeoJSON serialization is automatic when Jackson is on the classpath — no annotations needed:

### DTO
```java
public class StoreDTO {
    private Long id;
    private String name;
    private Point location;  // Automatically serialized as GeoJSON
    
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
        // Service logic
        return dto;
    }
}
```

### Request/Response
```json
{
  "name": "Main Store",
  "location": {
    "type": "Point",
    "coordinates": [121.5, 31.2]
  }
}
```

## Configuration (Optional)

```yaml
mybatis:
  geometry:
    default-srid: 4326
    interceptor-enabled: true
    database-type: MYSQL  # or POSTGRESQL (auto-detected if omitted)
```

> Coordinate range validation is automatically enabled when SRID is 4326, and disabled for other SRIDs.

## That's It!

You're now ready to use geometry types in your MyBatis Plus application!

For more details, see the [full README](../README.md).
