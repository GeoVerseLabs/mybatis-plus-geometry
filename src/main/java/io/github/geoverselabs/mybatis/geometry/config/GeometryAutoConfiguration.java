package io.github.geoverselabs.mybatis.geometry.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geoverselabs.mybatis.geometry.handler.LineStringTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.PolygonTypeHandler;
import io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor;
import io.github.geoverselabs.mybatis.geometry.jackson.GeometryJacksonModule;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Spring Boot auto-configuration for MyBatis Plus Geometry Extension.
 *
 * <p><strong>Activation Conditions:</strong></p>
 * <ul>
 *   <li>MyBatis Plus BaseMapper is on the classpath</li>
 *   <li>JTS Geometry is on the classpath</li>
 * </ul>
 *
 * <p><strong>Registered Beans:</strong></p>
 * <ul>
 *   <li><strong>GeometryHandlerStrategy</strong> - Database-specific strategy (auto-detected or configured)</li>
 *   <li><strong>PointTypeHandler</strong> - TypeHandler for Point geometry</li>
 *   <li><strong>PolygonTypeHandler</strong> - TypeHandler for Polygon geometry</li>
 *   <li><strong>LineStringTypeHandler</strong> - TypeHandler for LineString geometry</li>
 *   <li><strong>GeometryFieldInterceptor</strong> - SQL interceptor for SELECT queries (wraps geometry columns)</li>
 * </ul>
 *
 * <p><strong>Database Support:</strong></p>
 * <ul>
 *   <li><strong>MySQL:</strong> Direct WKB binary format</li>
 *   <li><strong>PostgreSQL/PostGIS:</strong> Hex WKB string format</li>
 * </ul>
 *
 * <p><strong>Configuration Properties:</strong></p>
 * <ul>
 *   <li>mybatis.geometry.database-type - Override auto-detection (MYSQL or POSTGRESQL)</li>
 *   <li>mybatis.geometry.default-srid - Default SRID for geometries (default: 4326)</li>
 *   <li>mybatis.geometry.interceptor-enabled - Enable/disable SQL interceptor (default: true)</li>
 * </ul>
 *
 * <p>Compatible with Spring Boot 2.7+ and Spring Boot 3.x</p>
 */
@AutoConfiguration
@ConditionalOnClass({BaseMapper.class, Geometry.class})
@EnableConfigurationProperties(GeometryProperties.class)
public class GeometryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GeometryAutoConfiguration.class);

    /**
     * Create GeometryHandlerStrategy bean.
     * Auto-detects database type if not configured.
     * Synchronizes the strategy to the global default so that TypeHandlers
     * created via reflection (no-arg constructor) use the same instance.
     */
    @Bean
    @ConditionalOnMissingBean
    public GeometryHandlerStrategy geometryHandlerStrategy(
            DataSource dataSource,
            GeometryProperties properties) {

        GeometryHandlerStrategy strategy;
        if (properties.getDatabaseType() != null) {
            log.info("Using configured database type: {}", properties.getDatabaseType());
            strategy = GeometryStrategyFactory.getStrategy(properties.getDatabaseType());
        } else {
            log.info("Auto-detecting database type from DataSource");
            strategy = GeometryStrategyFactory.detectStrategy(dataSource);
        }
        // Sync to global default strategy
        GeometryStrategyFactory.setDefaultStrategy(strategy);
        return strategy;
    }

    /**
     * Create GeometryFieldInterceptor bean for SELECT queries.
     * Only created when interceptor is enabled (default: true).
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "mybatis.geometry",
        name = "interceptor-enabled",
        havingValue = "true",
        matchIfMissing = true
    )
    public GeometryFieldInterceptor geometryFieldInterceptor(GeometryHandlerStrategy strategy) {
        log.info("Registering GeometryFieldInterceptor for SELECT queries");
        return new GeometryFieldInterceptor(strategy);
    }

    /**
     * Create GeometryJacksonModule bean for automatic GeoJSON serialization support.
     * Only created when Jackson ObjectMapper is on the classpath.
     * Coordinate validation is automatically enabled when default SRID is 4326 (WGS84).
     */
    @Bean
    @ConditionalOnClass(ObjectMapper.class)
    @ConditionalOnMissingBean(name = "geometryJacksonModule")
    public GeometryJacksonModule geometryJacksonModule(GeometryProperties properties) {
        boolean validate = properties.isCoordinateValidationEnabled();
        log.info("Registering GeometryJacksonModule (SRID={}, coordinateValidation={})",
            properties.getDefaultSrid(), validate);
        return new GeometryJacksonModule(validate);
    }

    /**
     * Create PointTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public PointTypeHandler pointTypeHandler(GeometryProperties properties, GeometryHandlerStrategy strategy) {
        configureGeometryFactory(properties);
        return new PointTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create PolygonTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public PolygonTypeHandler polygonTypeHandler(GeometryProperties properties, GeometryHandlerStrategy strategy) {
        return new PolygonTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create LineStringTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public LineStringTypeHandler lineStringTypeHandler(GeometryProperties properties, GeometryHandlerStrategy strategy) {
        return new LineStringTypeHandler(properties.getDefaultSrid(), strategy);
    }

    private void configureGeometryFactory(GeometryProperties properties) {
        if (properties.getDefaultSrid() != GeometryProperties.DEFAULT_SRID) {
            log.info("Configuring GeometryFactory with SRID: {}", properties.getDefaultSrid());
            GeometryFactoryProvider.setDefaultSrid(properties.getDefaultSrid());
        }
    }
}
