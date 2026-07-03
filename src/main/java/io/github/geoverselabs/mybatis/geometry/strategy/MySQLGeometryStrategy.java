package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.MySQLWkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException;
import org.locationtech.jts.geom.Geometry;

/**
 * MySQL-specific geometry handling strategy.
 * Uses WKB binary format for storage and HEX() function for reading.
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
     * Extract simple column name from potentially qualified name (e.g., "t.location" 鈫?"location").
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