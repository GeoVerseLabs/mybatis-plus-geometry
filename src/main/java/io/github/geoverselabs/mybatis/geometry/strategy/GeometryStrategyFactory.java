package io.github.geoverselabs.mybatis.geometry.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating and caching GeometryHandlerStrategy instances.
 * Supports auto-detection of database type from a DataSource, a Connection or a JDBC URL.
 *
 * <p>Detection uses {@link DatabaseMetaData#getDatabaseProductName()} first ("MySQL", "MariaDB",
 * "PostgreSQL"), then the JDBC URL scheme ({@code jdbc:mysql:}, {@code jdbc:mariadb:},
 * {@code jdbc:postgresql:}, including wrapper schemes such as {@code jdbc:p6spy:mysql:}). Host and
 * database names in the URL are never inspected. When nothing matches, MySQL is used and a warning
 * is logged.</p>
 */
public final class GeometryStrategyFactory {

    private static final Logger log = LoggerFactory.getLogger(GeometryStrategyFactory.class);

    /** Cached strategy instances */
    private static final Map<DatabaseType, GeometryHandlerStrategy> STRATEGY_CACHE =
        new ConcurrentHashMap<>();

    /** Sub-protocols whose next URL segment is a database name or mode, not a wrapped driver. */
    private static final Set<String> EMBEDDED_SUB_PROTOCOLS = Set.of("h2", "hsqldb", "derby", "sqlite");

    /** Explicitly configured default strategy, or null. */
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
        return STRATEGY_CACHE.computeIfAbsent(databaseType, type -> switch (type) {
            case MYSQL -> new MySQLGeometryStrategy();
            case POSTGRESQL -> new PostGISGeometryStrategy();
        });
    }

    /**
     * Get the default strategy. If none was set with {@link #setDefaultStrategy(GeometryHandlerStrategy)},
     * returns the MySQL strategy as fallback (without remembering it, so a later
     * {@code setDefaultStrategy} call takes effect everywhere).
     *
     * @return the default strategy
     */
    public static GeometryHandlerStrategy getDefaultStrategy() {
        GeometryHandlerStrategy s = defaultStrategy; // volatile read
        if (s != null) {
            return s;
        }
        if (log.isDebugEnabled()) {
            log.debug("No default GeometryHandlerStrategy configured, using MySQL");
        }
        return getStrategy(DatabaseType.MYSQL);
    }

    /**
     * Set the default strategy. Uses volatile write semantics to guarantee
     * immediate visibility to all threads. Handlers and interceptors created without an explicit
     * strategy read the default on every use.
     *
     * @param strategy the strategy to use as default; null restores the MySQL fallback
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
     * Detect database type from DataSource (opens and closes one connection).
     *
     * @param dataSource the DataSource to detect from
     * @return the detected database type, or MYSQL as fallback (logged at WARN)
     */
    public static DatabaseType detectDatabaseType(DataSource dataSource) {
        if (dataSource == null) {
            log.warn("DataSource is null, defaulting to MySQL");
            return DatabaseType.MYSQL;
        }
        try (Connection conn = dataSource.getConnection()) {
            DatabaseType type = detect(conn.getMetaData());
            if (type != null) {
                return type;
            }
        } catch (SQLException e) {
            log.warn("Failed to detect database type from DataSource: {}", e.getMessage());
        }
        log.warn("Could not detect database type, defaulting to MySQL");
        return DatabaseType.MYSQL;
    }

    /**
     * Detect database type from an open connection. The connection is not closed.
     *
     * @param connection the connection to inspect
     * @return the detected database type, or MYSQL as fallback (logged at WARN)
     */
    public static DatabaseType detectDatabaseType(Connection connection) {
        if (connection != null) {
            try {
                DatabaseType type = detect(connection.getMetaData());
                if (type != null) {
                    return type;
                }
            } catch (SQLException e) {
                log.warn("Failed to detect database type from Connection: {}", e.getMessage());
            }
        }
        log.warn("Could not detect database type, defaulting to MySQL");
        return DatabaseType.MYSQL;
    }

    /**
     * Detect database type from JDBC URL string. Only the URL scheme is inspected
     * ({@code jdbc:[wrapper:]mysql|mariadb|postgresql:...}).
     *
     * @param jdbcUrl the JDBC URL
     * @return the detected database type, or MYSQL as fallback (logged at WARN)
     */
    public static DatabaseType detectDatabaseType(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isEmpty()) {
            log.warn("JDBC URL is null or empty, defaulting to MySQL");
            return DatabaseType.MYSQL;
        }
        DatabaseType type = fromJdbcUrl(jdbcUrl);
        if (type != null) {
            log.debug("Detected {} database from URL scheme", type);
            return type;
        }
        log.warn("Could not detect database type from URL: {}, defaulting to MySQL", jdbcUrl);
        return DatabaseType.MYSQL;
    }

    /**
     * Clear the strategy cache and the configured default strategy.
     * Useful for testing or reconfiguration.
     */
    public static void clearCache() {
        STRATEGY_CACHE.clear();
        defaultStrategy = null;
    }

    private static DatabaseType detect(DatabaseMetaData metaData) throws SQLException {
        String productName = metaData.getDatabaseProductName();
        DatabaseType type = fromProductName(productName);
        if (type != null) {
            log.debug("Detected {} database from product name '{}'", type, productName);
            return type;
        }
        type = fromJdbcUrl(metaData.getURL());
        if (type != null) {
            log.debug("Detected {} database from URL scheme", type);
        }
        return type;
    }

    /**
     * Map a JDBC database product name.
     *
     * @param productName the value of {@link DatabaseMetaData#getDatabaseProductName()}
     * @return the database type, or null when not recognised
     */
    static DatabaseType fromProductName(String productName) {
        if (productName == null) {
            return null;
        }
        String name = productName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("mysql") || name.startsWith("mariadb")) {
            return DatabaseType.MYSQL;
        }
        if (name.startsWith("postgresql") || name.equals("postgres")) {
            return DatabaseType.POSTGRESQL;
        }
        return null;
    }

    /**
     * Map the scheme of a JDBC URL. Only the sub-protocol is inspected: the first segment after
     * {@code jdbc:}, or the second one when the first is a wrapper such as {@code p6spy},
     * {@code log4jdbc} or {@code tc} ({@code jdbc:p6spy:mysql:}, {@code jdbc:tc:postgis:}).
     * Host and database names never match.
     *
     * @param jdbcUrl the JDBC URL
     * @return the database type, or null when not recognised
     */
    static DatabaseType fromJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String url = jdbcUrl.trim().toLowerCase(Locale.ROOT);
        if (!url.startsWith("jdbc:")) {
            return null;
        }
        int end = url.length();
        for (int i = 5; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == ';' || c == '@') {
                end = i;
                break;
            }
        }
        String[] segments = url.substring(5, end).split(":", 3);
        DatabaseType type = fromSubProtocol(segments[0]);
        if (type != null || segments.length < 2 || EMBEDDED_SUB_PROTOCOLS.contains(segments[0])) {
            return type;
        }
        return fromSubProtocol(segments[1]);
    }

    private static DatabaseType fromSubProtocol(String segment) {
        switch (segment) {
            case "mysql", "mariadb":
                return DatabaseType.MYSQL;
            case "postgresql", "postgis", "pgsql":
                return DatabaseType.POSTGRESQL;
            default:
                // PostGIS JDBC driver wrappers: postgresql_postGIS, postgresql_lwgis, postgresql_autogis, postgres_jts
                return segment.startsWith("postgresql_") || segment.startsWith("postgres_")
                    ? DatabaseType.POSTGRESQL : null;
        }
    }
}
