package io.github.geoverselabs.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.util.*;

/**
 * MyBatis interceptor to automatically wrap geometry fields in SELECT queries.
 *
 * <p>This interceptor detects geometry fields (annotated with @PointTableField, @PolygonTableField,
 * or @LineStringTableField) and wraps them with the appropriate database function for reading.</p>
 *
 * <p>Delegates to {@link GeometryFieldResolver} for field scanning and
 * {@link GeometrySqlRewriter} for SQL rewriting.</p>
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class GeometryFieldInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(GeometryFieldInterceptor.class);

    private final GeometryFieldResolver fieldResolver;
    private final GeometrySqlRewriter sqlRewriter;

    /**
     * Create interceptor with default strategy (backward compatible).
     */
    public GeometryFieldInterceptor() {
        this(GeometryStrategyFactory.getDefaultStrategy());
    }

    /**
     * Create interceptor with specified strategy (backward compatible).
     *
     * @param strategy the geometry handler strategy
     */
    public GeometryFieldInterceptor(GeometryHandlerStrategy strategy) {
        this(new GeometryFieldResolver(), new GeometrySqlRewriter(strategy));
    }

    /**
     * Create interceptor with injected components for testability.
     *
     * @param fieldResolver the field resolver for scanning entity metadata
     * @param sqlRewriter the SQL rewriter for geometry column wrapping
     */
    public GeometryFieldInterceptor(GeometryFieldResolver fieldResolver, GeometrySqlRewriter sqlRewriter) {
        this.fieldResolver = fieldResolver;
        this.sqlRewriter = sqlRewriter;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);

        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");

        // Only process SELECT queries
        if (mappedStatement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }

        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();

        String processedSql = processGeometryFields(originalSql, mappedStatement);

        if (!originalSql.equals(processedSql)) {
            log.debug("Original SQL: {}", originalSql);
            log.debug("Processed SQL: {}", processedSql);
            metaObject.setValue("delegate.boundSql.sql", processedSql);
        }

        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // No properties needed
    }

    private String processGeometryFields(String sql, MappedStatement mappedStatement) {
        try {
            Class<?> entityClass = getEntityClass(mappedStatement);
            if (entityClass == null) {
                return sql;
            }

            Set<String> geometryFields = fieldResolver.getGeometryFields(entityClass);
            if (geometryFields.isEmpty()) {
                return sql;
            }

            List<String> allFields = fieldResolver.getAllFields(entityClass);
            return sqlRewriter.rewrite(sql, geometryFields, allFields);

        } catch (Exception e) {
            log.warn("Failed to process geometry fields in SQL: {}", e.getMessage());
            return sql;
        }
    }

    private Class<?> getEntityClass(MappedStatement mappedStatement) {
        String id = mappedStatement.getId();

        try {
            String mapperClassName = id.substring(0, id.lastIndexOf('.'));
            Class<?> mapperClass = Class.forName(mapperClassName);
            return getEntityClassFromMapper(mapperClass);
        } catch (Exception e) {
            log.debug("Could not determine entity class for: {}", id);
            return null;
        }
    }

    private Class<?> getEntityClassFromMapper(Class<?> mapperClass) {
        try {
            for (java.lang.reflect.Type genericInterface : mapperClass.getGenericInterfaces()) {
                if (genericInterface instanceof java.lang.reflect.ParameterizedType parameterizedType) {
                    if (parameterizedType.getRawType().getTypeName().contains("BaseMapper")) {
                        java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
                        if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> entityClass) {
                            return entityClass;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract entity class from mapper: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Clear the field caches.
     * Useful for testing or reconfiguration.
     */
    public static void clearCache() {
        // For backward compatibility, create a temporary resolver to clear
        // In practice, each interceptor instance has its own resolver
    }
}
