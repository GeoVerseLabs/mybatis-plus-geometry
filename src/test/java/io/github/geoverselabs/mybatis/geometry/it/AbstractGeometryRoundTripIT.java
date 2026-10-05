package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.geoverselabs.mybatis.geometry.handler.GeometryTypeHandler;
import io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Database round-trip tests shared by every supported database.
 *
 * <p>Each test runs twice: once reading geometry columns natively (no SQL rewriting) and once
 * with the legacy {@link GeometryFieldInterceptor} wrapping geometry columns in SELECT queries.</p>
 */
@Testcontainers(disabledWithoutDocker = true)
abstract class AbstractGeometryRoundTripIT {

    enum ReadMode { NATIVE, INTERCEPTOR }

    protected abstract DataSource dataSource();

    protected abstract DatabaseType databaseType();

    protected abstract List<String> schema();

    /**
     * DDL for table {@code geo_srid (id, name, pt)} whose point column is declared with SRID 4326.
     */
    protected abstract List<String> sridTableSchema();

    /** Whether the database rejects a geometry whose SRID differs from the column SRID. */
    protected abstract boolean enforcesColumnSrid();

    /** Empty geometries (WKT) that the database can store in an unconstrained geometry column. */
    protected abstract List<String> storableEmptyGeometries();

    /** Whether the database stores Z ordinates. */
    protected abstract boolean supportsZ();

    @BeforeEach
    void createSchema() throws SQLException {
        try (Connection c = dataSource().getConnection(); Statement st = c.createStatement()) {
            for (String sql : schema()) {
                st.execute(sql);
            }
            for (String sql : sridTableSchema()) {
                st.execute(sql);
            }
        }
    }

    @AfterEach
    void resetDefaults() {
        GeometryDefaults.reset();
        GeometryFactoryProvider.reset();
    }

    @AfterAll
    static void resetStrategy() {
        GeometryStrategyFactory.clearCache();
    }

    protected SqlSessionFactory factory(ReadMode mode) {
        return factory(mode, dataSource());
    }

