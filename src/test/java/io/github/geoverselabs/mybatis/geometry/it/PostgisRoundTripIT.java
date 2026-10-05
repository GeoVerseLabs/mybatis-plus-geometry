package io.github.geoverselabs.mybatis.geometry.it;

import io.github.geoverselabs.mybatis.geometry.handler.GeometryTypeHandler;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.session.SqlSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Geometry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Override
    protected List<String> sridTableSchema() {
        return List.of(
            "DROP TABLE IF EXISTS geo_srid",
            "CREATE TABLE geo_srid (id BIGSERIAL PRIMARY KEY, name VARCHAR(64), pt geometry(Point,4326) NOT NULL)");
    }

    @Override
    protected boolean enforcesColumnSrid() {
        return true;
    }

    @Override
    protected List<String> storableEmptyGeometries() {
        return List.of("POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY", "MULTIPOINT EMPTY",
            "MULTILINESTRING EMPTY", "MULTIPOLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY");
    }

    @Override
    protected boolean supportsZ() {
        return true;
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void readsByteaEwkbExpressions(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("bytea");
            mapper.insert(in);

            GeoAll out = mapper.selectAsEwkbBytea(in.getId());
            assertEquals("bytea", out.getName());
            assertGeometry(GeometryFixtures.point(), out.getPt());
            assertGeometry(GeometryFixtures.multiPolygon(), out.getMpg());
            assertGeometry(GeometryFixtures.geometryCollection(), out.getGc());
            assertGeometry(GeometryFixtures.polygonWithHole(), out.getGeom());
            assertNull(out.getLs());
        }
    }

    /**
     * pgjdbc switches a statement to a server-side prepared statement with binary result transfer
     * after {@code prepareThreshold} (default 5) executions on one connection; bytea columns then
     * arrive in binary format.
     */
    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void repeatedReadsSurviveServerSidePreparedStatements(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("repeat");
            mapper.insert(in);

            for (int i = 0; i < 12; i++) {
                session.clearCache();
                GeoAll bytea = mapper.selectAsEwkbBytea(in.getId());
                assertGeometry(GeometryFixtures.point(), bytea.getPt());
                assertGeometry(GeometryFixtures.multiPolygon(), bytea.getMpg());
                assertGeometry(GeometryFixtures.geometryCollection(), bytea.getGc());

                GeoAll plain = mapper.selectById(in.getId());
                assertEntity(in, plain);
            }
        }
    }

    /**
     * pgjdbc requires OUT parameters to be registered with the SQL type of the value (OTHER for
     * geometry, VARCHAR for text, BINARY for bytea) and then only allows the matching getter.
     */
    @Test
    void readsCallableOutParametersOfEveryType() throws SQLException {
        String value = "'SRID=3857;MULTIPOINT((1 2),(3 4))'::geometry";
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE OR REPLACE FUNCTION geo_out(OUT g geometry, OUT t text, OUT b bytea) AS $$ SELECT "
                + value + ", encode(ST_AsEWKB(" + value + "), 'hex'), ST_AsEWKB(" + value + ") $$ LANGUAGE sql");
            GeometryTypeHandler handler =
                new GeometryTypeHandler(4326, GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL));
            try (CallableStatement cs = c.prepareCall("{call geo_out(?, ?, ?)}")) {
                cs.registerOutParameter(1, Types.OTHER);
                cs.registerOutParameter(2, Types.VARCHAR);
                cs.registerOutParameter(3, Types.BINARY);
                cs.execute();
                for (int i = 1; i <= 3; i++) {
                    Geometry g = handler.getResult(cs, i);
                    assertEquals(3857, g.getSRID(), "parameter " + i);
                    assertTrue(g.equalsExact(GeometryFixtures.wkt("MULTIPOINT ((1 2), (3 4))")), "parameter " + i);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void readsPlainWkbHexWithoutSrid(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("plain");
            mapper.insert(in);

            Geometry pg = mapper.selectAsPlainWkbHex(in.getId()).getPg();
            // ST_AsBinary carries no SRID: the database says 0, not the configured default
            assertEquals(0, pg.getSRID());
            assertTrue(pg.equalsExact(GeometryFixtures.polygonWithHole()));
        }
    }
}
