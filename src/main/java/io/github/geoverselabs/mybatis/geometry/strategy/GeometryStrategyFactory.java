package io.github.geoverselabs.mybatis.geometry.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching GeometryHandlerStrategy instances.
 * Supports auto-detection of database type from DataSource.
 */
public final class GeometryStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(GeometryStrategyFactory.class);

    /** Cached strategy instances */
    private static final Map<DatabaseType, GeometryHandlerStrategy> STRATEGY_CACHE =
        new ConcurrentHashMap<>();

    /** Default strategy (MySQL) */
    private static volatile GeometryHandlerStrategy defaultStrategy;

    private GeometryStrategyFactory() {
        // Utility class, prevent instantiation
    }

    /**
     * Get strategy for the specified database type.
     *
     * @param databaseType the database type
     * @return the corresponding strategy
     */
    public static GeometryHandlerStrategy getStrategy(DatabaseType databaseType) {
        return STRATEGY_CACHE.computeIfAbsent(databaseType, type -> {
            return switch (type) {
                case MYSQL -> new MySQLGeometryStrategy();
                case POSTGRESQL -> new PostGISGeometryStrategy();
            };
        });
    }

    /**
     * Get the default strategy. If not set, returns MySQL strategy as fallback.
     * Uses double-check locking for fallback initialization only.
     *
     * @return the default strategy
     */
    public static GeometryHandlerStrategy getDefaultStrategy() {
        GeometryHandlerStrategy s = defaultStrategy; // volatile read
        if (s == null) {
            synchronized (GeometryStrategyFactory.class) {
                s = defaultStrategy;
                if (s == null) {
                    s = getStrategy(DatabaseType.MYSQL);
                    defaultStrategy = s;
                }
            }
        }
        return s;
    }

    /**
     * Set the default strategy. Uses volatile write semantics to guarantee
     * immediate visibility to all threads.
     *
     * @param strategy the strategy to use as default
     */
    public static void setDefaultStrategy(GeometryHandlerStrategy strategy) {
        defaultStrategy = strategy; // volatile write, no synchronized needed
    }

    /**
     * Auto-detect database type from DataSource and return appropriate strategy.
     *
     * @param dataSource the DataSource to detect from
     * @return the detected strategy, or MySQL strategy as fallback
     */
    public static GeometryHandlerStrategy detectStrategy(DataSource dataSource) {
        DatabaseType dbType = detectDatabaseType(dataSource);
        return getStrategy(dbType);
    }

    /**
     * Detect database type from DataSource.
     *
     * @param dataSource the DataSource to detect from
     * @return the detected database type, or MYSQL as fallback
     */
    public static DatabaseType detectDatabaseType(DataSource dataSource) {
        if (dataSource == null) {
            log.warn("DataSource is null, defaulting to MySQL");
            return DatabaseType.MYSQL;
        }

        try (Connection conn = dataSource.getConnection()) {
            String url = conn.getMetaData().getURL();

            if (url != null) {
                String lowerUrl = url.toLowerCase();

                if (lowerUrl.contains("mysql") || lowerUrl.contains("mariadb")) {
                    log.debug("Detected MySQL database from URL: {}", url);
                    return DatabaseType.MYSQL;
                }

                if (lowerUrl.contains("postgresql") || lowerUrl.contains("postgres")) {
                    log.debug("Detected PostgreSQL database from URL: {}", url);
                    return DatabaseType.POSTGRESQL;
                }
            }

            // Try to detect from driver name
            String driverName = conn.getMetaData().getDriverName();
            if (driverName != null) {
                String lowerDriver = driverName.toLowerCase();

                if (lowerDriver.contains("mysql") || lowerDriver.contains("mariadb")) {
                    log.debug("Detected MySQL database from driver: {}", driverName);
                    return DatabaseType.MYSQL;
                }

                if (lowerDriver.contains("postgresql") || lowerDriver.contains("postgres")) {
                    log.debug("Detected PostgreSQL database from driver: {}", driverName);
                    return DatabaseType.POSTGRESQL;
                }
            }

        } catch (SQLException e) {
            log.warn("Failed to detect database type from DataSource: {}", e.getMessage());
        }

        log.warn("Could not detect database type, defaulting to MySQL");
        return DatabaseType.MYSQL;
    }

    /**
     * Detect database type from JDBC URL string.
     *
     * @param jdbcUrl the JDBC URL
     * @return the detected database type, or MYSQL as fallback
     */
    public static DatabaseType detectDatabaseType(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            log.warn("JDBC URL is null or empty, defaulting to MySQL");
            return DatabaseType.MYSQL;
        }

        String lowerUrl = jdbcUrl.toLowerCase();

        if (lowerUrl.contains("mysql") || lowerUrl.contains("mariadb")) {
            log.debug("Detected MySQL database from URL: {}", jdbcUrl);
            return DatabaseType.MYSQL;
        }

        if (lowerUrl.contains("postgresql") || lowerUrl.contains("postgres")) {
            log.debug("Detected PostgreSQL database from URL: {}", jdbcUrl);
            return DatabaseType.POSTGRESQL;
        }

        log.warn("Could not detect database type from URL: {}, defaulting to MySQL", jdbcUrl);
        return DatabaseType.MYSQL;
    }

    /**
     * Clear the strategy cache.
     * Useful for testing or reconfiguration.
     */
    public static void clearCache() {
        STRATEGY_CACHE.clear();
        defaultStrategy = null;
    }
}
