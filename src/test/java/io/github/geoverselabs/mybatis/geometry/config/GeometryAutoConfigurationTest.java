package io.github.geoverselabs.mybatis.geometry.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.github.geoverselabs.mybatis.geometry.strategy.PostGISGeometryStrategy;
import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeometryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(GeometryAutoConfiguration.class));

    @AfterEach
    void resetGlobals() {
        GeometryStrategyFactory.clearCache();
        GeometryFactoryProvider.reset();
        GeometryDefaults.reset();
        GeoJsonOptions.resetGlobal();
    }

    @Test
    void registersStrategyHandlersAndModuleButNotInterceptorByDefault() {
        runner.withPropertyValues("mybatis.geometry.database-type=POSTGRESQL").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(GeometryHandlerStrategy.class).getSupportedDatabaseType())
                .isEqualTo(DatabaseType.POSTGRESQL);
            assertThat(ctx).hasSingleBean(PointTypeHandler.class)
                .hasSingleBean(LineStringTypeHandler.class)
                .hasSingleBean(PolygonTypeHandler.class)
                .hasSingleBean(MultiPointTypeHandler.class)
                .hasSingleBean(MultiLineStringTypeHandler.class)
                .hasSingleBean(MultiPolygonTypeHandler.class)
                .hasSingleBean(GeometryCollectionTypeHandler.class)
                .hasSingleBean(GeometryTypeHandler.class)
                .hasSingleBean(GeometryJacksonModule.class)
                .doesNotHaveBean(GeometryFieldInterceptor.class);
            assertThat(GeometryStrategyFactory.getDefaultStrategy()).isSameAs(ctx.getBean(GeometryHandlerStrategy.class));
        });
    }

    @Test
    void interceptorIsOptIn() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.interceptor-enabled=true")
            .run(ctx -> assertThat(ctx).hasSingleBean(GeometryFieldInterceptor.class));
    }

    @Test
    void publishesPropertiesToProcessWideDefaults() {
        runner.withPropertyValues(
                "mybatis.geometry.database-type=MYSQL",
                "mybatis.geometry.default-srid=3857",
                "mybatis.geometry.coordinate-sequence=PACKED",
                "mybatis.geometry.write-validation=FULL",
                "mybatis.geometry.preserve-z=true",
                "mybatis.geometry.geojson.coordinate-precision=6",
                "mybatis.geometry.geojson.validation=BASIC",
                "mybatis.geometry.geojson.fast-double-parsing=false")
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(GeometryFactoryProvider.getConfiguredSrid()).isEqualTo(3857);
                assertThat(GeometryFactoryProvider.getFactory().getSRID()).isEqualTo(3857);
                assertThat(GeometryFactoryProvider.getCoordinateSequenceType()).isEqualTo(CoordinateSequenceType.PACKED);
                assertThat(GeometryFactoryProvider.getFactory().getCoordinateSequenceFactory())
                    .isInstanceOf(PackedCoordinateSequenceFactory.class);
                assertThat(GeometryDefaults.getWriteValidation()).isEqualTo(GeometryValidation.FULL);
                assertThat(GeometryDefaults.isPreserveZ()).isTrue();
                GeoJsonOptions options = GeoJsonOptions.getGlobal();
                assertThat(options.coordinatePrecision()).isEqualTo(6);
                assertThat(options.validation()).isEqualTo(GeometryValidation.BASIC);
                assertThat(options.fastDoubleParsing()).isFalse();
                // SRID 3857 is projected: WGS84 range validation is off unless requested explicitly
                assertThat(options.coordinateRangeValidation()).isFalse();
            });
    }

    @Test
    void defaultsMatchDocumentation() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL").run(ctx -> {
            GeometryProperties p = ctx.getBean(GeometryProperties.class);
            assertThat(p.getDefaultSrid()).isEqualTo(4326);
            assertThat(p.isInterceptorEnabled()).isFalse();
            assertThat(p.getWriteValidation()).isEqualTo(GeometryValidation.BASIC);
            assertThat(p.isPreserveZ()).isFalse();
            assertThat(p.getCoordinateSequence()).isEqualTo(CoordinateSequenceType.ARRAY);
            assertThat(p.toGeoJsonOptions()).isEqualTo(GeoJsonOptions.DEFAULTS);
            assertThat(GeoJsonOptions.getGlobal()).isEqualTo(GeoJsonOptions.DEFAULTS);
        });
    }

    @Test
    void explicitRangeValidationOverridesSridHeuristic() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.default-srid=4490",
                "mybatis.geometry.geojson.coordinate-range-validation=true")
            .run(ctx -> assertThat(GeoJsonOptions.getGlobal().coordinateRangeValidation()).isTrue());
    }

    @Test
    void rejectsOutOfRangePrecision() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.geojson.coordinate-precision=16")
            .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void moduleUsesConfiguredPrecision() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.geojson.coordinate-precision=3")
            .run(ctx -> {
                ObjectMapper mapper = new ObjectMapper().registerModule(ctx.getBean(GeometryJacksonModule.class));
                Point p = GeometryFactoryProvider.getFactory().createPoint(new Coordinate(121.473701234, 31.230412345));
                assertThat(mapper.writeValueAsString(p))
                    .isEqualTo("{\"type\":\"Point\",\"coordinates\":[121.474,31.23]}");
            });
    }

    @Test
    void startsWithoutJacksonOnTheClasspath() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL")
            .withClassLoader(new FilteredClassLoader(ObjectMapper.class))
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(ctx).hasSingleBean(PointTypeHandler.class);
                assertThat(ctx).doesNotHaveBean("geometryJacksonModule");
            });
    }

    @Test
    void userDefinedStrategyBecomesProcessWideDefault() {
        runner.withUserConfiguration(CustomStrategyConfig.class).run(ctx -> {
            assertThat(ctx).hasSingleBean(GeometryHandlerStrategy.class);
            assertThat(GeometryStrategyFactory.getDefaultStrategy()).isSameAs(CustomStrategyConfig.STRATEGY);
        });
    }

    @Test
    void detectsDatabaseFromUniqueDataSource() {
        runner.withUserConfiguration(PostgresDataSourceConfig.class).run(ctx ->
            assertThat(ctx.getBean(GeometryHandlerStrategy.class).getSupportedDatabaseType())
                .isEqualTo(DatabaseType.POSTGRESQL));
    }

    @Test
    void startsWithSeveralDataSources() {
        runner.withUserConfiguration(TwoDataSourcesConfig.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(GeometryHandlerStrategy.class).getSupportedDatabaseType()).isEqualTo(DatabaseType.MYSQL);
        });
        runner.withUserConfiguration(TwoDataSourcesConfig.class)
            .withPropertyValues("mybatis.geometry.database-type=POSTGRESQL")
            .run(ctx -> assertThat(ctx.getBean(GeometryHandlerStrategy.class).getSupportedDatabaseType())
                .isEqualTo(DatabaseType.POSTGRESQL));
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomStrategyConfig {
        static final GeometryHandlerStrategy STRATEGY = new PostGISGeometryStrategy();

        @Bean
        GeometryHandlerStrategy customStrategy() {
            return STRATEGY;
        }
    }

    static DataSource dataSource(String productName, String url) throws Exception {
        DataSource ds = mock(DataSource.class);
        Connection c = mock(Connection.class);
        DatabaseMetaData md = mock(DatabaseMetaData.class);
        when(ds.getConnection()).thenReturn(c);
        when(c.getMetaData()).thenReturn(md);
        when(md.getDatabaseProductName()).thenReturn(productName);
        when(md.getURL()).thenReturn(url);
        return ds;
    }

    @Configuration(proxyBeanMethods = false)
    static class PostgresDataSourceConfig {
        @Bean
        DataSource dataSource() throws Exception {
            return GeometryAutoConfigurationTest.dataSource("PostgreSQL", "jdbc:postgresql://db/mysql_archive");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoDataSourcesConfig {
        @Bean
        DataSource first() throws Exception {
            return GeometryAutoConfigurationTest.dataSource("PostgreSQL", "jdbc:postgresql://a/db");
        }

        @Bean
        DataSource second() throws Exception {
            return GeometryAutoConfigurationTest.dataSource("MySQL", "jdbc:mysql://b/db");
        }
    }

    // ------------------------------------------------------------------ publication order

    @Test
    void primaryStrategyBecomesTheDefaultWhenThereAreSeveral() {
        runner.withUserConfiguration(TwoStrategiesConfig.class).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(GeometryStrategyFactory.getDefaultStrategy()).isSameAs(TwoStrategiesConfig.PRIMARY);
        });
    }

    @Test
    void strategyCreatedDuringPostProcessorRegistrationIsStillPublished() {
        runner.withPropertyValues("mybatis.geometry.database-type=POSTGRESQL")
            .withUserConfiguration(EarlyStrategyConsumerConfig.class)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(GeometryStrategyFactory.getDefaultStrategy().getSupportedDatabaseType())
                    .isEqualTo(DatabaseType.POSTGRESQL);
            });
    }

    @Test
    void defaultsArePublishedBeforeUserBeansAreCreated() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.default-srid=4490",
                "mybatis.geometry.geojson.coordinate-precision=6")
            .withUserConfiguration(EarlyReaderConfig.class)
            .run(ctx -> {
                EarlyReader reader = ctx.getBean(EarlyReader.class);
                assertThat(reader.srid).isEqualTo(4490);
                assertThat(reader.precision).isEqualTo(6);
            });
    }

    @Test
    void defaultsArePublishedWithLazyInitialization() {
        runner.withPropertyValues("mybatis.geometry.database-type=MYSQL", "mybatis.geometry.default-srid=4490",
                "mybatis.geometry.write-validation=FULL")
            .withBean(org.springframework.boot.LazyInitializationBeanFactoryPostProcessor.class)
            .run(ctx -> {
                assertThat(ctx).hasNotFailed();
                assertThat(GeometryFactoryProvider.getConfiguredSrid()).isEqualTo(4490);
                assertThat(GeometryDefaults.getWriteValidation()).isEqualTo(GeometryValidation.FULL);
            });
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoStrategiesConfig {
        static final GeometryHandlerStrategy PRIMARY = new PostGISGeometryStrategy();

        @Bean
        @org.springframework.context.annotation.Primary
        GeometryHandlerStrategy primaryStrategy() {
            return PRIMARY;
        }

        @Bean
        GeometryHandlerStrategy reportingStrategy() {
            return new io.github.geoverselabs.mybatis.geometry.strategy.MySQLGeometryStrategy();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class EarlyStrategyConsumerConfig {
        /** Like Shiro's filter factory: a post-processor whose dependencies reach the strategy bean. */
        @Bean
        static org.springframework.beans.factory.config.BeanPostProcessor earlyConsumer(
                GeometryHandlerStrategy strategy) {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
            };
        }
    }

    static class EarlyReader {
        final int srid = GeometryFactoryProvider.getFactory().getSRID();
        final Integer precision = GeoJsonOptions.getGlobal().coordinatePrecision();
    }

    @Configuration(proxyBeanMethods = false)
    static class EarlyReaderConfig {
        @Bean
        EarlyReader earlyReader() {
            return new EarlyReader();
        }
    }
}
