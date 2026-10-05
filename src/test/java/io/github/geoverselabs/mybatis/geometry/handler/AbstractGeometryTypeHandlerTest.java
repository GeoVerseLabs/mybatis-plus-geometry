package io.github.geoverselabs.mybatis.geometry.handler;

import io.github.geoverselabs.mybatis.geometry.codec.WkbSupport;
import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import io.github.geoverselabs.mybatis.geometry.strategy.DatabaseType;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.geoverselabs.mybatis.geometry.strategy.MySQLGeometryStrategy;
import io.github.geoverselabs.mybatis.geometry.strategy.PostGISGeometryStrategy;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.mockito.ArgumentCaptor;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractGeometryTypeHandlerTest {

    private static final MySQLGeometryStrategy MYSQL = new MySQLGeometryStrategy();
    private static final PostGISGeometryStrategy POSTGIS = new PostGISGeometryStrategy();

    @AfterEach
    void reset() {
        GeometryStrategyFactory.clearCache();
        GeometryFactoryProvider.reset();
        GeometryDefaults.reset();
    }

    // ==================== lazy configuration ====================

    @Test
    void noArgHandlerResolvesStrategyOnEveryCall() throws SQLException {
        PointTypeHandler handler = new PointTypeHandler();
        Point point = (Point) wkt("POINT (1 2)", 4326);

        GeometryStrategyFactory.setDefaultStrategy(GeometryStrategyFactory.getStrategy(DatabaseType.POSTGRESQL));
        PreparedStatement pg = mock(PreparedStatement.class);
        handler.setNonNullParameter(pg, 1, point, null);
        verify(pg).setObject(1, "0101000020e6100000000000000000f03f0000000000000040", Types.OTHER);
        verify(pg, never()).setBytes(anyInt(), any());

        GeometryStrategyFactory.setDefaultStrategy(GeometryStrategyFactory.getStrategy(DatabaseType.MYSQL));
        PreparedStatement my = mock(PreparedStatement.class);
        handler.setNonNullParameter(my, 2, point, null);
        verify(my).setBytes(eq(2), any(byte[].class));
    }

    @Test
    void noArgHandlerResolvesDefaultSridOnEveryCall() throws SQLException {
        PolygonTypeHandler handler = new PolygonTypeHandler(); // created before configuration
        GeometryFactoryProvider.setDefaultSrid(3857);
        Polygon polygon = (Polygon) wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");

        assertThat(sridWritten(handler, polygon)).isEqualTo(3857);

        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(sridWritten(handler, polygon)).isZero();
        assertThat(polygon.getSRID()).isZero();
    }

    @Test
    void explicitSridIsUsedAndStrategyStaysLazy() throws SQLException {
        LineStringTypeHandler handler = new LineStringTypeHandler(2154);
        GeometryFactoryProvider.setDefaultSrid(3857);
        LineString line = (LineString) wkt("LINESTRING (0 0, 1 1)");

        assertThat(sridWritten(handler, line)).isEqualTo(2154);

        GeometryStrategyFactory.setDefaultStrategy(POSTGIS);
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, line, null);
        verify(ps).setObject(eq(1), any(String.class), eq(Types.OTHER));
    }

    @Test
    void explicitStrategyIsUsed() throws SQLException {
        GeometryStrategyFactory.setDefaultStrategy(MYSQL);
        GeometryTypeHandler handler = new GeometryTypeHandler(4326, POSTGIS);
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, wkt("POINT (1 2)"), null);
        verify(ps).setObject(eq(1), any(String.class), eq(Types.OTHER));
    }

    @Test
    void nullStrategyInConstructorMeansDefault() throws SQLException {
        GeometryStrategyFactory.setDefaultStrategy(POSTGIS);
        PointTypeHandler handler = new PointTypeHandler(4326, null);
        PreparedStatement ps = mock(PreparedStatement.class);
        handler.setNonNullParameter(ps, 1, (Point) wkt("POINT (1 2)"), null);
        verify(ps).setObject(eq(1), any(String.class), eq(Types.OTHER));
    }

    @Test
    void geometrySridWinsOverDefault() throws SQLException {
        PointTypeHandler handler = new PointTypeHandler(4326, MYSQL);
        assertThat(sridWritten(handler, (Point) wkt("POINT (1 2)", 32650))).isEqualTo(32650);
    }

    @Test
    @SuppressWarnings("deprecation")
    void deprecatedFieldsHoldConstructionSnapshot() {
        GeometryFactoryProvider.setDefaultSrid(3857);
        GeometryStrategyFactory.setDefaultStrategy(POSTGIS);
        PointTypeHandler handler = new PointTypeHandler();
        assertThat(handler.defaultSrid).isEqualTo(3857);
        assertThat(handler.strategy).isSameAs(POSTGIS);
        assertThat(handler.getDefaultSrid()).isEqualTo(3857);
        assertThat(handler.getStrategy()).isSameAs(POSTGIS);

        GeometryFactoryProvider.setDefaultSrid(4326);
        GeometryStrategyFactory.setDefaultStrategy(MYSQL);
        assertThat(handler.getDefaultSrid()).isEqualTo(4326);
        assertThat(handler.getStrategy()).isSameAs(MYSQL);

        PointTypeHandler explicit = new PointTypeHandler(0, POSTGIS);
        assertThat(explicit.defaultSrid).isZero();
        assertThat(explicit.getDefaultSrid()).isZero();
        assertThat(explicit.getStrategy()).isSameAs(POSTGIS);
    }

    // ==================== writes ====================

    @Test
    void writesNeverModifyTheParameter() throws SQLException {
        GeometryCollection gc = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1))");
        Object userData = new Object();
        gc.setUserData(userData);
        for (GeometryHandlerStrategy strategy : new GeometryHandlerStrategy[] {MYSQL, POSTGIS}) {
            GeometryCollectionTypeHandler handler = new GeometryCollectionTypeHandler(3857, strategy);
            handler.setNonNullParameter(mock(PreparedStatement.class), 1, gc, JdbcType.OTHER);
        }
        assertThat(gc.getSRID()).isZero();
        assertThat(gc.getGeometryN(0).getSRID()).isZero();
        assertThat(gc.getUserData()).isSameAs(userData);
    }

    @Test
    void unknownStrategyValueTypeIsBoundWithSetObject() throws SQLException {
        Object custom = new Object();
        GeometryHandlerStrategy strategy = mock(GeometryHandlerStrategy.class);
        when(strategy.convertForDatabase(any(Geometry.class), anyInt())).thenReturn(custom);
        PreparedStatement ps = mock(PreparedStatement.class);

        new GeometryTypeHandler(4326, strategy).setNonNullParameter(ps, 3, wkt("POINT (1 2)"), null);

        verify(ps).setObject(3, custom);
    }

    @Test
    void nullConversionResultIsAnError() {
        GeometryHandlerStrategy strategy = mock(GeometryHandlerStrategy.class);
        assertThatThrownBy(() -> new GeometryTypeHandler(4326, strategy)
            .setNonNullParameter(mock(PreparedStatement.class), 1, wkt("POINT (1 2)"), null))
            .isInstanceOf(SQLException.class)
            .hasMessageContaining("converted a Point to null");
    }

    @Test
    void conversionFailuresKeepTheCause() {
        GeometryHandlerStrategy strategy = mock(GeometryHandlerStrategy.class);
        IllegalStateException boom = new IllegalStateException("boom");
        when(strategy.convertForDatabase(any(Geometry.class), anyInt())).thenThrow(boom);
        assertThatThrownBy(() -> new PointTypeHandler(4326, strategy)
            .setNonNullParameter(mock(PreparedStatement.class), 1, (Point) wkt("POINT (1 2)"), null))
            .isInstanceOf(SQLException.class)
            .hasMessage("Failed to convert Point to database format: boom")
            .hasCause(boom);
    }

    @Test
    void nullParameterIsRejected() {
        assertThatThrownBy(() -> new PointTypeHandler(4326, MYSQL)
            .setNonNullParameter(mock(PreparedStatement.class), 1, null, null))
            .isInstanceOf(SQLException.class);
    }

    // ==================== validation ====================

    @Test
    void basicValidationRejectsNonFiniteOrdinates() {
        GeometryFactory f = new GeometryFactory();
        PointTypeHandler points = new PointTypeHandler(4326, MYSQL);
        assertThatThrownBy(() -> points.setNonNullParameter(mock(PreparedStatement.class), 1,
            f.createPoint(new Coordinate(Double.NaN, 1)), null))
            .isInstanceOf(SQLException.class)
            .hasMessage("Invalid Point geometry: X coordinate is NaN");
        assertThatThrownBy(() -> points.setNonNullParameter(mock(PreparedStatement.class), 1,
            f.createPoint(new Coordinate(1, Double.NEGATIVE_INFINITY)), null))
            .hasMessage("Invalid Point geometry: Y coordinate is infinite");

        Polygon polygon = f.createPolygon(new Coordinate[] {new Coordinate(0, 0), new Coordinate(1, 0),
            new Coordinate(1, Double.POSITIVE_INFINITY), new Coordinate(0, 0)});
        GeometryTypeHandler generic = new GeometryTypeHandler(4326, MYSQL);
        assertThatThrownBy(() -> generic.setNonNullParameter(mock(PreparedStatement.class), 1, polygon, null))
            .hasMessage("Invalid Polygon geometry: Y coordinate is infinite");
    }

    @Test
    void basicValidationAcceptsOgcInvalidGeometries() throws SQLException {
        PreparedStatement ps = mock(PreparedStatement.class);
        new PolygonTypeHandler(4326, MYSQL).setNonNullParameter(ps, 1,
            (Polygon) wkt("POLYGON ((0 0, 2 2, 2 0, 0 2, 0 0))"), null);
        new LineStringTypeHandler(4326, MYSQL).setNonNullParameter(ps, 2,
            (LineString) wkt("LINESTRING (1 1, 1 1)"), null);
        new MultiPolygonTypeHandler(4326, MYSQL).setNonNullParameter(ps, 3, (MultiPolygon) wkt(
            "MULTIPOLYGON (((0 0, 1 0, 1 1, 0 1, 0 0)), ((1 0, 2 0, 2 1, 1 1, 1 0)))"), null);
        verify(ps).setBytes(eq(1), any(byte[].class));
        verify(ps).setBytes(eq(2), any(byte[].class));
        verify(ps).setBytes(eq(3), any(byte[].class));
    }

    @Test
    void fullValidationRejectsOgcInvalidGeometries() {
        GeometryDefaults.setWriteValidation(GeometryValidation.FULL);
        assertThatThrownBy(() -> new PolygonTypeHandler(4326, MYSQL).setNonNullParameter(
            mock(PreparedStatement.class), 1, (Polygon) wkt("POLYGON ((0 0, 2 2, 2 0, 0 2, 0 0))"), null))
            .isInstanceOf(SQLException.class)
            .hasMessageStartingWith("Invalid Polygon geometry: Self-intersection")
            .hasMessageContaining("at or near point");
        GeometryFactory f = new GeometryFactory();
        assertThatThrownBy(() -> new PointTypeHandler(4326, MYSQL).setNonNullParameter(
            mock(PreparedStatement.class), 1, f.createPoint(new Coordinate(Double.NaN, 1)), null))
            .hasMessage("Invalid Point geometry: X coordinate is NaN");
    }

    @Test
    void fullValidationAcceptsValidGeometries() {
        GeometryDefaults.setWriteValidation(GeometryValidation.FULL);
        assertThatCode(() -> new PolygonTypeHandler(4326, MYSQL).setNonNullParameter(
            mock(PreparedStatement.class), 1, (Polygon) wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))"), null))
            .doesNotThrowAnyException();
    }

    @Test
    void noValidationAcceptsAnything() throws SQLException {
        GeometryDefaults.setWriteValidation(GeometryValidation.NONE);
        PreparedStatement ps = mock(PreparedStatement.class);
        new PointTypeHandler(4326, POSTGIS).setNonNullParameter(ps, 1,
            new GeometryFactory().createPoint(new Coordinate(Double.NaN, Double.NaN)), null);
        verify(ps).setObject(eq(1), any(String.class), eq(Types.OTHER));
    }

    @ParameterizedTest
    @EnumSource(GeometryValidation.class)
    void emptyGeometriesPassEveryLevel(GeometryValidation level) throws SQLException {
        GeometryDefaults.setWriteValidation(level);
        PreparedStatement ps = mock(PreparedStatement.class);
        new PointTypeHandler(4326, POSTGIS).setNonNullParameter(ps, 1, (Point) wkt("POINT EMPTY"), null);
        new LineStringTypeHandler(4326, POSTGIS).setNonNullParameter(ps, 2, (LineString) wkt("LINESTRING EMPTY"), null);
        new PolygonTypeHandler(4326, POSTGIS).setNonNullParameter(ps, 3, (Polygon) wkt("POLYGON EMPTY"), null);
        new GeometryCollectionTypeHandler(4326, POSTGIS).setNonNullParameter(ps, 4,
            (GeometryCollection) wkt("GEOMETRYCOLLECTION EMPTY"), null);
        verify(ps).setObject(eq(1), any(String.class), eq(Types.OTHER));
        verify(ps).setObject(eq(4), any(String.class), eq(Types.OTHER));
    }

    @Test
    void subclassValidationOverrideIsHonoured() {
        PointTypeHandler strict = new PointTypeHandler(4326, MYSQL) {
            @Override
            protected void validateGeometry(Point point) throws SQLException {
                if (point.getX() < 0) {
                    throw new SQLException("negative longitude");
                }
            }
        };
        assertThatThrownBy(() -> strict.setNonNullParameter(mock(PreparedStatement.class), 1,
            (Point) wkt("POINT (-1 2)"), null)).hasMessage("negative longitude");
    }

    // ==================== reads ====================

    @Test
    void readsNativeMySqlValues() throws SQLException {
        byte[] stored = WkbSupport.writeSridPrefixed(wkt("POINT (1 2)"), 4326);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes("pt")).thenReturn(stored);
        when(rs.getBytes(2)).thenReturn(stored);
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getBytes(1)).thenReturn(stored);
        PointTypeHandler handler = new PointTypeHandler(4326, MYSQL);

        assertThat(handler.getNullableResult(rs, "pt").getSRID()).isEqualTo(4326);
        assertThat(handler.getNullableResult(rs, 2).getX()).isEqualTo(1.0);
        assertThat(handler.getNullableResult(cs, 1).getY()).isEqualTo(2.0);
        verify(rs, never()).getString(anyString());
    }

    @Test
    void readsNativePostgisValuesWithLazyStrategy() throws SQLException {
        GeometryStrategyFactory.setDefaultStrategy(POSTGIS);
        ResultSet rs = mock(ResultSet.class);
        String nativeText = ((String) POSTGIS.convertForDatabase(
            wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))", 4326))).toUpperCase(java.util.Locale.ROOT);
        when(rs.getString("mpg")).thenReturn(nativeText);
        MultiPolygon mp = new MultiPolygonTypeHandler().getNullableResult(rs, "mpg");
        assertThat(mp.getNumGeometries()).isEqualTo(1);
        assertThat(mp.getGeometryN(0).getSRID()).isEqualTo(4326);
    }

    @Test
    void sqlNullReadsAsNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        assertThat(new PointTypeHandler(4326, MYSQL).getNullableResult(rs, "pt")).isNull();
        assertThat(new PointTypeHandler(4326, POSTGIS).getNullableResult(rs, 1)).isNull();
        assertThat(new PointTypeHandler(4326, POSTGIS).getNullableResult(mock(CallableStatement.class), 1)).isNull();
    }

    @Test
    void typeMismatchIsAClearError() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes("boundary")).thenReturn(WkbSupport.writeSridPrefixed(
            wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))"), 4326));
        when(rs.getBytes(7)).thenReturn(WkbSupport.writeSridPrefixed(wkt("POINT (0 0)"), 4326));

        assertThatThrownBy(() -> new PolygonTypeHandler(4326, MYSQL).getNullableResult(rs, "boundary"))
            .isInstanceOf(SQLException.class)
            .hasMessage("column 'boundary' contains a MultiPolygon but the mapped type is "
                + "org.locationtech.jts.geom.Polygon");
        assertThatThrownBy(() -> new LineStringTypeHandler(4326, MYSQL).getNullableResult(rs, 7))
            .hasMessageContaining("column 7 contains a Point");
    }

    @Test
    void collectionAndGenericHandlersAcceptSubtypes() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes(1)).thenReturn(WkbSupport.writeSridPrefixed(
            wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))"), 4326));

        assertThat(new GeometryCollectionTypeHandler(4326, MYSQL).getNullableResult(rs, 1))
            .isInstanceOf(MultiPolygon.class);
        assertThat(new GeometryTypeHandler(4326, MYSQL).getNullableResult(rs, 1)).isInstanceOf(MultiPolygon.class);
    }

    @Test
    void malformedValuesBecomeSqlExceptionsWithCause() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("geom")).thenReturn("not hex");
        when(rs.getString(5)).thenReturn("0101000020e6100000");
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(2)).thenReturn("zz");
        GeometryTypeHandler handler = new GeometryTypeHandler(4326, POSTGIS);

        assertThatThrownBy(() -> handler.getNullableResult(rs, "geom"))
            .isInstanceOf(SQLException.class)
            .hasMessageStartingWith("Failed to read Geometry from column 'geom'")
            .hasCauseInstanceOf(WkbParseException.class);
        assertThatThrownBy(() -> handler.getNullableResult(rs, 5))
            .hasMessageStartingWith("Failed to read Geometry from column 5")
            .hasRootCauseInstanceOf(org.locationtech.jts.io.ParseException.class);
        assertThatThrownBy(() -> handler.getNullableResult(cs, 2))
            .isInstanceOf(SQLException.class)
            .hasCauseInstanceOf(WkbParseException.class);
    }

    @Test
    void driverExceptionsPropagateUnchanged() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        SQLException driver = new SQLException("connection reset");
        when(rs.getBytes("pt")).thenThrow(driver);
        assertThatThrownBy(() -> new PointTypeHandler(4326, MYSQL).getNullableResult(rs, "pt")).isSameAs(driver);
    }

    @Test
    void readPathUsesTheStrategy() throws SQLException {
        GeometryHandlerStrategy strategy = mock(GeometryHandlerStrategy.class);
        ResultSet rs = mock(ResultSet.class);
        Point expected = (Point) wkt("POINT (9 9)", 4326);
        when(strategy.read(rs, "custom")).thenReturn(expected);
        assertThat(new PointTypeHandler(4326, strategy).getNullableResult(rs, "custom")).isSameAs(expected);
    }

    // ==================== legacy protected API ====================

    @Test
    @SuppressWarnings("deprecation")
    void legacyProtectedMethodsStillWork() {
        GeometryStrategyFactory.setDefaultStrategy(POSTGIS);
        PolygonTypeHandler handler = new PolygonTypeHandler();
        String ewkb = (String) POSTGIS.convertForDatabase(wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))", 3857));

        assertThat(handler.parseGeometry(ewkb).getSRID()).isEqualTo(3857);
        assertThat(handler.parseGeometry(null)).isNull();
        assertThat(handler.parseGeometry("")).isNull();
        String pointHex = (String) POSTGIS.convertForDatabase(wkt("POINT (1 1)", 4326));
        assertThatThrownBy(() -> handler.parseGeometry(pointHex))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a Polygon");

        assertThat(handler.getGeometryTypeName()).isEqualTo("Polygon");
        assertThat(new GeometryTypeHandler().getGeometryTypeName()).isEqualTo("Geometry");

        Geometry g = wkt("POINT (1 2)");
        GeometryFactoryProvider.setDefaultSrid(3857);
        handler.ensureSrid(g);
        assertThat(g.getSRID()).isEqualTo(3857);
        Geometry tagged = wkt("POINT (1 2)", 4326);
        handler.ensureSrid(tagged);
        assertThat(tagged.getSRID()).isEqualTo(4326);
    }

    @ParameterizedTest
    @ValueSource(classes = {PointTypeHandler.class, LineStringTypeHandler.class, PolygonTypeHandler.class,
        MultiPointTypeHandler.class, MultiLineStringTypeHandler.class,
        MultiPolygonTypeHandler.class, GeometryCollectionTypeHandler.class, GeometryTypeHandler.class})
    void everyHandlerHasTheThreeConstructorsAndMapsItsType(Class<?> type) throws Exception {
        AbstractGeometryTypeHandler<?> noArg = (AbstractGeometryTypeHandler<?>) type.getConstructor().newInstance();
        AbstractGeometryTypeHandler<?> withSrid =
            (AbstractGeometryTypeHandler<?>) type.getConstructor(int.class).newInstance(3857);
        AbstractGeometryTypeHandler<?> withBoth = (AbstractGeometryTypeHandler<?>) type
            .getConstructor(int.class, GeometryHandlerStrategy.class).newInstance(0, POSTGIS);

        String expected = type.getSimpleName().replace("TypeHandler", "");
        assertThat(noArg.getGeometryTypeName()).isEqualTo(expected);
        assertThat(noArg.getRawType().getTypeName()).isEqualTo("org.locationtech.jts.geom." + expected);
        assertThat(withSrid.getDefaultSrid()).isEqualTo(3857);
        assertThat(withBoth.getStrategy()).isSameAs(POSTGIS);
        org.apache.ibatis.type.MappedTypes mapped = type.getAnnotation(org.apache.ibatis.type.MappedTypes.class);
        assertThat(mapped.value()).extracting(Class::getSimpleName).containsExactly(expected);
    }

    @Test
    void genericSubclassFallsBackToGeometry() throws SQLException {
        GenericHandler<Geometry> handler = new GenericHandler<>();
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes(1)).thenReturn(WkbSupport.writeSridPrefixed(wkt("LINESTRING (0 0, 1 1)"), 4326));
        GeometryStrategyFactory.setDefaultStrategy(MYSQL);
        Geometry read = handler.getNullableResult(rs, 1);
        assertThat(read).isInstanceOf(LineString.class);
        assertThat(handler.getGeometryTypeName()).isEqualTo("Geometry");
    }

    private static int sridWritten(AbstractGeometryTypeHandler<?> handler, Geometry geometry) throws SQLException {
        @SuppressWarnings("unchecked")
        AbstractGeometryTypeHandler<Geometry> h = (AbstractGeometryTypeHandler<Geometry>) handler;
        GeometryStrategyFactory.setDefaultStrategy(MYSQL);
        PreparedStatement ps = mock(PreparedStatement.class);
        h.setNonNullParameter(ps, 1, geometry, null);
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(ps).setBytes(eq(1), bytes.capture());
        return WkbSupport.readSridPrefixed(bytes.getValue()).getSRID();
    }

    /**
     * Handler whose type parameter is not resolvable from the class hierarchy.
     */
    static final class GenericHandler<G extends Geometry> extends AbstractGeometryTypeHandler<G> {
    }
}
