package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

public interface GeoAllMapper extends BaseMapper<GeoAll> {

    /** Hand-written SQL with a table alias, mapped through the MyBatis-Plus auto result map. */
    @Select("SELECT g.id, g.name, g.pt, g.mpg, g.geom FROM geo_all g WHERE g.id = #{id}")
    @ResultMap("mybatis-plus_GeoAll")
    GeoAll selectWithAlias(@Param("id") Long id);

    /** A MultiPolygon column read into the Polygon field: the handler must report the mismatch. */
    @Select("SELECT id, name, mpg AS pg FROM geo_all WHERE id = #{id}")
    @ResultMap("mybatis-plus_GeoAll")
    GeoAll selectMultiPolygonAsPolygon(@Param("id") Long id);

    /** PostGIS only: bytea EWKB expressions, read by pgjdbc getString() as \x-prefixed hex. */
    @Select("SELECT id, name, ST_AsEWKB(pt) AS pt, ST_AsEWKB(mpg) AS mpg, ST_AsEWKB(gc) AS gc,"
        + " ST_AsEWKB(geom) AS geom FROM geo_all WHERE id = #{id}")
    @ResultMap("mybatis-plus_GeoAll")
    GeoAll selectAsEwkbBytea(@Param("id") Long id);

    /** PostGIS only: plain WKB hex without SRID, as documented for hand-written queries. */
    @Select("SELECT id, name, encode(ST_AsBinary(pg), 'hex') AS pg FROM geo_all WHERE id = #{id}")
    @ResultMap("mybatis-plus_GeoAll")
    GeoAll selectAsPlainWkbHex(@Param("id") Long id);
}
