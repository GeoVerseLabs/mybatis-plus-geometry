package io.github.geoverselabs.mybatis.geometry.it;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.util.List;

class PostgisRoundTripIT extends AbstractGeometryRoundTripIT {

    @Container
    static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>(
        DockerImageName.parse("postgis/postgis:16-3.4").asCompatibleSubstituteFor("postgres"));

    static DataSource dataSource;

    @BeforeAll
    static void initDataSource() {
        dataSource = new PooledDataSource("org.postgresql.Driver", DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    protected DatabaseType databaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    protected List<String> schema() {
        return List.of(
            "CREATE EXTENSION IF NOT EXISTS postgis",
            "DROP TABLE IF EXISTS geo_all",
            "CREATE TABLE geo_all (id BIGSERIAL PRIMARY KEY, name VARCHAR(64), pt geometry(Point,4326),"
                + " ls geometry(LineString,4326), pg geometry(Polygon,4326), mpt geometry(MultiPoint,4326),"
                + " mls geometry(MultiLineString,4326), mpg geometry(MultiPolygon,4326),"
                + " gc geometry(GeometryCollection,4326), geom geometry)");
    }
}
