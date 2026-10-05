package io.github.geoverselabs.mybatis.geometry.strategy;

import io.github.geoverselabs.mybatis.geometry.codec.TestGeometries;
import io.github.geoverselabs.mybatis.geometry.codec.WkbCodec;
import io.github.geoverselabs.mybatis.geometry.exception.GeometryConversionException;
import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.nio.charset.StandardCharsets;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.hex;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuiltInStrategiesTest {

    private static final String MYSQL_POINT = "e6100000" + "0101000000" + "000000000000f03f" + "0000000000000040";
    private static final String EWKB_POINT = "0101000020e6100000000000000000f03f0000000000000040";

    private final MySQLGeometryStrategy mysql = new MySQLGeometryStrategy();
    private final PostGISGeometryStrategy postgis = new PostGISGeometryStrategy();

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
    }

    @Test
    void supportedTypesAndInputFunction() {
        assertThat(mysql.getSupportedDatabaseType()).isEqualTo(DatabaseType.MYSQL);
        assertThat(postgis.getSupportedDatabaseType()).isEqualTo(DatabaseType.POSTGRESQL);
        assertThat(mysql.getGeometryInputFunction()).isEqualTo("?");
        assertThat(postgis.getGeometryInputFunction()).isEqualTo("?");
    }

    @Test
    void selectWrapping() {
        assertThat(mysql.wrapColumnForSelect("location")).isEqualTo("HEX(location) AS location");
        assertThat(mysql.wrapColumnForSelect("t.location")).isEqualTo("HEX(t.location) AS location");
        assertThat(postgis.wrapColumnForSelect("geom")).isEqualTo("encode(ST_AsEWKB(geom), 'hex') AS geom");
        assertThat(postgis.wrapColumnForSelect("t.geom")).isEqualTo("encode(ST_AsEWKB(t.geom), 'hex') AS geom");
    }

    // ==================== MySQL ====================

    @Test
    void mysqlReadsBytesByLabelIndexAndCallable() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getBytes("pt")).thenReturn(hex(MYSQL_POINT));
        when(rs.getBytes(3)).thenReturn(MYSQL_POINT.toUpperCase().getBytes(StandardCharsets.US_ASCII));
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getBytes(1)).thenReturn(hex(MYSQL_POINT));

        assertThat(((Point) mysql.read(rs, "pt")).getX()).isEqualTo(1.0);
        assertThat(((Point) mysql.read(rs, 3)).getY()).isEqualTo(2.0);
        assertThat(mysql.read(cs, 1).getSRID()).isEqualTo(4326);
        verify(rs, never()).getString(anyString());
        verify(rs, never()).getString(anyInt());
    }

    @Test
    void mysqlReadsSqlNull() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        assertThat(mysql.read(rs, "pt")).isNull();
        assertThat(mysql.read(rs, 1)).isNull();
        assertThat(mysql.read(mock(CallableStatement.class), 1)).isNull();
    }

    @Test
    void mysqlConvertsWithConfiguredDefaultSrid() {
        GeometryFactoryProvider.setDefaultSrid(3857);
        Geometry g = wkt("POINT (1 2)");
        assertThat(hex((byte[]) mysql.convertForDatabase(g))).startsWith("110f0000");
        assertThat(hex((byte[]) mysql.convertForDatabase(g, 0))).startsWith("00000000");
        assertThat(g.getSRID()).isZero();
        assertThat(mysql.convertForDatabase(null)).isNull();
        assertThat(mysql.convertForDatabase(null, 4326)).isNull();
    }

    @Test
    void mysqlParseFailureKeepsCause() {
        assertThatThrownBy(() -> mysql.parseFromDatabase("E61000000101000000"))
            .isInstanceOf(WkbParseException.class)
            .hasMessageContaining("Failed to decode geometry")
            .hasMessageContaining("E61000000101000000")
            .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> mysql.parseFromDatabase(new byte[] {9, 9, 9}))
            .isInstanceOf(WkbParseException.class)
            .hasMessageContaining("090909");
    }

    @Test
    void unexpectedValueTypesAreReported() {
        assertThatThrownBy(() -> mysql.parseFromDatabase(12))
            .isInstanceOf(GeometryConversionException.class)
            .isNotInstanceOf(WkbParseException.class)
            .hasMessageContaining("Unexpected database value type")
            .hasMessageContaining("java.lang.Integer");
        assertThatThrownBy(() -> postgis.parseFromDatabase(new Object()))
            .isInstanceOf(GeometryConversionException.class)
            .hasMessageContaining("java.lang.Object");
        assertThat(mysql.parseFromDatabase(null)).isNull();
        assertThat(postgis.parseFromDatabase(null)).isNull();
    }

    // ==================== PostGIS ====================

    @Test
    void postgisReadsStringsByLabelIndexAndCallable() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("geom")).thenReturn(EWKB_POINT.toUpperCase());
        when(rs.getString(2)).thenReturn("\\x" + EWKB_POINT);
        CallableStatement cs = mock(CallableStatement.class);
        when(cs.getString(1)).thenReturn(EWKB_POINT);

        assertThat(postgis.read(rs, "geom").getSRID()).isEqualTo(4326);
        assertThat(((Point) postgis.read(rs, 2)).getY()).isEqualTo(2.0);
        assertThat(postgis.read(cs, 1)).isInstanceOf(Point.class);
        assertThat(postgis.read(mock(ResultSet.class), "geom")).isNull();
    }

    @Test
    void postgisConvertsToHexEwkb() {
        assertThat(postgis.convertForDatabase(wkt("POINT (1 2)", 4326))).isEqualTo(EWKB_POINT);
        Geometry g = wkt("POINT (1 2)");
        assertThat(postgis.convertForDatabase(g, 4326)).isEqualTo(EWKB_POINT);
        assertThat(g.getSRID()).isZero();
    }

    @Test
    void postgisParsesLegacyAndEwkb() {
        Polygon polygon = (Polygon) wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))", 3857);
        String ewkb = (String) postgis.convertForDatabase(polygon);
        assertThat(postgis.parseFromDatabase(ewkb).equalsExact(polygon)).isTrue();
        assertThat(postgis.parseFromDatabase(hex(ewkb)).getSRID()).isEqualTo(3857);
        assertThat(postgis.parseFromDatabase(MYSQL_POINT).getSRID()).isEqualTo(4326);
    }

    @Test
    void postgisParseFailureKeepsCause() {
        assertThatThrownBy(() -> postgis.parseFromDatabase("0101000020e6100000"))
            .isInstanceOf(WkbParseException.class)
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .satisfies(e -> assertThat(e.getCause().getMessage()).contains("0101000020e6100000"));
    }

    @Test
    void encodingFailuresAreWrappedWithCause() {
        WkbCodec failing = mock(WkbCodec.class);
        IllegalStateException boom = new IllegalStateException("boom");
        when(failing.encode(any(Geometry.class), anyInt())).thenThrow(boom);

        assertThatThrownBy(() -> CodecSupport.encode(failing, wkt("POINT (1 2)"), 4326))
            .isInstanceOf(GeometryConversionException.class)
            .hasMessageContaining("Failed to encode geometry: boom")
            .hasMessageContaining("[type=Point]")
            .hasCause(boom);

        GeometryConversionException direct = new GeometryConversionException("direct");
        WkbCodec rethrowing = mock(WkbCodec.class);
        when(rethrowing.encode(any(Geometry.class), anyInt())).thenThrow(direct);
        assertThatThrownBy(() -> CodecSupport.encode(rethrowing, wkt("POINT (1 2)"), 4326)).isSameAs(direct);
        assertThat(CodecSupport.encode(failing, null, 4326)).isNull();
    }

    @Test
    void samplesRoundTripThroughBothStrategies() {
        for (String text : TestGeometries.SAMPLE_WKT) {
            Geometry g = wkt(text, 4326);
            assertThat(TestGeometries.sameGeometry(g, mysql.parseFromDatabase(mysql.convertForDatabase(g))))
                .as(text).isTrue();
            assertThat(TestGeometries.sameGeometry(g, postgis.parseFromDatabase(postgis.convertForDatabase(g))))
                .as(text).isTrue();
        }
    }
}
