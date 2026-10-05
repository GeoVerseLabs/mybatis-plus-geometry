package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import io.github.geoverselabs.mybatis.geometry.support.GeometryDefaults;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBWriter;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Stream;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.hex;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.sameGeometry;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostGISWkbCodecTest {

    private static final String POINT_EWKB = "0101000020e6100000000000000000f03f0000000000000040";

    private final PostGISWkbCodec codec = new PostGISWkbCodec();

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
        GeometryDefaults.reset();
    }

    static Stream<String> samples() {
        return TestGeometries.SAMPLE_WKT.stream();
    }

    // ==================== encoding ====================

    @Test
    void encodesHexEwkbWithSrid() {
        assertThat(codec.encode(wkt("POINT (1 2)", 4326))).isEqualTo(POINT_EWKB);
    }

    @Test
    void encodeUsesConfiguredDefaultForSridZeroAndAllowsZero() {
        assertThat(codec.encode(wkt("POINT (1 2)"))).isEqualTo(POINT_EWKB);

        GeometryFactoryProvider.setDefaultSrid(3857);
        assertThat((String) codec.encode(wkt("POINT (1 2)"))).startsWith("0101000020110f0000");

        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(codec.encode(wkt("POINT (1 2)")))
            .isEqualTo("0101000000000000000000f03f0000000000000040");
    }

    @Test
    void encodeDoesNotModifyGeometry() {
        Geometry g = wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1))");
        String encoded = (String) codec.encode(g, 3857);

        assertThat(encoded).startsWith("0107000020110f0000");
        assertThat(g.getSRID()).isZero();
        assertThat(g.getGeometryN(1).getSRID()).isZero();
    }

    @ParameterizedTest
    @MethodSource("samples")
    void matchesJtsEwkbOfSridTaggedCopy(String text) {
        Geometry reference = wkt(text);
        reference.setSRID(4326);
        String expected = hex(new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, true).write(reference));

        assertThat(codec.encode(wkt(text), 4326)).isEqualTo(expected);
    }

    @Test
    void zIsDroppedByDefault() {
        String encoded = (String) codec.encode(wkt("POINT Z (1 2 3)", 4326));
        assertThat(encoded).isEqualTo(POINT_EWKB);
    }

    @Test
    void zIsWrittenWhenPreserveZIsEnabled() {
        GeometryDefaults.setPreserveZ(true);
        String encoded = (String) codec.encode(wkt("POINT Z (1 2 3)", 4326));
        assertThat(encoded).isEqualTo("01010000a0e6100000000000000000f03f00000000000000400000000000000840");
        assertThat(codec.decode(encoded).getCoordinate().getZ()).isEqualTo(3.0);
    }

    @Test
    void preserveZWritesTwoDimensionsWhenAllZAreNaN() {
        GeometryDefaults.setPreserveZ(true);
        Point p = new GeometryFactory().createPoint(new Coordinate(1, 2));
        assertThat(codec.encode(p, 4326)).isEqualTo(POINT_EWKB);
    }

    @Test
    void preserveZWritesNaNForMembersWithoutZ() {
        GeometryDefaults.setPreserveZ(true);
        Geometry g = wkt("GEOMETRYCOLLECTION (POINT (1 2), POINT Z (3 4 5))", 4326);
        Geometry decoded = codec.decode(codec.encode(g));
        assertThat(decoded.getGeometryN(0).getCoordinate().getZ()).isNaN();
        assertThat(decoded.getGeometryN(1).getCoordinate().getZ()).isEqualTo(5.0);
    }

    @Test
    void nullHandling() {
        assertThat(codec.encode(null)).isNull();
        assertThat(codec.encode(null, 4326)).isNull();
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode("\\x")).isNull();
        assertThat(codec.decode(new byte[0])).isNull();
    }

    // ==================== decoding ====================

    @Test
    void decodesNativeUppercaseHexEwkb() {
        Geometry g = codec.decode(POINT_EWKB.toUpperCase(Locale.ROOT));
        assertThat(g.getCoordinate().x).isEqualTo(1.0);
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @Test
    void decodesByteaHexOutput() {
        Geometry g = codec.decode("\\x" + POINT_EWKB);
        assertThat(g.getCoordinate().y).isEqualTo(2.0);
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @Test
    void decodesRawAndAsciiBytes() {
        assertThat(codec.decode(hex(POINT_EWKB)).getSRID()).isEqualTo(4326);
        assertThat(codec.decode(POINT_EWKB.getBytes(StandardCharsets.US_ASCII)).getSRID()).isEqualTo(4326);
        assertThat(codec.decode(("\\x" + POINT_EWKB).getBytes(StandardCharsets.US_ASCII)).getSRID())
            .isEqualTo(4326);
    }

    @Test
    void decodesBigEndianEwkb() {
        Geometry reference = wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");
        reference.setSRID(3857);
        byte[] be = new WKBWriter(2, ByteOrderValues.BIG_ENDIAN, true).write(reference);

        Geometry g = codec.decode(hex(be));
        assertThat(g).isInstanceOf(Polygon.class);
        assertThat(g.equalsExact(reference)).isTrue();
        assertThat(g.getSRID()).isEqualTo(3857);
    }

    @Test
    void plainWkbHasSridZeroNotTheConfiguredDefault() {
        GeometryFactoryProvider.setDefaultSrid(4326);
        Geometry g = codec.decode("0101000000000000000000f03f0000000000000040");
        assertThat(g.getSRID()).isZero();
        assertThat(g.getFactory().getSRID()).isZero();
    }

    @Test
    void decodesLegacySridPrefixedFormat() {
        // former interceptor output: lpad/to_hex SRID prefix (little-endian) + ST_AsBinary
        String legacy = "e6100000" + "0101000000" + "000000000000f03f" + "0000000000000040";
        Geometry g = codec.decode(legacy);
        assertThat(g.getCoordinate().x).isEqualTo(1.0);
        assertThat(g.getSRID()).isEqualTo(4326);

        String legacySridZero = "00000000" + "0101000000" + "000000000000f03f" + "0000000000000040";
        Geometry zero = codec.decode(legacySridZero);
        assertThat(zero.getCoordinate().x).isEqualTo(1.0);
        assertThat(zero.getSRID()).isZero();
    }

    @ParameterizedTest
    @MethodSource("samples")
    void roundTripsEveryTypeIncludingEmpties(String text) {
        Geometry g = wkt(text, 4326);
        String encoded = (String) codec.encode(g);
        Geometry decoded = codec.decode(encoded);
        assertThat(sameGeometry(g, decoded)).as("%s -> %s", g, decoded).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(4326);
        assertThat(sameGeometry(g, codec.decode(encoded.toUpperCase(Locale.ROOT)))).isTrue();
        assertThat(sameGeometry(g, codec.decode("\\x" + encoded))).isTrue();
    }

    @Test
    void nonDefaultSridPropagatesToCollectionMembers() {
        Geometry g = wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)), ((2 2, 3 2, 3 3, 2 2)))");
        MultiPolygon decoded = (MultiPolygon) codec.decode(codec.encode(g, 2154));
        assertThat(decoded.getSRID()).isEqualTo(2154);
        for (int i = 0; i < decoded.getNumGeometries(); i++) {
            assertThat(decoded.getGeometryN(i).getSRID()).isEqualTo(2154);
            assertThat(decoded.getGeometryN(i).getFactory().getSRID()).isEqualTo(2154);
        }
    }

    @Test
    void packedSequenceType() {
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        Polygon p = (Polygon) codec.decode(codec.encode(wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))", 4326)));
        assertThat(p.getExteriorRing().getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.class);
    }

    @Test
    void rejectsMeasuredGeometries() {
        assertThatThrownBy(() -> codec.decode("0101000060e6100000000000000000f03f00000000000000400000000000000840"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("M ordinates");
    }

    @Test
    void rejectsUnsupportedValueType() {
        assertThatThrownBy(() -> codec.decode(1L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("java.lang.Long");
    }

    @Test
    void rejectsMalformedHex() {
        assertThatThrownBy(() -> codec.decode("0101000020e61000000000zz"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid hex character");
    }

    @Test
    void rejectsMalformedWkbAndKeepsCause() {
        assertThatThrownBy(() -> codec.decode("0109000020e6100000000000000000f03f0000000000000040"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("0109000020e61000")
            .hasCauseInstanceOf(org.locationtech.jts.io.ParseException.class);
    }
}
