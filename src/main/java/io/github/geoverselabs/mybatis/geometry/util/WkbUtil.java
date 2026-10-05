package io.github.geoverselabs.mybatis.geometry.util;

import io.github.geoverselabs.mybatis.geometry.codec.WkbSupport;
import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * Utility class for conversion between JTS geometries and the SRID-prefixed WKB format
 * (4-byte little-endian SRID followed by 2D little-endian WKB, the MySQL internal geometry format).
 *
 * <p>Hex output is uppercase. When a geometry's SRID is 0, the configured default SRID
 * ({@link GeometryFactoryProvider#getConfiguredSrid()}) is written. Input geometries are never
 * modified, and empty geometries are supported. Parsed geometries are created with
 * {@link GeometryFactoryProvider#getFactory(int)} for the decoded SRID. All methods are thread-safe.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Point point = geometryFactory.createPoint(new Coordinate(121.5, 31.2));
 * String wkb = WkbUtil.toWkb(point);
 * Point restored = WkbUtil.fromWkbAsPoint(wkb);
 * }</pre>
 */
public final class WkbUtil {

    /** Default SRID (WGS84) */
    public static final int DEFAULT_SRID = 4326;

    private WkbUtil() {
        // Utility class, prevent instantiation
    }

    /**
     * Geometry type codes as defined in WKB specification.
     */
    public enum GeometryType {
        POINT(1),
        LINESTRING(2),
        POLYGON(3),
        MULTIPOINT(4),
        MULTILINESTRING(5),
        MULTIPOLYGON(6),
        GEOMETRYCOLLECTION(7);

        private final int code;

        GeometryType(int code) {
            this.code = code;
        }

        /**
         * WKB type code.
         *
         * @return the code (1-7)
         */
        public int getCode() {
            return code;
        }
    }

    // ==================== Point Conversion ====================

    /**
     * Convert JTS Point to WKB hex string.
     *
     * @param point the Point to convert
     * @return WKB hex string with SRID prefix, or null if point is null
     */
    public static String toWkb(Point point) {
        return toHex(point);
    }

    /**
     * Convert JTS Point to WKB byte array.
     *
     * @param point the Point to convert
     * @return WKB byte array with SRID prefix, or null if point is null
     */
    public static byte[] toWkbBytes(Point point) {
        return encode(point);
    }

    /**
     * Parse WKB hex string to JTS Point.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Point, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a Point geometry
     */
    public static Point fromWkbAsPoint(String wkbHex) {
        return fromWkbAs(wkbHex, Point.class, "Point");
    }

    // ==================== LineString Conversion ====================

    /**
     * Convert JTS LineString to WKB hex string.
     *
     * @param lineString the LineString to convert
     * @return WKB hex string with SRID prefix, or null if lineString is null
     */
    public static String toWkb(LineString lineString) {
        return toHex(lineString);
    }

    /**
     * Convert JTS LineString to WKB byte array.
     *
     * @param lineString the LineString to convert
     * @return WKB byte array with SRID prefix, or null if lineString is null
     */
    public static byte[] toWkbBytes(LineString lineString) {
        return encode(lineString);
    }

    /**
     * Parse WKB hex string to JTS LineString.
     *
     * @param wkbHex the WKB hex string
     * @return JTS LineString, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a LineString geometry
     */
    public static LineString fromWkbAsLineString(String wkbHex) {
        return fromWkbAs(wkbHex, LineString.class, "LineString");
    }

    // ==================== Polygon Conversion ====================

    /**
     * Convert JTS Polygon to WKB hex string.
     *
     * @param polygon the Polygon to convert
     * @return WKB hex string with SRID prefix, or null if polygon is null
     */
    public static String toWkb(Polygon polygon) {
        return toHex(polygon);
    }

    /**
     * Convert JTS Polygon to WKB byte array.
     *
     * @param polygon the Polygon to convert
     * @return WKB byte array with SRID prefix, or null if polygon is null
     */
    public static byte[] toWkbBytes(Polygon polygon) {
        return encode(polygon);
    }

    /**
     * Parse WKB hex string to JTS Polygon.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Polygon, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a Polygon geometry
     */
    public static Polygon fromWkbAsPolygon(String wkbHex) {
        return fromWkbAs(wkbHex, Polygon.class, "Polygon");
    }

    // ==================== MultiPoint Conversion ====================

    /**
     * Convert JTS MultiPoint to WKB hex string.
     *
     * @param multiPoint the MultiPoint to convert
     * @return WKB hex string with SRID prefix, or null if multiPoint is null
     */
    public static String toWkb(MultiPoint multiPoint) {
        return toHex(multiPoint);
    }

    /**
     * Convert JTS MultiPoint to WKB byte array.
     *
     * @param multiPoint the MultiPoint to convert
     * @return WKB byte array with SRID prefix, or null if multiPoint is null
     */
    public static byte[] toWkbBytes(MultiPoint multiPoint) {
        return encode(multiPoint);
    }

    /**
     * Parse WKB hex string to JTS MultiPoint.
     *
     * @param wkbHex the WKB hex string
     * @return JTS MultiPoint, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a MultiPoint geometry
     */
    public static MultiPoint fromWkbAsMultiPoint(String wkbHex) {
        return fromWkbAs(wkbHex, MultiPoint.class, "MultiPoint");
    }

    // ==================== MultiLineString Conversion ====================

    /**
     * Convert JTS MultiLineString to WKB hex string.
     *
     * @param multiLineString the MultiLineString to convert
     * @return WKB hex string with SRID prefix, or null if multiLineString is null
     */
    public static String toWkb(MultiLineString multiLineString) {
        return toHex(multiLineString);
    }

    /**
     * Convert JTS MultiLineString to WKB byte array.
     *
     * @param multiLineString the MultiLineString to convert
     * @return WKB byte array with SRID prefix, or null if multiLineString is null
     */
    public static byte[] toWkbBytes(MultiLineString multiLineString) {
        return encode(multiLineString);
    }

    /**
     * Parse WKB hex string to JTS MultiLineString.
     *
     * @param wkbHex the WKB hex string
     * @return JTS MultiLineString, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a MultiLineString geometry
     */
    public static MultiLineString fromWkbAsMultiLineString(String wkbHex) {
        return fromWkbAs(wkbHex, MultiLineString.class, "MultiLineString");
    }

    // ==================== MultiPolygon Conversion ====================

    /**
     * Convert JTS MultiPolygon to WKB hex string.
     *
     * @param multiPolygon the MultiPolygon to convert
     * @return WKB hex string with SRID prefix, or null if multiPolygon is null
     */
    public static String toWkb(MultiPolygon multiPolygon) {
        return toHex(multiPolygon);
    }

    /**
     * Convert JTS MultiPolygon to WKB byte array.
     *
     * @param multiPolygon the MultiPolygon to convert
     * @return WKB byte array with SRID prefix, or null if multiPolygon is null
     */
    public static byte[] toWkbBytes(MultiPolygon multiPolygon) {
        return encode(multiPolygon);
    }

    /**
     * Parse WKB hex string to JTS MultiPolygon.
     *
     * @param wkbHex the WKB hex string
     * @return JTS MultiPolygon, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a MultiPolygon geometry
     */
    public static MultiPolygon fromWkbAsMultiPolygon(String wkbHex) {
        return fromWkbAs(wkbHex, MultiPolygon.class, "MultiPolygon");
    }

    // ==================== GeometryCollection Conversion ====================

    /**
     * Convert JTS GeometryCollection to WKB hex string.
     *
     * @param geometryCollection the GeometryCollection to convert
     * @return WKB hex string with SRID prefix, or null if geometryCollection is null
     */
    public static String toWkb(GeometryCollection geometryCollection) {
        return toHex(geometryCollection);
    }

    /**
     * Convert JTS GeometryCollection to WKB byte array.
     *
     * @param geometryCollection the GeometryCollection to convert
     * @return WKB byte array with SRID prefix, or null if geometryCollection is null
     */
    public static byte[] toWkbBytes(GeometryCollection geometryCollection) {
        return encode(geometryCollection);
    }

    /**
     * Parse WKB hex string to JTS GeometryCollection (including multi-geometries).
     *
     * @param wkbHex the WKB hex string
     * @return JTS GeometryCollection, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a GeometryCollection geometry
     */
    public static GeometryCollection fromWkbAsGeometryCollection(String wkbHex) {
        return fromWkbAs(wkbHex, GeometryCollection.class, "GeometryCollection");
    }

    // ==================== Generic Conversion ====================

    /**
     * Convert any JTS Geometry to WKB hex string.
     *
     * @param geometry the Geometry to convert
     * @return WKB hex string with SRID prefix, or null if geometry is null
     * @throws IllegalArgumentException if geometry type is not supported
     */
    public static String toWkb(Geometry geometry) {
        return toHex(geometry);
    }

    /**
     * Convert any JTS Geometry to WKB byte array.
     *
     * @param geometry the Geometry to convert
     * @return WKB byte array with SRID prefix, or null if geometry is null
     * @throws IllegalArgumentException if geometry type is not supported
     */
    public static byte[] toWkbBytes(Geometry geometry) {
        return encode(geometry);
    }

    /**
     * Parse WKB hex string (SRID prefix + WKB, case-insensitive) to JTS Geometry.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Geometry, or null if input is null/empty
     * @throws WkbParseException if parsing fails (the original failure is the cause)
     */
    public static Geometry fromWkb(String wkbHex) {
        if (wkbHex == null || wkbHex.isEmpty()) {
            return null;
        }
        try {
            return WkbSupport.readSridPrefixed(WkbSupport.hexToBytes(wkbHex));
        } catch (IllegalArgumentException e) {
            throw new WkbParseException("Failed to parse WKB string: " + e.getMessage(), wkbHex, e);
        }
    }

    // ==================== Thread Safety ====================

    /**
     * Formerly released a thread-local WKB reader. Readers are now created per call, so there is
     * nothing to clean up.
     *
     * @deprecated no-op; WkbUtil no longer keeps thread-local state.
     */
    @Deprecated
    public static void cleanupThreadLocal() {
        // nothing to release
    }

    /**
     * Get the default SRID value.
     *
     * @return default SRID (4326)
     */
    public static int getDefaultSrid() {
        return DEFAULT_SRID;
    }

    // ==================== Private Helper Methods ====================

    private static byte[] encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return WkbSupport.writeSridPrefixed(geometry, WkbSupport.effectiveSrid(geometry));
    }

    private static String toHex(Geometry geometry) {
        byte[] bytes = encode(geometry);
        return bytes == null ? null : WkbSupport.toHex(bytes, true);
    }

    private static <G extends Geometry> G fromWkbAs(String wkbHex, Class<G> type, String typeName) {
        Geometry geometry = fromWkb(wkbHex);
        if (geometry == null) {
            return null;
        }
        if (type.isInstance(geometry)) {
            return type.cast(geometry);
        }
        throw new IllegalArgumentException("WKB string is not a " + typeName + " geometry, got: "
            + geometry.getGeometryType());
    }
}
