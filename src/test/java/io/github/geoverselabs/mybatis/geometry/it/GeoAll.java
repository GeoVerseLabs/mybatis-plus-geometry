package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.github.geoverselabs.mybatis.geometry.annotation.GeometryCollectionTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.GeometryTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.LineStringTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiLineStringTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiPointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.MultiPolygonTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PointTableField;
import io.github.geoverselabs.mybatis.geometry.annotation.PolygonTableField;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Test entity covering every supported geometry type in a single table.
 */
@TableName(value = "geo_all", autoResultMap = true)
public class GeoAll {

    @TableId(type = IdType.AUTO)
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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Point getPt() { return pt; }
    public void setPt(Point pt) { this.pt = pt; }
    public LineString getLs() { return ls; }
    public void setLs(LineString ls) { this.ls = ls; }
    public Polygon getPg() { return pg; }
    public void setPg(Polygon pg) { this.pg = pg; }
    public MultiPoint getMpt() { return mpt; }
    public void setMpt(MultiPoint mpt) { this.mpt = mpt; }
    public MultiLineString getMls() { return mls; }
    public void setMls(MultiLineString mls) { this.mls = mls; }
    public MultiPolygon getMpg() { return mpg; }
    public void setMpg(MultiPolygon mpg) { this.mpg = mpg; }
    public GeometryCollection getGc() { return gc; }
    public void setGc(GeometryCollection gc) { this.gc = gc; }
    public Geometry getGeom() { return geom; }
    public void setGeom(Geometry geom) { this.geom = geom; }
}
