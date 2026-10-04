package io.github.geoverselabs.mybatis.geometry.it;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.sql.DataSource;
import java.util.List;

class MySqlRoundTripIT extends AbstractGeometryRoundTripIT {

    @Container
    static final MySQLContainer<?> DB = new MySQLContainer<>("mysql:8.0");

    static DataSource dataSource;

    @BeforeAll
    static void initDataSource() {
        dataSource = new PooledDataSource("com.mysql.cj.jdbc.Driver", DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
    }

    @Override
    protected DataSource dataSource() {
        return dataSource;
    }

    @Override
    protected DatabaseType databaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    protected List<String> schema() {
        return List.of(
            "DROP TABLE IF EXISTS geo_all",
            "CREATE TABLE geo_all (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64), pt POINT, ls LINESTRING,"
                + " pg POLYGON, mpt MULTIPOINT, mls MULTILINESTRING, mpg MULTIPOLYGON, gc GEOMETRYCOLLECTION, geom GEOMETRY)");
    }
}
