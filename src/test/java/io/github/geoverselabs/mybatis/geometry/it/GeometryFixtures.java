package io.github.geoverselabs.mybatis.geometry.it;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

/**
 * Shared sample geometries (WGS84 longitude/latitude around Shanghai).
 */
public final class GeometryFixtures {

    public static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private GeometryFixtures() {
    }

    public static Geometry wkt(String wkt) {
        return wkt(wkt, 4326);
    }

    public static Geometry wkt(String wkt, int srid) {
        try {
            Geometry g = new WKTReader(new GeometryFactory(new PrecisionModel(), srid)).read(wkt);
            g.setSRID(srid);
            return g;
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Point point() {
        return (Point) wkt("POINT (121.4737 31.2304)");
    }

    public static LineString lineString() {
        return (LineString) wkt("LINESTRING (121.47 31.23, 121.48 31.24, 121.49 31.22)");
    }

    public static Polygon polygonWithHole() {
        return (Polygon) wkt("POLYGON ((121.4 31.1, 121.6 31.1, 121.6 31.3, 121.4 31.3, 121.4 31.1),"
            + " (121.45 31.15, 121.45 31.25, 121.55 31.25, 121.55 31.15, 121.45 31.15))");
    }

    public static MultiPoint multiPoint() {
        return (MultiPoint) wkt("MULTIPOINT ((121.4 31.2), (121.5 31.3))");
    }

    public static MultiLineString multiLineString() {
        return (MultiLineString) wkt("MULTILINESTRING ((121.4 31.2, 121.5 31.3), (121.6 31.4, 121.7 31.5, 121.8 31.4))");
    }

    public static MultiPolygon multiPolygon() {
        return (MultiPolygon) wkt("MULTIPOLYGON (((121.0 31.0, 121.1 31.0, 121.1 31.1, 121.0 31.1, 121.0 31.0)),"
            + " ((122.0 32.0, 122.2 32.0, 122.2 32.2, 122.0 32.2, 122.0 32.0),"
            + " (122.05 32.05, 122.05 32.15, 122.15 32.15, 122.15 32.05, 122.05 32.05)))");
    }

    public static GeometryCollection geometryCollection() {
        return (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (121.4 31.2), LINESTRING (121.4 31.2, 121.5 31.3))");
    }

    public static GeoAll fullEntity(String name) {
        GeoAll e = new GeoAll();
        e.setName(name);
        e.setPt(point());
        e.setLs(lineString());
        e.setPg(polygonWithHole());
        e.setMpt(multiPoint());
        e.setMls(multiLineString());
        e.setMpg(multiPolygon());
        e.setGc(geometryCollection());
        e.setGeom(polygonWithHole());
        return e;
    }
}