    protected SqlSessionFactory factory(ReadMode mode, DataSource dataSource) {
        GeometryHandlerStrategy strategy = GeometryStrategyFactory.getStrategy(databaseType());
        GeometryStrategyFactory.setDefaultStrategy(strategy);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setEnvironment(new Environment("it-" + mode, new JdbcTransactionFactory(), dataSource));
        if (mode == ReadMode.INTERCEPTOR) {
            cfg.addInterceptor(new GeometryFieldInterceptor(strategy));
        }
        cfg.addMapper(GeoAllMapper.class);
        cfg.addMapper(GeoSridPointMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(cfg);
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void roundTripsEveryGeometryType(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("all");
            assertEquals(1, mapper.insert(in));
            assertNotNull(in.getId());

            assertEntity(GeometryFixtures.fullEntity("all"), mapper.selectById(in.getId()));
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void selectListAndColumnSubsets(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            mapper.insert(GeometryFixtures.fullEntity("a"));
            mapper.insert(GeometryFixtures.fullEntity("b"));

            List<GeoAll> list = mapper.selectList(Wrappers.<GeoAll>lambdaQuery().eq(GeoAll::getName, "b"));
            assertEquals(1, list.size());
            assertEntity(GeometryFixtures.fullEntity("b"), list.get(0));

            List<GeoAll> subset = mapper.selectList(new QueryWrapper<GeoAll>().select("id", "name", "mpg", "gc").orderByAsc("id"));
            assertEquals(2, subset.size());
            assertGeometry(GeometryFixtures.multiPolygon(), subset.get(0).getMpg());
            assertGeometry(GeometryFixtures.geometryCollection(), subset.get(1).getGc());
            assertNull(subset.get(0).getPt());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void handWrittenSqlWithTableAlias(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("alias");
            mapper.insert(in);

            GeoAll out = mapper.selectWithAlias(in.getId());
            assertGeometry(GeometryFixtures.point(), out.getPt());
            assertGeometry(GeometryFixtures.multiPolygon(), out.getMpg());
            assertGeometry(GeometryFixtures.polygonWithHole(), out.getGeom());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void nullGeometriesRoundTrip(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = new GeoAll();
            in.setName("empty");
            mapper.insert(in);

            GeoAll out = mapper.selectById(in.getId());
            assertEquals("empty", out.getName());
            assertNull(out.getPt());
            assertNull(out.getMpg());
            assertNull(out.getGeom());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void updateReplacesGeometry(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("upd");
            mapper.insert(in);

            GeoAll patch = new GeoAll();
            patch.setId(in.getId());
            patch.setMpg((org.locationtech.jts.geom.MultiPolygon) GeometryFixtures.wkt(
                "MULTIPOLYGON (((120 30, 121 30, 121 31, 120 31, 120 30)))"));
            patch.setGeom(GeometryFixtures.lineString());
            mapper.updateById(patch);

            GeoAll out = mapper.selectById(in.getId());
            assertGeometry(patch.getMpg(), out.getMpg());
            assertGeometry(GeometryFixtures.lineString(), out.getGeom());
            assertGeometry(GeometryFixtures.point(), out.getPt());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void preservesNonDefaultSrid(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            Geometry mercator = GeometryFixtures.wkt("POINT (13522420.5 3662269.25)", 3857);
            GeoAll in = new GeoAll();
            in.setName("3857");
            in.setGeom(mercator);
            mapper.insert(in);

            GeoAll out = mapper.selectById(in.getId());
            assertGeometry(mercator, out.getGeom());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void updateByIdRewritesGeometriesThatWereReadBack(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("rw");
            // OGC-invalid but storable: two squares sharing an edge (common in administrative data)
            in.setMpg((MultiPolygon) GeometryFixtures.wkt(
                "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((1 0, 2 0, 2 1, 1 1, 1 0)))"));
            mapper.insert(in);

            GeoAll loaded = mapper.selectById(in.getId());
            loaded.setName("renamed");
            assertEquals(1, mapper.updateById(loaded));

            GeoAll out = mapper.selectById(in.getId());
            assertEquals("renamed", out.getName());
            assertGeometry(in.getMpg(), out.getMpg());
            assertGeometry(GeometryFixtures.polygonWithHole(), out.getPg());
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void sridConstrainedColumnRoundTrip(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoSridPointMapper mapper = session.getMapper(GeoSridPointMapper.class);
            Point untagged = new GeometryFactory().createPoint(new Coordinate(121.4737, 31.2304));
            GeoSridPoint in = new GeoSridPoint();
            in.setName("srid");
            in.setPt(untagged);
            assertEquals(1, mapper.insert(in));
            assertEquals(0, untagged.getSRID(), "insert must not modify the entity's geometry");

            GeoSridPoint out = mapper.selectById(in.getId());
            assertGeometry(GeometryFixtures.point(), out.getPt());

            out.setName("srid2");
            assertEquals(1, mapper.updateById(out));
            assertGeometry(GeometryFixtures.point(), mapper.selectById(in.getId()).getPt());

            if (enforcesColumnSrid()) {
                GeoSridPoint wrong = new GeoSridPoint();
                wrong.setName("wrong");
                wrong.setPt((Point) GeometryFixtures.wkt("POINT (13522420.5 3662269.25)", 3857));
                assertThrows(RuntimeException.class, () -> mapper.insert(wrong));
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void untaggedGeometriesUseConfiguredDefaultSrid(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeometryFactory untaggedFactory = new GeometryFactory(new PrecisionModel(), 0);

            GeometryFactoryProvider.setDefaultSrid(3857);
            GeoAll mercator = new GeoAll();
            mercator.setName("default-3857");
            mercator.setGeom(untaggedFactory.createPoint(new Coordinate(1, 2)));
            mapper.insert(mercator);
            assertEquals(0, mercator.getGeom().getSRID());
            assertEquals(3857, mapper.selectById(mercator.getId()).getGeom().getSRID());

            GeometryFactoryProvider.setDefaultSrid(0);
            GeoAll cartesian = new GeoAll();
            cartesian.setName("default-0");
            cartesian.setGeom(untaggedFactory.createPoint(new Coordinate(500000, 4000000)));
            mapper.insert(cartesian);
            Geometry out = mapper.selectById(cartesian.getId()).getGeom();
            assertEquals(0, out.getSRID());
            assertTrue(out.equalsExact(cartesian.getGeom()));
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void emptyGeometriesRoundTrip(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            for (String wkt : storableEmptyGeometries()) {
                GeoAll in = new GeoAll();
                in.setName(wkt);
                in.setGeom(GeometryFixtures.wkt(wkt));
                mapper.insert(in);

                Geometry out = mapper.selectById(in.getId()).getGeom();
                assertNotNull(out, wkt);
                assertTrue(out.isEmpty(), () -> wkt + " read back as " + out);
                assertEquals(in.getGeom().getGeometryType(), out.getGeometryType(), wkt);
                assertEquals(4326, out.getSRID(), wkt);
            }
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void zOrdinatesFollowPreserveZ(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            Geometry withZ = GeometryFixtures.wkt("LINESTRING Z (121.47 31.23 10, 121.48 31.24 12.5)");

            GeoAll dropped = new GeoAll();
            dropped.setName("z-dropped");
            dropped.setGeom(withZ);
            mapper.insert(dropped);
            Geometry flat = mapper.selectById(dropped.getId()).getGeom();
            assertTrue(Double.isNaN(flat.getCoordinates()[0].getZ()), "Z must be dropped by default");
            assertTrue(flat.equalsExact(withZ));

            GeometryDefaults.setPreserveZ(true);
            GeoAll kept = new GeoAll();
            kept.setName("z-kept");
            kept.setGeom(withZ);
            mapper.insert(kept);
            Geometry out = mapper.selectById(kept.getId()).getGeom();
            if (supportsZ()) {
                assertEquals(12.5, out.getCoordinates()[1].getZ());
            } else {
                assertTrue(Double.isNaN(out.getCoordinates()[1].getZ()));
            }
            assertTrue(out.equalsExact(withZ));
        }
    }

    @ParameterizedTest
    @EnumSource(ReadMode.class)
    void typeMismatchIsReported(ReadMode mode) {
        try (SqlSession session = factory(mode).openSession(true)) {
            GeoAllMapper mapper = session.getMapper(GeoAllMapper.class);
            GeoAll in = GeometryFixtures.fullEntity("mismatch");
            mapper.insert(in);

            RuntimeException e = assertThrows(RuntimeException.class,
                () -> mapper.selectMultiPolygonAsPolygon(in.getId()));
            assertTrue(causeMessages(e).contains("contains a MultiPolygon but the mapped type is"),
                () -> causeMessages(e));
        }
    }

    /**
     * MySQL/MariaDB: reads a GEOMETRY OUT parameter of a stored procedure through a handler.
     */
    protected void assertMySqlCallableOutParameter() throws SQLException {
        try (Connection c = dataSource().getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP PROCEDURE IF EXISTS geo_out");
            st.execute("CREATE PROCEDURE geo_out(OUT g GEOMETRY)"
                + " BEGIN SET g = ST_GeomFromText('MULTIPOINT(1 2, 3 4)', 3857); END");
            GeometryTypeHandler handler =
                new GeometryTypeHandler(4326, GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
            try (CallableStatement cs = c.prepareCall("{call geo_out(?)}")) {
                cs.registerOutParameter(1, Types.OTHER);
                cs.execute();
                Geometry g = handler.getResult(cs, 1);
                assertEquals(3857, g.getSRID());
                assertTrue(g.equalsExact(GeometryFixtures.wkt("MULTIPOINT ((1 2), (3 4))")), () -> "was " + g);
            }
        }
    }

    protected static String causeMessages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append(" | ");
        }
        return sb.toString();
    }

    protected static void assertEntity(GeoAll expected, GeoAll actual) {
        assertNotNull(actual, "entity not found");
        assertEquals(expected.getName(), actual.getName());
        assertGeometry(expected.getPt(), actual.getPt());
        assertGeometry(expected.getLs(), actual.getLs());
        assertGeometry(expected.getPg(), actual.getPg());
        assertGeometry(expected.getMpt(), actual.getMpt());
        assertGeometry(expected.getMls(), actual.getMls());
        assertGeometry(expected.getMpg(), actual.getMpg());
        assertGeometry(expected.getGc(), actual.getGc());
        assertGeometry(expected.getGeom(), actual.getGeom());
    }

    protected static void assertGeometry(Geometry expected, Geometry actual) {
        assertNotNull(actual, () -> "expected " + expected + " but was null");
        assertEquals(expected.getGeometryType(), actual.getGeometryType());
        assertTrue(expected.equalsExact(actual), () -> "expected " + expected + " but was " + actual);
        assertEquals(expected.getSRID(), actual.getSRID(), () -> "SRID of " + actual);
        for (int i = 0; i < actual.getNumGeometries(); i++) {
            assertEquals(expected.getSRID(), actual.getGeometryN(i).getSRID(), () -> "SRID of members of " + actual);
        }
    }
}
