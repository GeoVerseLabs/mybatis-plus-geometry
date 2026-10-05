package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.geoverselabs.mybatis.geometry.annotation.GeometryCollectionTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.GeometryTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.LineStringTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiLineStringTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiPointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiPolygonTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PolygonTableField;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.PolygonTypeHandler;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.type.StringTypeHandler;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Map;

/**
 * Entities, annotations and mappers shared by the interceptor tests.
 *
 * <p>MyBatis-Plus keeps table metadata in a static cache keyed by entity class, so every entity
 * is used with one configuration style only, and the {@code Unregistered*} entities are never
 * registered with any mapper.</p>
 */
public final class TestEntities {

    private TestEntities() {
    }

    /** User-defined annotation composed from {@code @PointTableField}. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @PointTableField
    public @interface Location {
    }

    /** Two levels of composition. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Location
    public @interface DeepLocation {
    }

    // ------------------------------------------------------------------ shop

    @TableName(value = "shop", autoResultMap = true)
    public static class Shop {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String name;
        @PointTableField
        private Point location;
    }

    /** Result type of statements that do not return the entity. */
    public static class CityDto {
        private Long id;
        private String location;
    }

    public interface ShopMapper extends BaseMapper<Shop> {

        @Select("SELECT * FROM shop WHERE name = #{name}")
        @ResultMap("mybatis-plus_Shop")
        List<Shop> findByName(@Param("name") String name);

        @Select("SELECT *\nFROM shop\nWHERE name = #{name}")
        @ResultMap("mybatis-plus_Shop")
        List<Shop> findByNameMultiLine(@Param("name") String name);

        @Select("SELECT s.* FROM shop s WHERE s.id = #{id}")
        @ResultMap("mybatis-plus_Shop")
        Shop findStarById(@Param("id") Long id);

        @Select("SELECT w.location loc, w.name FROM shop w WHERE w.id = #{id}")
        List<Map<String, Object>> implicitAlias(@Param("id") Long id);

        @Select("SELECT id, location FROM city")
        List<CityDto> cities();

        @Select("SELECT s.id, c.location FROM shop s JOIN city c ON c.id = s.city_id")
        List<Map<String, Object>> joinedMaps();

        @Select("SELECT id, location FROM shop UNION ALL SELECT id, location FROM shop_archive")
        @ResultMap("mybatis-plus_Shop")
        List<Shop> union();
    }

    /** A plain MyBatis mapper (no BaseMapper) whose statements return the entity. */
    public interface PlainShopMapper {

        @Select("SELECT id, name, location FROM shop")
        List<Shop> all();

        @Select("SELECT id, location FROM shop")
        List<Map<String, Object>> maps();
    }

    // ------------------------------------------------------------------ logic delete

    @TableName(value = "parcel", autoResultMap = true)
    public static class Parcel {
        @TableId
        private Long id;
        @MultiPolygonTableField
        private MultiPolygon boundary;
        @TableLogic
        private Integer deleted;
    }

    public interface ParcelMapper extends BaseMapper<Parcel> {
    }

    // ------------------------------------------------------------------ every annotation

    @TableName(value = "geo_all", autoResultMap = true)
    public static class GeoAll {
        @TableId
        private Long id;
        private String name;
        @PointTableField
        private Point pt;
        @LineStringTableField
        private LineString ls;
        @PolygonTableField
        private Polygon pg;
        @MultiPointTableField
        private MultiPoint mpt;
        @MultiLineStringTableField
        private MultiLineString mls;
        @MultiPolygonTableField
        private MultiPolygon mpg;
        @GeometryCollectionTableField
        private GeometryCollection gc;
        @GeometryTableField
        private Geometry geom;
        @Location
        private Point composed;
        @DeepLocation
        private Point deep;
        @TableField(typeHandler = PointTypeHandler.class)
        private Point direct;
        @TableField(value = "geo_loc", typeHandler = PolygonTypeHandler.class)
        private Polygon renamed;
        @TableField(typeHandler = StringTypeHandler.class)
        private String text;
        @TableField(exist = false)
        @PointTableField
        private Point notPersisted;
        @PointTableField
        private transient Point transientPoint;
        @PointTableField
        private static Point staticPoint;
    }

    public interface GeoAllMapper extends BaseMapper<GeoAll> {
    }

    // ------------------------------------------------------------------ naming edge cases

    @TableName(value = "site", autoResultMap = true, excludeProperty = {"ignored"})
    public static class Site {
        @TableId("pk_id")
        private Long id;
        @PointTableField
        private Point geoJSONPoint;
        @PointTableField
        private Point location2D;
        /** Direct @TableField declared first: MyBatis-Plus uses it (column `point`, no type handler). */
        @TableField("`point`")
        @PointTableField
        private Point point;
        /** Geometry annotation declared first: MyBatis-Plus uses its meta @TableField (column from the name). */
        @PointTableField
        @TableField("geo_first")
        private Point annotatedFirst;
        @PointTableField
        private Point ignored;
        private String label;
    }

    public interface SiteMapper extends BaseMapper<Site> {
    }

    // ------------------------------------------------------------------ mapper hierarchy

    public interface SuperMapper<T> extends BaseMapper<T> {
    }

    public interface KeyedMapper<K, T> extends SuperMapper<T> {
    }

    @TableName(value = "warehouse", autoResultMap = true)
    public static class Warehouse {
        @TableId
        private Long id;
        private String name;
        @PolygonTableField
        private Polygon area;
    }

    public interface WarehouseMapper extends KeyedMapper<Long, Warehouse> {

        @Select("SELECT id, area FROM warehouse")
        List<Map<String, Object>> maps();
    }

    // ------------------------------------------------------------------ global column format

    @TableName(value = "spot", autoResultMap = true)
    public static class Spot {
        @TableId
        private Long id;
        /** Formatted: "`point`". */
        @TableField(typeHandler = PointTypeHandler.class, keepGlobalFormat = true)
        private Point point;
        /** The meta @TableField of @PointTableField does not keep the global format: "shape". */
        @PointTableField
        private Point shape;
        /** No @TableField: always formatted, "`label`". */
        private String label;
    }

    public interface SpotMapper extends BaseMapper<Spot> {
    }

    // ------------------------------------------------------------------ mapUnderscoreToCamelCase = false

    @TableName(value = "camel", autoResultMap = true)
    public static class Camel {
        @TableId
        private Long id;
        @PointTableField
        private Point homeLocation;
    }

    public interface CamelMapper extends BaseMapper<Camel> {
    }

    // ------------------------------------------------------------------ not registered with MyBatis-Plus

    @TableName(value = "plain_geo", excludeProperty = {"skipped"})
    public static class UnregisteredGeo {
        @TableId("pk")
        private Long id;
        private String placeName;
        @PointTableField
        private Point geoJSONPoint;
        @Location
        private Point composed;
        @TableField(value = "geo_loc")
        @PolygonTableField
        private Polygon renamed;
        @TableField(exist = false)
        @PointTableField
        private Point notPersisted;
        @PointTableField
        private Point skipped;
        @PointTableField
        private transient Point transientPoint;
    }

    public static class UnregisteredChild extends UnregisteredGeo {
        @MultiPolygonTableField
        private MultiPolygon extra;
    }

    public static class UnregisteredNoTable {
        @GeometryTableField
        private Geometry shape;
    }
}
