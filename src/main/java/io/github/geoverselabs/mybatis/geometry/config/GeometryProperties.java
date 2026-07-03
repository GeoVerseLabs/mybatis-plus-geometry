package io.github.geoverselabs.mybatis.geometry.config;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for MyBatis Plus Geometry Extension.
 *
 * <p>Example configuration in application.yml:</p>
 * <pre>{@code
 * mybatis:
 *   geometry:
 *     default-srid: 4326
 *     interceptor-enabled: true
 *     database-type: MYSQL
 * }</pre>
 */
@ConfigurationProperties(prefix = "mybatis.geometry")
public class GeometryProperties {

    /** Default SRID constant */
    public static final int DEFAULT_SRID = WkbUtil.DEFAULT_SRID;

    /** WGS84 SRID value */
    public static final int WGS84_SRID = 4326;

    /**
     * Default SRID for geometry objects without explicit SRID.
     * Default: 4326 (WGS84)
     */
    private int defaultSrid = DEFAULT_SRID;

    /**
     * Enable automatic HEX() wrapping in SELECT queries.
     * Default: true
     */
    private boolean interceptorEnabled = true;

    /**
     * Database type (auto-detected if not specified).
     * Supported values: MYSQL, POSTGRESQL
     */
    private DatabaseType databaseType;

    public int getDefaultSrid() {
        return defaultSrid;
    }

    public void setDefaultSrid(int defaultSrid) {
        this.defaultSrid = defaultSrid;
    }

    public boolean isInterceptorEnabled() {
        return interceptorEnabled;
    }

    public void setInterceptorEnabled(boolean interceptorEnabled) {
        this.interceptorEnabled = interceptorEnabled;
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    public void setDatabaseType(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    /**
     * Whether coordinate range validation should be enabled.
     * Automatically determined by defaultSrid: enabled only when SRID is 4326 (WGS84).
     */
    public boolean isCoordinateValidationEnabled() {
        return defaultSrid == WGS84_SRID;
    }
}
