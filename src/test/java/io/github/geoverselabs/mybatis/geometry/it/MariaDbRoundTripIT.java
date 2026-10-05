package io.github.geoverselabs.mybatis.geometry.it;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;

class MariaDbRoundTripIT extends AbstractGeometryRoundTripIT {

    @Container
    static final MariaDBContainer<?> DB = new MariaDBContainer<>("mariadb:11.4");

    static DataSource dataSource;

    @BeforeAll
    static void initDataSource() {
        dataSource = new PooledDataSource("org.mariadb.jdbc.Driver", DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
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

    @Override
    protected List<String> sridTableSchema() {
        return List.of(
            "DROP TABLE IF EXISTS geo_srid",
            "CREATE TABLE geo_srid (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64),"
                + " pt POINT REF_SYSTEM_ID=4326 NOT NULL)");
    }

    @Override
    protected boolean enforcesColumnSrid() {
        // MariaDB records REF_SYSTEM_ID as metadata but does not reject other SRIDs
        return false;
    }

    @Override
    protected List<String> storableEmptyGeometries() {
        return List.of("POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY", "MULTIPOINT EMPTY",
            "MULTILINESTRING EMPTY", "MULTIPOLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY");
    }

    @Override
    protected boolean supportsZ() {
        return false;
    }

    @Test
    void readsCallableOutParameter() throws SQLException {
        assertMySqlCallableOutParameter();
    }
}
