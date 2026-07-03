package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.PostGISWkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException;
import org.locationtech.jts.geom.Geometry;

/**
 * PostgreSQL/PostGIS-specific geometry handling strategy.
 * Uses hex WKB format for geometry data exchange.
 *
 * <p><strong>INSERT/UPDATE Operations:</strong></p>
 * <ul>
 *   <li>Converts JTS Geometry to hex WKB string</li>
 *   <li>Uses ps.setObject(hexString) to pass data</li>
 *   <li>PostGIS directly recognizes hex WKB format without ST functions</li>
 *   <li>Example: '0101000000000000000000F03F000000000000F03F' for POINT(1 1)</li>
 * </ul>
 *
 * <p><strong>SELECT Operations:</strong></p>
 * <ul>
 *   <li>Uses encode(ST_AsBinary(column), 'hex') to read geometry as hex string</li>
 *   <li>Returns standard WKB format (not EWKB)</li>
 *   <li>Compatible with WkbUtil parser</li>
 * </ul>
 *
 * <p><strong>Advantages:</strong></p>
 * <ul>
 *   <li>No need for ST_GeomFromWKB or ST_GeomFromText functions</li>
 *   <li>Simplified SQL generation</li>
 *   <li>Better performance (reduced function call overhead)</li>
 *   <li>No additional PostGIS JDBC dependencies required</li>
 * </ul>
 */
public class PostGISGeometryStrategy implements GeometryHandlerStrategy {

    private final WkbCodec codec = new PostGISWkbCodec();

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    public String wrapColumnForSelect(String columnName) {
        // PostGIS: Build the format expected by WkbUtil.fromWkb(): 4-byte LE SRID + standard WKB
        //
        // ST_SRID returns integer SRID (e.g., 4326 = 0x000010E6)
        // We need it as 4-byte little-endian hex: E6100000
        // Strategy: use lpad(to_hex(srid), 8, '0') to get 8-char big-endian hex,
        //           then reverse byte pairs for little-endian
        //
        // Example: SRID 4326 鈫?to_hex = '10e6' 鈫?lpad = '000010e6'
        //          鈫?reverse: 'e6' + '10' + '00' + '00' = 'e6100000'
        String simpleCol = extractSimpleColumnName(columnName);
        String sridHex = "lpad(to_hex(ST_SRID(" + columnName + ")), 8, '0')";
        String sridLE = "substr(" + sridHex + ",7,2)||substr(" + sridHex + ",5,2)||substr(" + sridHex + ",3,2)||substr(" + sridHex + ",1,2)";
        String wkbHex = "encode(ST_AsBinary(" + columnName + "), 'hex')";
        return "(" + sridLE + " || " + wkbHex + ") AS " + simpleCol;
    }

    /**
     * Extract simple column name from potentially qualified name (e.g., "t.location" 鈫?"location").
     */
    private String extractSimpleColumnName(String columnName) {
        int dotIndex = columnName.lastIndexOf('.');
        return dotIndex >= 0 ? columnName.substring(dotIndex + 1) : columnName;
    }

    @Override
    public String getGeometryInputFunction() {
        // PostGIS can directly accept hex WKB string without ST function wrapping
        // The geometry column automatically recognizes hex WKB format
        // Example hex WKB: '0101000000000000000000F03F000000000000F03F' represents POINT(1 1)
        // No need for ST_GeomFromWKB(decode(?, 'hex')) wrapper
        return "?";
    }

    @Override
    public Object convertForDatabase(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return codec.encode(geometry);
    }

    @Override
    public Geometry parseFromDatabase(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        try {
            return codec.decode(dbValue);
        } catch (IllegalArgumentException e) {
            throw new GeometryConversionException(
                "Unexpected database value type",
                "Geometry",
                dbValue.getClass().getName()
            );
        }
    }
}