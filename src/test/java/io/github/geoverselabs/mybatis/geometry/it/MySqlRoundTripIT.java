package io.github.geoverselabs.mybatis.geometry.it;

import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;

import javax.sql.DataSource;
import java.sql.SQLException;
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

    @Override
    protected List<String> sridTableSchema() {
        return List.of(
            "DROP TABLE IF EXISTS geo_srid",
            "CREATE TABLE geo_srid (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64), pt POINT NOT NULL SRID 4326)");
    }

    @Override
    protected boolean enforcesColumnSrid() {
        return true;
    }

    @Override
    protected List<String> storableEmptyGeometries() {
        // MySQL rejects other empty geometries ("Cannot get geometry object from data you send");
        // an empty point is written as NaN ordinates, which MySQL stores and returns unchanged
        return List.of("POINT EMPTY", "GEOMETRYCOLLECTION EMPTY");
    }

    @Override
    protected boolean supportsZ() {
        return false;
    }

    @Test
    void readsCallableOutParameter() throws SQLException {
        assertMySqlCallableOutParameter();
    }

    /** Server-side prepared statements return result rows in the binary protocol. */
    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void readsThroughServerSidePreparedStatements(ReadMode mode) {
        String url = DB.getJdbcUrl();
        PooledDataSource serverPrepared = new PooledDataSource("com.mysql.cj.jdbc.Driver",
            url + (url.contains("?") ? "&" : "?") + "useServerPrepStmts=true&cachePrepStmts=true",
            DB.getUsername(), DB.getPassword());
        try (SqlSession session = factory(mode, serverPrepared).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("binary-protocol");
            mapper.insert(in);
            for (int i = 0; i < 3; i++) {
                session.clearCache();
                assertEntity(GeometryFixtures.fullEntity("binary-protocol"), mapper.selectById(in.getId()));
            }
        } finally {
            serverPrepared.forceCloseAll();
        }
    }
}
