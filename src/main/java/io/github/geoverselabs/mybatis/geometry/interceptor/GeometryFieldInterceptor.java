package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis interceptor that wraps geometry columns of SELECT statements with the database
 * function the {@link GeometryHandlerStrategy} reads them through ({@code HEX(location) AS
 * location} on MySQL/MariaDB, a hex EWKB expression on PostGIS).
 *
 * <p>The geometry TypeHandlers read raw geometry columns themselves, so this interceptor is
 * optional. When it is registered, every SELECT statement is examined:</p>
 * <ol>
 *   <li>The entity is resolved from the statement's result map type, or from the mapper's
 *       {@code BaseMapper<T>} (see {@link GeometryFieldResolver} for how geometry columns are
 *       found). The lookup is cached per statement id. Statements whose rows are not mapped onto
 *       the entity ({@code selectMaps}, DTO results) are only rewritten when their first FROM
 *       table is the entity's table.</li>
 *   <li>Only the top-level select list is rewritten by {@link GeometrySqlRewriter}; subqueries,
 *       {@code WITH} and {@code UNION} statements are left unchanged.</li>
 *   <li>Failures never break the query: the original SQL is executed and the problem is logged
 *       once per statement (at WARN, details at DEBUG).</li>
 * </ol>
 *
 * <p>Registered in {@code mybatis-config.xml}, the plugin accepts the property
 * {@value #DATABASE_TYPE_PROPERTY} ({@code MYSQL} or {@code POSTGRESQL}) to pin the strategy;
 * without it, and with the no-arg constructor, {@link GeometryStrategyFactory#getDefaultStrategy()}
 * is consulted on every statement:</p>
 * <pre>{@code
 * <plugins>
 *   <plugin interceptor="io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor">
 *     <property name="databaseType" value="POSTGRESQL"/>
 *   </plugin>
 * </plugins>
 * }</pre>
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class GeometryFieldInterceptor implements Interceptor {

    /** Plugin property that pins the database type ({@code MYSQL} or {@code POSTGRESQL}). */
    public static final String DATABASE_TYPE_PROPERTY = "databaseType";

    private static final Logger log = LoggerFactory.getLogger(GeometryFieldInterceptor.class);

    /** Statements whose failure was already logged at WARN; bounded so it cannot grow forever. */
    private static final int MAX_REPORTED_FAILURES = 1024;

    private final GeometryFieldResolver fieldResolver;
    private final StatementEntityResolver entityResolver = new StatementEntityResolver();
    private final Set<String> reportedFailures = ConcurrentHashMap.newKeySet();
    private volatile GeometrySqlRewriter sqlRewriter;

    /**
     * Create an interceptor that uses {@link GeometryStrategyFactory#getDefaultStrategy()},
     * resolved on every statement (so a strategy configured after the plugin was created, for
     * example by Spring Boot auto-configuration, is honoured).
     */
    public GeometryFieldInterceptor() {
        this(new GeometryFieldResolver(), new GeometrySqlRewriter());
    }

    /**
     * Create interceptor with specified strategy.
     *
     * @param strategy the geometry handler strategy; null behaves like the no-arg constructor
     */
    public GeometryFieldInterceptor(GeometryHandlerStrategy strategy) {
        this(new GeometryFieldResolver(), new GeometrySqlRewriter(strategy));
    }

    /**
     * Create interceptor with injected components for testability.
     *
     * @param fieldResolver the field resolver for scanning entity metadata (null for a new one)
     * @param sqlRewriter   the SQL rewriter for geometry column wrapping (null for one using the
     *                      default strategy)
     */
    public GeometryFieldInterceptor(GeometryFieldResolver fieldResolver, GeometrySqlRewriter sqlRewriter) {
        this.fieldResolver = fieldResolver != null ? fieldResolver : new GeometryFieldResolver();
        this.sqlRewriter = sqlRewriter != null ? sqlRewriter : new GeometrySqlRewriter();
    }

    /**
     * Rewrite the SQL of SELECT statements, then continue with the invocation. Problems while
     * rewriting are logged and the original SQL is used.
     *
     * @param invocation the {@code StatementHandler.prepare} invocation
     * @return the result of the invocation
     * @throws Throwable whatever the invocation throws
     */
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = null;
        try {
            PluginUtils.MPStatementHandler handler =
                PluginUtils.mpStatementHandler((StatementHandler) invocation.getTarget());
            statement = handler.mappedStatement();
            if (statement != null && statement.getSqlCommandType() == SqlCommandType.SELECT) {
                rewrite(statement, handler.boundSql());
            }
        } catch (RuntimeException | LinkageError e) {
            reportFailure(statement, e);
        }
        return invocation.proceed();
    }

    private void rewrite(MappedStatement statement, BoundSql boundSql) {
        if (boundSql == null) {
            return;
        }
        String sql = boundSql.getSql();
        StatementEntityResolver.Target target = entityResolver.resolve(statement);
        if (sql == null || target == null) {
            return;
        }
        GeometryColumns columns = fieldResolver.resolve(target.entityClass());
        if (columns.isEmpty()) {
            return;
        }
        String rewritten = sqlRewriter.rewrite(sql, columns, !target.resultType());
        if (!sql.equals(rewritten)) {
            if (log.isDebugEnabled()) {
                log.debug("Geometry columns wrapped for {}:\n  original: {}\n  rewritten: {}",
                    statement.getId(), sql, rewritten);
            }
            PluginUtils.mpBoundSql(boundSql).sql(rewritten);
        }
    }

    private void reportFailure(MappedStatement statement, Throwable e) {
        String id = statement == null ? "<unknown statement>" : statement.getId();
        boolean first = reportedFailures.size() < MAX_REPORTED_FAILURES && reportedFailures.add(String.valueOf(id));
        if (first) {
            log.warn("Could not wrap geometry columns of {}; executing the original SQL: {}", id, e.toString());
        }
        log.debug("Geometry column wrapping failed for {}", id, e);
    }

    /**
     * Wrap only {@code StatementHandler} targets.
     *
     * @param target the object to wrap
     * @return the target or a proxy around it
     */
    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    /**
     * Configure the plugin. Supported property: {@value #DATABASE_TYPE_PROPERTY}
     * ({@code MYSQL}, {@code MARIADB}, {@code POSTGRESQL}, {@code POSTGIS}; case-insensitive),
     * which pins the strategy instead of resolving the default one per statement.
     *
     * @param properties the plugin properties (may be null)
     * @throws IllegalArgumentException for an unknown database type
     */
    @Override
    public void setProperties(Properties properties) {
        if (properties == null) {
            return;
        }
        String value = properties.getProperty(DATABASE_TYPE_PROPERTY);
        if (value == null || value.isBlank()) {
            return;
        }
        DatabaseType type = parseDatabaseType(value);
        log.debug("GeometryFieldInterceptor pinned to database type {}", type);
        this.sqlRewriter = new GeometrySqlRewriter(GeometryStrategyFactory.getStrategy(type));
    }

    static DatabaseType parseDatabaseType(String value) {
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "MYSQL", "MARIADB" -> DatabaseType.MYSQL;
            case "POSTGRESQL", "POSTGRES", "POSTGIS" -> DatabaseType.POSTGRESQL;
            default -> throw new IllegalArgumentException("Unsupported " + DATABASE_TYPE_PROPERTY + " '" + value
                + "' for GeometryFieldInterceptor; expected MYSQL or POSTGRESQL");
        };
    }

    /**
     * Clear the cached entity metadata, statement lookups and rewritten SQL of this interceptor,
     * for example after entity classes were reloaded.
     */
    public void clearCaches() {
        fieldResolver.clearCache();
        entityResolver.clear();
        sqlRewriter.clearCache();
        reportedFailures.clear();
    }

    /**
     * Formerly documented as clearing the field caches, but it never did anything: each
     * interceptor owns its caches.
     *
     * @deprecated no-op; use {@link #clearCaches()} on the interceptor instance
     */
    @Deprecated
    public static void clearCache() {
        // no-op: caches belong to interceptor instances, see clearCaches()
    }

    GeometrySqlRewriter sqlRewriter() {
        return sqlRewriter;
    }
}
