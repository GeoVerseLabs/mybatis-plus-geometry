package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.MySQLWkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbSupport;
import org.locationtech.jts.geom.Geometry;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MySQL/MariaDB-specific geometry handling strategy.
 *
 * <p>Writes the internal geometry format (4-byte little-endian SRID + WKB) with
 * {@code ps.setBytes()}. Reads use {@code ResultSet.getBytes()}, which returns the internal format
 * for a geometry column and ASCII hex for the {@code HEX(column)} expression produced by
 * {@link #wrapColumnForSelect(String)}; both are decoded.</p>
 */
public class MySQLGeometryStrategy implements GeometryHandlerStrategy {

    private final WkbCodec codec = new MySQLWkbCodec();

    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    public String wrapColumnForSelect(String columnName) {
        String alias = extractSimpleColumnName(columnName);
        return "HEX(" + columnName + ") AS " + alias;
    }

    /**
     * Extract simple column name from potentially qualified name (e.g., "t.location" becomes "location").
     * Takes the part after the last dot to ensure the alias does not contain dots.
     */
    private String extractSimpleColumnName(String columnName) {
        int dotIndex = columnName.lastIndexOf('.');
        return dotIndex >= 0 ? columnName.substring(dotIndex + 1) : columnName;
    }

    @Override
    public String getGeometryInputFunction() {
        // MySQL accepts direct binary input for GEOMETRY columns
        return "?";
    }

    /**
     * Encode with the geometry's SRID, or the configured default SRID when it is 0.
     *
     * @param geometry the geometry (not modified)
     * @return internal format bytes, or null for a null geometry
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
     * @param srid     the SRID to store
     * @return internal format bytes, or null for a null geometry
     * @throws io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException if encoding fails
     */
    @Override
    public Object convertForDatabase(Geometry geometry, int srid) {
        return CodecSupport.encode(codec, geometry, srid);
    }

    /**
     * Decode raw internal format bytes, ASCII hex bytes or a hex string.
     *
     * @param dbValue {@code byte[]} or {@code String}
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
        return parseFromDatabase(rs.getBytes(columnLabel));
    }

    @Override
    public Geometry read(ResultSet rs, int columnIndex) throws SQLException {
        return parseFromDatabase(rs.getBytes(columnIndex));
    }

    @Override
    public Geometry read(CallableStatement cs, int parameterIndex) throws SQLException {
        return parseFromDatabase(cs.getBytes(parameterIndex));
    }
}
