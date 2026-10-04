package io.github.geoverselabs.mybatis.geometry.it;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.geoverselabs.mybatis.geometry.interceptor.GeometryFieldInterceptor;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.locationtech.jts.geom.Geometry;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @BeforeEach
    void createSchema() throws SQLException {
        try (Connection c = dataSource().getConnection(); Statement st = c.createStatement()) {
            for (String sql : schema()) {
                st.execute(sql);
            }
        }
    }

    @AfterAll
    static void resetStrategy() {
        GeometryStrategyFactory.clearCache();
    }

    protected SqlSessionFactory factory(ReadMode mode) {
        GeometryHandlerStrategy strategy = GeometryStrategyFactory.getStrategy(databaseType());
        GeometryStrategyFactory.setDefaultStrategy(strategy);
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setEnvironment(new Environment("it-" + mode, new JdbcTransactionFactory(), dataSource()));
        if (mode == ReadMode.INTERCEPTOR) {
            cfg.addInterceptor(new GeometryFieldInterceptor(strategy));
        }
        cfg.addMapper(GeoAllMapper.class);
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
    }
}
