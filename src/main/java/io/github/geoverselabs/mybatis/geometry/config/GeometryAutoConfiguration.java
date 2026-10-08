package io.github.geoverselabs.mybatis.geometry.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.geoverselabs.mybatis.geometry.handler.GeometryCollectionTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.GeometryTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.LineStringTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.MultiLineStringTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.MultiPointTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.MultiPolygonTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.PointTypeHandler;
import io.github.geoverselabs.mybatis.geometry.handler.PolygonTypeHandler;
import io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor;
import io.github.geoverselabs.mybatis.geometry.jackson.GeoJsonOptions;
import io.github.geoverselabs.mybatis.geometry.jackson.GeometryJacksonModule;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

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
 *   <li><strong>GeometryHandlerStrategy</strong> - Database-specific strategy (configured or auto-detected)</li>
 *   <li><strong>TypeHandlers</strong> - One per geometry type (Point, LineString, Polygon, MultiPoint,
 *       MultiLineString, MultiPolygon, GeometryCollection, Geometry), registered with MyBatis by Java type</li>
 *   <li><strong>GeometryJacksonModule</strong> - GeoJSON serialization, only when Jackson is on the classpath</li>
 *   <li><strong>GeometryFieldInterceptor</strong> - Legacy SELECT rewriting, only when
 *       {@code mybatis.geometry.interceptor-enabled=true}</li>
 * </ul>
 *
 * <p>TypeHandlers that MyBatis-Plus instantiates reflectively (for example from
 * {@code @TableField(typeHandler = ...)}) cannot receive Spring beans, so this configuration also
 * publishes the settings to the library's process-wide defaults ({@link GeometryStrategyFactory},
 * {@link GeometryFactoryProvider}, {@link GeometryDefaults} and {@link GeoJsonOptions}). When several
 * application contexts with different settings share a class loader, the last one started wins.</p>
 */
