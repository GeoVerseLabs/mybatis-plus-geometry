package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor;
import io.github.geoverselabs.mybatis.geometry.it.boot.BootGeoAllMapper;
import io.github.geoverselabs.mybatis.geometry.jackson.GeoJsonOptions;
import io.github.geoverselabs.mybatis.geometry.jackson.GeometryJacksonModule;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.MultiPolygon;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test of the Spring Boot auto-configuration against PostGIS: database detection from
 * the DataSource, native column reads without the interceptor, TypeHandlers registered by Java type
 * and the GeoJSON module honouring {@code mybatis.geometry.geojson.*}.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = SpringBootPostgisIT.App.class,
    properties = "mybatis.geometry.geojson.coordinate-precision=3")
class SpringBootPostgisIT {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @SpringBootApplication(scanBasePackageClasses = BootGeoAllMapper.class)
    @MapperScan(basePackageClasses = BootGeoAllMapper.class)
    static class App {
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    DataSource dataSource;

    @Autowired
    BootGeoAllMapper mapper;

    @AfterAll
    static void resetGlobals() {
        GeometryStrategyFactory.clearCache();
        GeometryFactoryProvider.reset();
        GeometryDefaults.reset();
        GeoJsonOptions.resetGlobal();
    }

    @BeforeEach
    void schema() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS postgis");
            st.execute("DROP TABLE IF EXISTS geo_all");
            st.execute("CREATE TABLE geo_all (id BIGSERIAL PRIMARY KEY, name VARCHAR(64), pt geometry(Point,4326),"
                + " ls geometry(LineString,4326), pg geometry(Polygon,4326), mpt geometry(MultiPoint,4326),"
                + " mls geometry(MultiLineString,4326), mpg geometry(MultiPolygon,4326),"
                + " gc geometry(GeometryCollection,4326), geom geometry)");
        }
    }

    @Test
    void detectsPostgisAndReadsNativelyWithoutInterceptor() {
        assertThat(context.getBean(GeometryHandlerStrategy.class).getSupportedDatabaseType())
            .isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(context.getBeansOfType(GeometryFieldInterceptor.class)).isEmpty();

        GeoAll in = GeometryFixtures.fullEntity("boot");
        mapper.insert(in);
        AbstractGeometryRoundTripIT.assertEntity(GeometryFixtures.fullEntity("boot"), mapper.selectById(in.getId()));
    }

    @Test
    void wrapperSetUsesTypeHandlerRegisteredByJavaType() {
        GeoAll in = GeometryFixtures.fullEntity("wrapper");
        mapper.insert(in);
        MultiPolygon replacement = (MultiPolygon) GeometryFixtures.wkt(
            "MULTIPOLYGON (((120 30, 121 30, 121 31, 120 31, 120 30)))");

        mapper.update(null, Wrappers.<GeoAll>lambdaUpdate()
            .set(GeoAll::getMpg, replacement)
            .eq(GeoAll::getId, in.getId()));

        AbstractGeometryRoundTripIT.assertGeometry(replacement, mapper.selectById(in.getId()).getMpg());
    }

    @Test
    void geoJsonModuleUsesConfiguredPrecision() throws Exception {
        ObjectMapper json = new ObjectMapper().registerModule(context.getBean(GeometryJacksonModule.class));
        assertThat(json.writeValueAsString(GeometryFixtures.point()))
            .isEqualTo("{\"type\":\"Point\",\"coordinates\":[121.474,31.23]}");
    }
}
