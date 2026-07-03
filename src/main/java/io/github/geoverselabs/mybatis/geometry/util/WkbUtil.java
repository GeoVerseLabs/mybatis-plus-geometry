package io.github.geoverselabs.mybatis.geometry.util;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * Utility class for WKB (Well-Known Binary) format conversion.
 * Provides thread-safe conversion between JTS geometry objects and WKB format.
 *
 * <p>WKB format used includes SRID prefix (4 bytes) followed by standard WKB data.</p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * Point point = geometryFactory.createPoint(new Coordinate(121.5, 31.2));
 * String wkb = WkbUtil.toWkb(point);
 * Point restored = WkbUtil.fromWkbAsPoint(wkb);
 * }</pre>
 */
public final class WkbUtil {

    private static final Logger log = LoggerFactory.getLogger(WkbUtil.class);

    /** Default SRID (WGS84) */
    public static final int DEFAULT_SRID = 4326;

    /** ThreadLocal WKBReader for thread-safe parsing */
    private static final ThreadLocal<WKBReader> WKB_READER = ThreadLocal.withInitial(WKBReader::new);

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
        MULTIPOLYGON(6);

        private final int code;

        GeometryType(int code) {
            this.code = code;
        }

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
        if (point == null) {
            return null;
        }
        return pointToWkb(point.getX(), point.getY(), getSrid(point));
    }

    /**
     * Convert JTS Point to WKB byte array.
     *
     * @param point the Point to convert
     * @return WKB byte array with SRID prefix, or null if point is null
     */
    public static byte[] toWkbBytes(Point point) {
        if (point == null) {
            return null;
        }
        try {
            String hexString = toWkb(point);
            return HexFormat.of().parseHex(hexString);
        } catch (Exception e) {
            log.error("Failed to convert Point to WKB bytes: {}", e.getMessage());
            throw new RuntimeException("Failed to convert Point to WKB bytes", e);
        }
    }

    /**
     * Parse WKB hex string to JTS Point.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Point, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a Point geometry
     */
    public static Point fromWkbAsPoint(String wkbHex) {
        Geometry geometry = fromWkb(wkbHex);
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof Point point) {
            return point;
        }
        throw new IllegalArgumentException("WKB string is not a Point geometry, got: " +
            geometry.getGeometryType());
    }

    // ==================== LineString Conversion ====================

    /**
     * Convert JTS LineString to WKB hex string.
     *
     * @param lineString the LineString to convert
     * @return WKB hex string with SRID prefix, or null if lineString is null
     */
    public static String toWkb(LineString lineString) {
        if (lineString == null) {
            return null;
        }
        Coordinate[] coordinates = lineString.getCoordinates();
        double[][] coords = new double[coordinates.length][2];
        for (int i = 0; i < coordinates.length; i++) {
            coords[i][0] = coordinates[i].x;
            coords[i][1] = coordinates[i].y;
        }
        return lineStringToWkb(coords, getSrid(lineString));
    }

    /**
     * Convert JTS LineString to WKB byte array.
     *
     * @param lineString the LineString to convert
     * @return WKB byte array with SRID prefix, or null if lineString is null
     */
    public static byte[] toWkbBytes(LineString lineString) {
        if (lineString == null) {
            return null;
        }
        try {
            String hexString = toWkb(lineString);
            return HexFormat.of().parseHex(hexString);
        } catch (Exception e) {
            log.error("Failed to convert LineString to WKB bytes: {}", e.getMessage());
            throw new RuntimeException("Failed to convert LineString to WKB bytes", e);
        }
    }

    /**
     * Parse WKB hex string to JTS LineString.
     *
     * @param wkbHex the WKB hex string
     * @return JTS LineString, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a LineString geometry
     */
    public static LineString fromWkbAsLineString(String wkbHex) {
        Geometry geometry = fromWkb(wkbHex);
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof LineString lineString) {
            return lineString;
        }
        throw new IllegalArgumentException("WKB string is not a LineString geometry, got: " +
            geometry.getGeometryType());
    }


    // ==================== Polygon Conversion ====================

    /**
     * Convert JTS Polygon to WKB hex string.
     *
     * @param polygon the Polygon to convert
     * @return WKB hex string with SRID prefix, or null if polygon is null
     */
    public static String toWkb(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        int srid = getSrid(polygon);
        int numRings = 1 + polygon.getNumInteriorRing();

        // Calculate total points across all rings
        int totalPoints = polygon.getExteriorRing().getNumPoints();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            totalPoints += polygon.getInteriorRingN(i).getNumPoints();
        }

        // SRID(4) + byte_order(1) + type(4) + numRings(4) + per-ring: numPoints(4) + points(16*n)
        int size = 4 + 1 + 4 + 4 + numRings * 4 + totalPoints * 16;
        ByteBuffer buffer = ByteBuffer.allocate(size);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.putInt(srid);
        buffer.put((byte) 1); // little-endian
        buffer.putInt(GeometryType.POLYGON.getCode());
        buffer.putInt(numRings);

        // Write exterior ring
        writeRingToBuffer(buffer, polygon.getExteriorRing());

        // Write interior rings (holes)
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            writeRingToBuffer(buffer, polygon.getInteriorRingN(i));
        }

        return HexFormat.of().formatHex(buffer.array()).toUpperCase();
    }

    /**
     * Convert JTS Polygon to WKB byte array.
     *
     * @param polygon the Polygon to convert
     * @return WKB byte array with SRID prefix, or null if polygon is null
     */
    public static byte[] toWkbBytes(Polygon polygon) {
        if (polygon == null) {
            return null;
        }
        try {
            String hexString = toWkb(polygon);
            return HexFormat.of().parseHex(hexString);
        } catch (Exception e) {
            log.error("Failed to convert Polygon to WKB bytes: {}", e.getMessage());
            throw new RuntimeException("Failed to convert Polygon to WKB bytes", e);
        }
    }

    /**
     * Parse WKB hex string to JTS Polygon.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Polygon, or null if input is null/empty
     * @throws IllegalArgumentException if WKB is not a Polygon geometry
     */
    public static Polygon fromWkbAsPolygon(String wkbHex) {
        Geometry geometry = fromWkb(wkbHex);
        if (geometry == null) {
            return null;
        }
        if (geometry instanceof Polygon polygon) {
            return polygon;
        }
        throw new IllegalArgumentException("WKB string is not a Polygon geometry, got: " +
            geometry.getGeometryType());
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
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof Point point) {
            return toWkb(point);
        } else if (geometry instanceof LineString lineString) {
            return toWkb(lineString);
        } else if (geometry instanceof Polygon polygon) {
            return toWkb(polygon);
        } else {
            throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
        }
    }

    /**
     * Convert any JTS Geometry to WKB byte array.
     *
     * @param geometry the Geometry to convert
     * @return WKB byte array with SRID prefix, or null if geometry is null
     */
    public static byte[] toWkbBytes(Geometry geometry) {
        if (geometry == null) {
            return null;
        }

        if (geometry instanceof Point point) {
            return toWkbBytes(point);
        } else if (geometry instanceof LineString lineString) {
            return toWkbBytes(lineString);
        } else if (geometry instanceof Polygon polygon) {
            return toWkbBytes(polygon);
        } else {
            throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
        }
    }

    /**
     * Parse WKB hex string to JTS Geometry.
     *
     * @param wkbHex the WKB hex string
     * @return JTS Geometry, or null if input is null/empty
     * @throws RuntimeException if parsing fails
     */
    public static Geometry fromWkb(String wkbHex) {
        if (wkbHex == null || wkbHex.isEmpty()) {
            return null;
        }

        try {
            byte[] wkbBytes = HexFormat.of().parseHex(wkbHex);

            // Skip SRID prefix (4 bytes)
            byte[] wkbWithoutSrid = Arrays.copyOfRange(wkbBytes, 4, wkbBytes.length);

            // Parse using thread-safe WKBReader
            Geometry geometry = WKB_READER.get().read(wkbWithoutSrid);

            // Read SRID from original bytes
            ByteBuffer buffer = ByteBuffer.wrap(wkbBytes);
            buffer.order(ByteOrder.LITTLE_ENDIAN);
            int srid = buffer.getInt();
            geometry.setSRID(srid);

            return geometry;
        } catch (Exception e) {
            String prefix = wkbHex.length() > 20 ? wkbHex.substring(0, 20) + "..." : wkbHex;
            log.error("Failed to parse WKB string: {}... - {}", prefix, e.getMessage());
            throw new RuntimeException("Failed to parse WKB string: " + prefix, e);
        }
    }


    // ==================== Thread Safety ====================

    /**
     * Clean up ThreadLocal resources.
     * Should be called when thread is about to be destroyed or reused.
     */
    public static void cleanupThreadLocal() {
        WKB_READER.remove();
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

    private static int getSrid(Geometry geometry) {
        return geometry.getSRID() == 0 ? DEFAULT_SRID : geometry.getSRID();
    }

    private static String pointToWkb(double longitude, double latitude, int srid) {
        try {
            ByteBuffer buffer = ByteBuffer.allocate(25);
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // SRID (4 bytes)
            buffer.putInt(srid);

            // Byte order (1 byte): 1 = little-endian
            buffer.put((byte) 1);

            // Geometry type (4 bytes)
            buffer.putInt(GeometryType.POINT.getCode());

            // Coordinates (8 bytes + 8 bytes)
            buffer.putDouble(longitude);
            buffer.putDouble(latitude);

            return HexFormat.of().formatHex(buffer.array()).toUpperCase();
        } catch (Exception e) {
            log.error("Failed to create Point WKB", e);
            throw new RuntimeException("Failed to create Point WKB", e);
        }
    }

    private static String lineStringToWkb(double[][] coordinates, int srid) {
        try {
            int size = 9 + (coordinates.length * 16); // Base 9 bytes + 16 bytes per point
            ByteBuffer buffer = ByteBuffer.allocate(size + 4); // Add 4 bytes for SRID
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // SRID
            buffer.putInt(srid);

            // Byte order
            buffer.put((byte) 1);

            // Geometry type
            buffer.putInt(GeometryType.LINESTRING.getCode());

            // Number of points
            buffer.putInt(coordinates.length);

            // Write all coordinates
            for (double[] coord : coordinates) {
                buffer.putDouble(coord[0]);
                buffer.putDouble(coord[1]);
            }

            return HexFormat.of().formatHex(buffer.array()).toUpperCase();
        } catch (Exception e) {
            log.error("Failed to create LineString WKB", e);
            throw new RuntimeException("Failed to create LineString WKB", e);
        }
    }

    private static void writeRingToBuffer(ByteBuffer buffer, LineString ring) {
        Coordinate[] coords = ring.getCoordinates();
        buffer.putInt(coords.length);
        for (Coordinate c : coords) {
            buffer.putDouble(c.x);
            buffer.putDouble(c.y);
        }
    }

    private static String polygonToWkb(double[][] coordinates, int srid) {
        try {
            // Ensure polygon is closed
            if (!Arrays.equals(coordinates[0], coordinates[coordinates.length - 1])) {
                coordinates = Arrays.copyOf(coordinates, coordinates.length + 1);
                coordinates[coordinates.length - 1] = coordinates[0];
            }

            int size = 13 + (coordinates.length * 16); // Base 13 bytes + 16 bytes per point
            ByteBuffer buffer = ByteBuffer.allocate(size + 4); // Add 4 bytes for SRID
            buffer.order(ByteOrder.LITTLE_ENDIAN);

            // SRID
            buffer.putInt(srid);

            // Byte order
            buffer.put((byte) 1);

            // Geometry type
            buffer.putInt(GeometryType.POLYGON.getCode());

            // Number of rings (1 for exterior ring only)
            buffer.putInt(1);

            // Number of points
            buffer.putInt(coordinates.length);

            // Write all coordinates
            for (double[] coord : coordinates) {
                buffer.putDouble(coord[0]);
                buffer.putDouble(coord[1]);
            }

            return HexFormat.of().formatHex(buffer.array()).toUpperCase();
        } catch (Exception e) {
            log.error("Failed to create Polygon WKB", e);
            throw new RuntimeException("Failed to create Polygon WKB", e);
        }
    }
}
