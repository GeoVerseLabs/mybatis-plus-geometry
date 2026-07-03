package io.github.geoverselabs.mybatis.geometry.strategy;

/**
 * Supported database types for geometry handling.
 */
public enum DatabaseType {
    
    /**
     * MySQL database with native GEOMETRY type.
     */
    MYSQL,
    
    /**
     * PostgreSQL database with PostGIS extension.
     */
    POSTGRESQL
}