@AutoConfiguration
@ConditionalOnClass({BaseMapper.class, Geometry.class})
@EnableConfigurationProperties(GeometryProperties.class)
public class GeometryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GeometryAutoConfiguration.class);

    private final GeometryProperties properties;

    /**
     * Create the auto-configuration and publish the configured defaults.
     *
     * @param properties bound {@code mybatis.geometry.*} properties
     */
    public GeometryAutoConfiguration(GeometryProperties properties) {
        this.properties = properties;
        applyDefaults(properties);
    }

    /**
     * Publish the configured settings to the library's process-wide defaults.
     *
     * @param properties bound {@code mybatis.geometry.*} properties
     */
    static void applyDefaults(GeometryProperties properties) {
        GeometryFactoryProvider.setDefaultSrid(properties.getDefaultSrid());
        GeometryFactoryProvider.setCoordinateSequenceType(properties.getCoordinateSequence());
        GeometryDefaults.setWriteValidation(properties.getWriteValidation());
        GeometryDefaults.setPreserveZ(properties.isPreserveZ());
        GeoJsonOptions.setGlobal(properties.toGeoJsonOptions());
        log.debug("Geometry defaults: srid={}, coordinateSequence={}, writeValidation={}, preserveZ={}, geojson={}",
            properties.getDefaultSrid(), properties.getCoordinateSequence(), properties.getWriteValidation(),
            properties.isPreserveZ(), GeoJsonOptions.getGlobal());
    }

    /**
     * Publish the {@code mybatis.geometry.*} defaults before any singleton is created, so beans
     * instantiated before this configuration (and applications with lazy initialization) already see
     * them. The constructor publishes them again from the bound properties.
     *
     * @param environment the application environment
     * @return the post-processor
     */
    @Bean
    static BeanFactoryPostProcessor geometryDefaultsPublisher(Environment environment) {
        return beanFactory -> {
            try {
                GeometryProperties early = Binder.get(environment)
                    .bind("mybatis.geometry", GeometryProperties.class)
                    .orElseGet(GeometryProperties::new);
                applyDefaults(early);
            } catch (RuntimeException e) {
                // invalid values fail the regular @ConfigurationProperties binding with a clear message
                log.debug("Could not bind mybatis.geometry.* early; defaults are published later", e);
            }
        };
    }

    /**
     * Make every {@link GeometryHandlerStrategy} bean the process-wide default as soon as it is
     * initialized, including user-defined ones, so reflectively created TypeHandlers use it.
     * {@link #geometryDefaultStrategyPublisher} settles the final choice once all singletons exist.
     *
     * @return the post-processor
     */
    @Bean
    static BeanPostProcessor geometryStrategyDefaultRegistrar() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof GeometryHandlerStrategy strategy) {
                    GeometryStrategyFactory.setDefaultStrategy(strategy);
                    log.debug("Default geometry strategy set from bean '{}' ({})", beanName,
                        strategy.getSupportedDatabaseType());
                }
                return bean;
            }
        };
    }

    /**
     * Create GeometryHandlerStrategy bean. Uses {@code mybatis.geometry.database-type} when set,
     * otherwise detects the database from the single DataSource bean.
     *
     * @param dataSources the application's DataSource beans
     * @return the strategy
     */
    @Bean
    @ConditionalOnMissingBean
    public GeometryHandlerStrategy geometryHandlerStrategy(ObjectProvider<DataSource> dataSources) {
        GeometryHandlerStrategy strategy = createStrategy(dataSources);
        // also published here: a strategy created while BeanPostProcessors are being registered
        // (for example as a dependency of another post-processor) bypasses the registrar
        GeometryStrategyFactory.setDefaultStrategy(strategy);
        return strategy;
    }

    private GeometryHandlerStrategy createStrategy(ObjectProvider<DataSource> dataSources) {
        if (properties.getDatabaseType() != null) {
            log.info("Using configured geometry database type: {}", properties.getDatabaseType());
            return GeometryStrategyFactory.getStrategy(properties.getDatabaseType());
        }
        DataSource dataSource = dataSources.getIfUnique();
        if (dataSource == null) {
            log.warn("No unique DataSource bean to detect the geometry database type from; defaulting to MYSQL. "
                + "Set mybatis.geometry.database-type to choose explicitly.");
            return GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL);
        }
        GeometryHandlerStrategy strategy = GeometryStrategyFactory.detectStrategy(dataSource);
        log.info("Detected geometry database type: {}", strategy.getSupportedDatabaseType());
        return strategy;
    }

    /**
     * Once all singletons exist, make the unique (or {@code @Primary}) strategy bean the process-wide
     * default, so the last strategy initialized does not win when there are several.
     *
     * @param strategies the strategy beans
     * @return the callback
     */
    @Bean
    static SmartInitializingSingleton geometryDefaultStrategyPublisher(ObjectProvider<GeometryHandlerStrategy> strategies) {
        return () -> {
            GeometryHandlerStrategy strategy = strategies.getIfUnique();
            if (strategy != null) {
                GeometryStrategyFactory.setDefaultStrategy(strategy);
            } else if (strategies.stream().count() > 1) {
                log.warn("Several GeometryHandlerStrategy beans and none is @Primary; TypeHandlers created by "
                    + "MyBatis-Plus through @TableField(typeHandler = ...) use {}. Mark one strategy @Primary.",
                    GeometryStrategyFactory.getDefaultStrategy().getSupportedDatabaseType());
            }
        };
    }

    /**
     * Create the legacy SELECT interceptor. Only registered when
     * {@code mybatis.geometry.interceptor-enabled=true}.
     *
     * @param strategy the geometry strategy
     * @return the interceptor
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mybatis.geometry", name = "interceptor-enabled", havingValue = "true")
    public GeometryFieldInterceptor geometryFieldInterceptor(GeometryHandlerStrategy strategy) {
        log.info("Registering GeometryFieldInterceptor for SELECT queries");
        return new GeometryFieldInterceptor(strategy);
    }

    /**
     * Create PointTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public PointTypeHandler pointTypeHandler(GeometryHandlerStrategy strategy) {
        return new PointTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create LineStringTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public LineStringTypeHandler lineStringTypeHandler(GeometryHandlerStrategy strategy) {
        return new LineStringTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create PolygonTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public PolygonTypeHandler polygonTypeHandler(GeometryHandlerStrategy strategy) {
        return new PolygonTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create MultiPointTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiPointTypeHandler multiPointTypeHandler(GeometryHandlerStrategy strategy) {
        return new MultiPointTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create MultiLineStringTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiLineStringTypeHandler multiLineStringTypeHandler(GeometryHandlerStrategy strategy) {
        return new MultiLineStringTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create MultiPolygonTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public MultiPolygonTypeHandler multiPolygonTypeHandler(GeometryHandlerStrategy strategy) {
        return new MultiPolygonTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create GeometryCollectionTypeHandler bean.
     */
    @Bean
    @ConditionalOnMissingBean
    public GeometryCollectionTypeHandler geometryCollectionTypeHandler(GeometryHandlerStrategy strategy) {
        return new GeometryCollectionTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * Create GeometryTypeHandler bean (generic {@code Geometry} fields).
     */
    @Bean
    @ConditionalOnMissingBean
    public GeometryTypeHandler geometryTypeHandler(GeometryHandlerStrategy strategy) {
        return new GeometryTypeHandler(properties.getDefaultSrid(), strategy);
    }

    /**
     * GeoJSON support, isolated in a nested configuration so that the auto-configuration class
     * itself never references Jackson types and starts without Jackson on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.fasterxml.jackson.databind.ObjectMapper")
    static class GeometryJacksonConfiguration {

        /**
         * Create GeometryJacksonModule bean; Spring Boot registers Module beans with its ObjectMapper.
         *
         * @param properties bound {@code mybatis.geometry.*} properties
         * @return the module
         */
        @Bean
        @ConditionalOnMissingBean(name = "geometryJacksonModule")
        GeometryJacksonModule geometryJacksonModule(GeometryProperties properties) {
            GeoJsonOptions options = properties.toGeoJsonOptions();
            log.info("Registering GeometryJacksonModule ({})", options);
            return new GeometryJacksonModule(options);
        }
    }
}
