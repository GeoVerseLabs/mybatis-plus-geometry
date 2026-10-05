package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.PostGISWkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbSupport;
import org.locationtech.jts.geom.Geometry;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL/PostGIS-specific geometry handling strategy based on hex EWKB.
 *
 * <p><strong>INSERT/UPDATE Operations:</strong></p>
 * <ul>
 *   <li>Converts JTS Geometry to a hex EWKB string carrying the SRID (and Z when enabled)</li>
 *   <li>Uses {@code ps.setObject(hexString, Types.OTHER)}; PostGIS parses hex EWKB directly</li>
 * </ul>
 *
 * <p><strong>SELECT Operations:</strong></p>
 * <ul>
 *   <li>Reads {@code ResultSet.getString()}: the text output of a {@code geometry} column is hex
 *       EWKB, so plain columns are read without any SQL rewriting</li>
 *   <li>Also accepts {@code encode(ST_AsEWKB(col), 'hex')} (used by the optional interceptor),
 *       bytea expressions such as {@code ST_AsEWKB(col)} ({@code \x}-prefixed hex), plain WKB hex
 *       and the legacy "SRID prefix + WKB" format of earlier versions</li>
 * </ul>
 *
 * <p>No PostGIS JDBC extension is required.</p>
 */
public class PostGISGeometryStrategy implements GeometryHandlerStrategy {

    private final WkbCodec codec = new PostGISWkbCodec();

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    /**
     * Wrap a column as {@code encode(ST_AsEWKB(col), 'hex') AS alias}, where the alias is the
     * unqualified column name.
     *
     * @param columnName the column name, optionally qualified
     * @return the wrapped expression
     */
    @Override
    public String wrapColumnForSelect(String columnName) {
        return "encode(ST_AsEWKB(" + columnName + "), 'hex') AS " + extractSimpleColumnName(columnName);
    }

    /**
     * Extract simple column name from potentially qualified name (e.g., "t.location" becomes "location").
     */
    private String extractSimpleColumnName(String columnName) {
        int dotIndex = columnName.lastIndexOf('.');
        return dotIndex >= 0 ? columnName.substring(dotIndex + 1) : columnName;
    }

    @Override
    public String getGeometryInputFunction() {
        // PostGIS accepts hex (E)WKB strings for geometry parameters without a function call
        return "?";
    }

    /**
     * Encode with the geometry's SRID, or the configured default SRID when it is 0.
     *
     * @param geometry the geometry (not modified)
     * @return hex EWKB, or null for a null geometry
     * @throws io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException if encoding fails
     */
    @Override
    public Object convertForDatabase(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return CodecSupport.encode(codec, geometry, WkbSupport.effectiveSrid(geometry));
    }

    /**
     * Encode with an explicit SRID, without copying or modifying the geometry.
     *
     * @param geometry the geometry (not modified)
     * @param srid     the SRID to embed
     * @return hex EWKB, or null for a null geometry
     * @throws io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException if encoding fails
     */
    @Override
    public Object convertForDatabase(Geometry geometry, int srid) {
        return CodecSupport.encode(codec, geometry, srid);
    }

    /**
     * Decode hex or binary (E)WKB, or the legacy SRID-prefixed format.
     *
     * @param dbValue {@code String} or {@code byte[]}
     * @return the geometry, or null for null/empty input
     * @throws io.github.geoverselabs.mybatis.geometry.exception.WkbParseException if the value is malformed
     * @throws io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException for other value types
     */
    @Override
    public Geometry parseFromDatabase(Object dbValue) {
        return CodecSupport.decode(codec, dbValue);
    }

    @Override
    public Geometry read(ResultSet rs, String columnLabel) throws SQLException {
        return parseFromDatabase(rs.getString(columnLabel));
    }

    @Override
    public Geometry read(ResultSet rs, int columnIndex) throws SQLException {
        return parseFromDatabase(rs.getString(columnIndex));
    }

    @Override
    public Geometry read(CallableStatement cs, int parameterIndex) throws SQLException {
        return parseFromDatabase(cs.getString(parameterIndex));
    }
}
