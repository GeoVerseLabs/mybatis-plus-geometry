package io.github.geoverselabs.mybatis.geometry.util;

import io.github.geoverselabs.mybatis.geometry.codec.MySQLWkbCodec;
import io.github.geoverselabs.mybatis.geometry.codec.TestGeometries;
import io.github.geoverselabs.mybatis.geometry.exception.WkbParseException;
import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;

import java.util.Locale;
import java.util.stream.Stream;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.sameGeometry;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WkbUtilTest {

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
    }

    static Stream<String> samples() {
        return TestGeometries.SAMPLE_WKT.stream();
    }

    @Test
    void pointHexIsUppercaseSridPrefixedWkb() {
        String hex = WkbUtil.toWkb((Point) wkt("POINT (1 2)", 4326));
        assertThat(hex).isEqualTo("E6100000" + "0101000000" + "000000000000F03F" + "0000000000000040");
    }

    @ParameterizedTest
    @MethodSource("samples")
    void roundTripsEveryTypeIncludingEmpties(String text) {
        Geometry g = wkt(text, 3857);
        String hex = WkbUtil.toWkb(g);
        assertThat(hex).isEqualTo(hex.toUpperCase(Locale.ROOT));
        Geometry decoded = WkbUtil.fromWkb(hex);
        assertThat(sameGeometry(g, decoded)).as("%s -> %s", g, decoded).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(3857);
        assertThat(WkbUtil.toWkbBytes(g)).isEqualTo(java.util.HexFormat.of().parseHex(hex));
    }

    @ParameterizedTest
    @MethodSource("samples")
    void sameBytesAsMySqlCodec(String text) {
        Geometry g = wkt(text, 4326);
        assertThat(WkbUtil.toWkbBytes(g)).isEqualTo((byte[]) new MySQLWkbCodec().encode(g));
    }

    @Test
    void typedOverloadsDelegateToTheSameEncoding() {
        Point point = (Point) wkt("POINT (1 2)", 4326);
        LineString line = (LineString) wkt("LINESTRING (1 2, 3 4)", 4326);
        Polygon polygon = (Polygon) wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))", 4326);
        MultiPoint multiPoint = (MultiPoint) wkt("MULTIPOINT ((1 2))", 4326);
        MultiLineString multiLine = (MultiLineString) wkt("MULTILINESTRING ((1 2, 3 4))", 4326);
        MultiPolygon multiPolygon = (MultiPolygon) wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))", 4326);
        GeometryCollection collection = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2))", 4326);

        assertThat(WkbUtil.toWkb(point)).isEqualTo(WkbUtil.toWkb((Geometry) point));
        assertThat(WkbUtil.toWkbBytes(point)).isEqualTo(WkbUtil.toWkbBytes((Geometry) point));
        assertThat(WkbUtil.fromWkbAsPoint(WkbUtil.toWkb(point)).equalsExact(point)).isTrue();

        assertThat(WkbUtil.toWkbBytes(line)).isEqualTo(WkbUtil.toWkbBytes((Geometry) line));
        assertThat(WkbUtil.fromWkbAsLineString(WkbUtil.toWkb(line)).equalsExact(line)).isTrue();

        assertThat(WkbUtil.toWkbBytes(polygon)).isEqualTo(WkbUtil.toWkbBytes((Geometry) polygon));
        assertThat(WkbUtil.fromWkbAsPolygon(WkbUtil.toWkb(polygon)).equalsExact(polygon)).isTrue();

        assertThat(WkbUtil.toWkbBytes(multiPoint)).isEqualTo(WkbUtil.toWkbBytes((Geometry) multiPoint));
        assertThat(WkbUtil.fromWkbAsMultiPoint(WkbUtil.toWkb(multiPoint)).equalsExact(multiPoint)).isTrue();

        assertThat(WkbUtil.toWkbBytes(multiLine)).isEqualTo(WkbUtil.toWkbBytes((Geometry) multiLine));
        assertThat(WkbUtil.fromWkbAsMultiLineString(WkbUtil.toWkb(multiLine)).equalsExact(multiLine)).isTrue();

        assertThat(WkbUtil.toWkbBytes(multiPolygon)).isEqualTo(WkbUtil.toWkbBytes((Geometry) multiPolygon));
        assertThat(WkbUtil.fromWkbAsMultiPolygon(WkbUtil.toWkb(multiPolygon)).equalsExact(multiPolygon)).isTrue();

        assertThat(WkbUtil.toWkbBytes(collection)).isEqualTo(WkbUtil.toWkbBytes((Geometry) collection));
        assertThat(WkbUtil.fromWkbAsGeometryCollection(WkbUtil.toWkb(collection)).equalsExact(collection))
            .isTrue();
        assertThat(WkbUtil.fromWkbAsGeometryCollection(WkbUtil.toWkb(multiPolygon))).isInstanceOf(MultiPolygon.class);
    }

    @Test
    void nullsAndEmptyStrings() {
        assertThat(WkbUtil.toWkb((Point) null)).isNull();
        assertThat(WkbUtil.toWkb((Geometry) null)).isNull();
        assertThat(WkbUtil.toWkbBytes((Polygon) null)).isNull();
        assertThat(WkbUtil.toWkbBytes((Geometry) null)).isNull();
        assertThat(WkbUtil.fromWkb(null)).isNull();
        assertThat(WkbUtil.fromWkb("")).isNull();
        assertThat(WkbUtil.fromWkbAsPoint("")).isNull();
        assertThat(WkbUtil.fromWkbAsMultiPolygon(null)).isNull();
    }

    @Test
    void sridZeroUsesConfiguredDefaultWithoutHardCoded4326() {
        Point p = (Point) wkt("POINT (500000 4000000)");
        assertThat(WkbUtil.toWkb(p)).startsWith("E6100000");

        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(WkbUtil.toWkb(p)).startsWith("00000000");
        assertThat(WkbUtil.fromWkb(WkbUtil.toWkb(p)).getSRID()).isZero();

        GeometryFactoryProvider.setDefaultSrid(32650);
        assertThat(WkbUtil.fromWkb(WkbUtil.toWkb(p)).getSRID()).isEqualTo(32650);
        assertThat(p.getSRID()).isZero();
    }

    @Test
    void emptyPointAndPolygonEncode() {
        Point empty = (Point) wkt("POINT EMPTY", 4326);
        assertThat(WkbUtil.fromWkbAsPoint(WkbUtil.toWkb(empty)).isEmpty()).isTrue();
        Polygon emptyPolygon = (Polygon) wkt("POLYGON EMPTY", 4326);
        // 0 rings, as JTS writes it (not one ring of 0 points)
        assertThat(WkbUtil.toWkb(emptyPolygon)).isEqualTo("E6100000" + "0103000000" + "00000000");
        assertThat(WkbUtil.fromWkbAsPolygon(WkbUtil.toWkb(emptyPolygon)).isEmpty()).isTrue();
    }

    @Test
    void zIsNotWritten() {
        Point p = (Point) wkt("POINT Z (1 2 3)", 4326);
        assertThat(WkbUtil.toWkb(p)).hasSize(50);
        assertThat(WkbUtil.fromWkb(WkbUtil.toWkb(p)).getCoordinate().getZ()).isNaN();
    }

    @Test
    void parsedGeometriesUseConfiguredSequenceType() {
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        LineString line = WkbUtil.fromWkbAsLineString(WkbUtil.toWkb(wkt("LINESTRING (1 2, 3 4)", 4326)));
        assertThat(line.getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.class);
    }

    @Test
    void lowercaseHexIsAccepted() {
        String hex = WkbUtil.toWkb(wkt("POINT (1 2)", 4326)).toLowerCase(Locale.ROOT);
        assertThat(WkbUtil.fromWkbAsPoint(hex).getX()).isEqualTo(1.0);
    }

    @Test
    void typeMismatchIsReported() {
        String hex = WkbUtil.toWkb(wkt("POINT (1 2)", 4326));
        assertThatThrownBy(() -> WkbUtil.fromWkbAsPolygon(hex))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("WKB string is not a Polygon geometry, got: Point");
        assertThatThrownBy(() -> WkbUtil.fromWkbAsMultiPoint(hex))
            .hasMessage("WKB string is not a MultiPoint geometry, got: Point");
    }

    @Test
    void malformedInputKeepsCauseAndPrefix() {
        assertThatThrownBy(() -> WkbUtil.fromWkb("E61000000109000000000000000000F03F0000000000000040"))
            .isInstanceOf(WkbParseException.class)
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Failed to parse WKB string")
            .hasMessageContaining("E61000000109")
            .hasCauseInstanceOf(IllegalArgumentException.class)
            .satisfies(e -> assertThat(((WkbParseException) e).getHexPrefix()).startsWith("E61000000109"));
        assertThatThrownBy(() -> WkbUtil.fromWkb("XYZ1"))
            .isInstanceOf(WkbParseException.class);
    }

    @Test
    @SuppressWarnings("deprecation")
    void cleanupThreadLocalIsANoOp() {
        WkbUtil.cleanupThreadLocal();
        assertThat(WkbUtil.fromWkbAsPoint(WkbUtil.toWkb(wkt("POINT (1 2)", 4326))).getY()).isEqualTo(2.0);
    }

    @Test
    void constantsAreUnchanged() {
        assertThat(WkbUtil.DEFAULT_SRID).isEqualTo(4326);
        assertThat(WkbUtil.getDefaultSrid()).isEqualTo(4326);
        assertThat(WkbUtil.GeometryType.MULTIPOLYGON.getCode()).isEqualTo(6);
    }
}
