package io.github.geoverselabs.mybatis.geometry.codec;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.util.HexFormat;
import java.util.List;

/**
 * Shared test geometries and helpers for the database path tests.
 */
public final class TestGeometries {

    /** One sample of every geometry type, including holes, nesting and empties. */
    public static final List<String> SAMPLE_WKT = List.of(
        "POINT (121.4737 31.2304)",
        "POINT (-180 -90)",
        "LINESTRING (121.47 31.23, 121.48 31.24, 121.49 31.22)",
        "LINESTRING (1 1, 1 1)",
        "POLYGON ((121.4 31.1, 121.6 31.1, 121.6 31.3, 121.4 31.3, 121.4 31.1),"
            + " (121.45 31.15, 121.45 31.25, 121.55 31.25, 121.55 31.15, 121.45 31.15))",
        "POLYGON ((0 0, 2 2, 2 0, 0 2, 0 0))",
        "MULTIPOINT ((121.4 31.2), (121.5 31.3))",
        "MULTILINESTRING ((121.4 31.2, 121.5 31.3), (121.6 31.4, 121.7 31.5, 121.8 31.4))",
        "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((1 0, 2 0, 2 1, 1 1, 1 0),"
            + " (1.2 0.2, 1.2 0.8, 1.8 0.8, 1.8 0.2, 1.2 0.2)))",
        "GEOMETRYCOLLECTION (POINT (121.4 31.2), LINESTRING (121.4 31.2, 121.5 31.3),"
            + " GEOMETRYCOLLECTION (POINT (1 2), POLYGON ((0 0, 1 0, 1 1, 0 0))))",
        "POINT EMPTY",
        "LINESTRING EMPTY",
        "POLYGON EMPTY",
        "MULTIPOINT EMPTY",
        "MULTILINESTRING EMPTY",
        "MULTIPOLYGON EMPTY",
        "GEOMETRYCOLLECTION EMPTY",
        "GEOMETRYCOLLECTION (POINT EMPTY, LINESTRING EMPTY)"
    );

    private TestGeometries() {
    }

    /**
     * Parse WKT with a factory carrying the SRID (so every component has it).
     */
    public static Geometry wkt(String wkt, int srid) {
        try {
            return new WKTReader(new GeometryFactory(new PrecisionModel(), srid)).read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parse WKT with SRID 0.
     */
    public static Geometry wkt(String wkt) {
        return wkt(wkt, 0);
    }

    public static byte[] hex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /**
     * Structural equality that also treats empty points (NaN) as equal and checks the type.
     */
    public static boolean sameGeometry(Geometry expected, Geometry actual) {
        if (actual == null || !expected.getGeometryType().equals(actual.getGeometryType())) {
            return false;
        }
        if (expected.isEmpty() || actual.isEmpty()) {
            return expected.isEmpty() && actual.isEmpty()
                && expected.getNumGeometries() == actual.getNumGeometries();
        }
        return expected.equalsExact(actual);
    }
}
