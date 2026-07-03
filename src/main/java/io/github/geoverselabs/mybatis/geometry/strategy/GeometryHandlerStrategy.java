package io.github.geoverselabs.mybatis.geometry.strategy;

import org.locationtech.jts.geom.Geometry;

/**
 * Strategy interface for database-specific geometry handling.
 * Implementations provide database-specific SQL functions and conversion logic.
 * 
 * <p>This abstraction allows the library to support multiple databases
 * (MySQL, PostgreSQL/PostGIS) with different geometry handling approaches.</p>
 */
public interface GeometryHandlerStrategy {
    
    /**
     * Get the database type this strategy supports.
     *
     * @return the supported database type
     */
    DatabaseType getSupportedDatabaseType();
    
    /**
     * Wrap a geometry column for SELECT query.
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>MySQL: {@code HEX(column) AS column}</li>
     *   <li>PostGIS: {@code ST_AsHexEWKB(column) AS column}</li>
     * </ul>
     *
     * @param columnName the column name to wrap
     * @return the wrapped column expression
     */
    String wrapColumnForSelect(String columnName);
    
    /**
     * Get the SQL function for geometry input.
     * 
     * <p>Examples:</p>
     * <ul>
     *   <li>MySQL: {@code ?} (direct binary)</li>
     *   <li>PostGIS: {@code ST_GeomFromEWKB(?)}</li>
     * </ul>
     *
     * @return the SQL function placeholder
     */
    String getGeometryInputFunction();
    
    /**
     * Convert a JTS geometry to database-specific format.
     *
     * @param geometry the geometry to convert
     * @return the database-specific representation
     */
    Object convertForDatabase(Geometry geometry);
    
    /**
     * Parse geometry from database result.
     *
     * @param dbValue the value from database
     * @return the parsed JTS geometry
     */
    Geometry parseFromDatabase(Object dbValue);
}
