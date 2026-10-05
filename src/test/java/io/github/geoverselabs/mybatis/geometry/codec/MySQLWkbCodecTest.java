package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
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

class MySQLWkbCodecTest {

    private static final String POINT_4326 = "e6100000" + "0101000000" + "000000000000f03f" + "0000000000000040";

    private final MySQLWkbCodec codec = new MySQLWkbCodec();

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
    }

    static Stream<String> samples() {
        return TestGeometries.SAMPLE_WKT.stream();
    }

    @Test
    void encodesInternalFormat() {
        assertThat(hex((byte[]) codec.encode(wkt("POINT (1 2)", 4326)))).isEqualTo(POINT_4326);
    }

    @Test
    void encodeUsesGeometrySridOrConfiguredDefault() {
        byte[] own = (byte[]) codec.encode(wkt("POINT (1 2)", 3857));
        assertThat(hex(own)).startsWith("110f0000");

        byte[] defaulted = (byte[]) codec.encode(wkt("POINT (1 2)"));
        assertThat(hex(defaulted)).startsWith("e6100000");

        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(hex((byte[]) codec.encode(wkt("POINT (1 2)")))).startsWith("00000000");

        GeometryFactoryProvider.setDefaultSrid(2154);
        assertThat(hex((byte[]) codec.encode(wkt("POINT (1 2)")))).startsWith("6a080000");
    }

    @Test
    void encodeWithExplicitSridDoesNotModifyGeometry() {
        Geometry g = wkt("MULTIPOINT ((1 2), (3 4))");
        byte[] bytes = (byte[]) codec.encode(g, 3857);

        assertThat(hex(bytes)).startsWith("110f0000");
        assertThat(g.getSRID()).isZero();
        assertThat(g.getGeometryN(0).getSRID()).isZero();
    }

    @Test
    void encodeWritesTwoDimensions() {
        byte[] bytes = (byte[]) codec.encode(wkt("POINT Z (1 2 3)"), 4326);
        assertThat(hex(bytes)).isEqualTo(POINT_4326);
        assertThat(codec.decode(bytes).getCoordinate().getZ()).isNaN();
    }

    @Test
    void nullHandling() {
        assertThat(codec.encode(null)).isNull();
        assertThat(codec.encode(null, 4326)).isNull();
        assertThat(codec.decode(null)).isNull();
        assertThat(codec.decode("")).isNull();
        assertThat(codec.decode(new byte[0])).isNull();
    }

    @Test
    void decodesRawInternalFormat() {
        Geometry g = codec.decode(hex(POINT_4326));
        assertThat(g).isInstanceOf(Point.class);
        assertThat(g.getCoordinate().x).isEqualTo(1.0);
        assertThat(g.getCoordinate().y).isEqualTo(2.0);
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void decodesHexStringCaseInsensitive(boolean upper) {
        String text = upper ? POINT_4326.toUpperCase(Locale.ROOT) : POINT_4326;
        Geometry g = codec.decode(text);
        assertThat(g.getCoordinate().y).isEqualTo(2.0);
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @Test
    void decodesAsciiHexBytesFromHexExpression() {
        byte[] ascii = POINT_4326.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        Geometry g = codec.decode(ascii);
        assertThat(g.getCoordinate().x).isEqualTo(1.0);
        assertThat(g.getSRID()).isEqualTo(4326);
    }

    @Test
    void decodesBigEndianWkbAfterPrefix() {
        byte[] wkb = new WKBWriter(2, ByteOrderValues.BIG_ENDIAN).write(wkt("LINESTRING (1 2, 3 4)"));
        byte[] value = new byte[wkb.length + 4];
        value[0] = 0x11;
        value[1] = 0x0F;
        System.arraycopy(wkb, 0, value, 4, wkb.length);

        Geometry g = codec.decode(value);
        assertThat(g.equalsExact(wkt("LINESTRING (1 2, 3 4)"))).isTrue();
        assertThat(g.getSRID()).isEqualTo(3857);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void roundTripsEveryTypeIncludingEmpties(String text) {
        Geometry g = wkt(text, 4326);
        byte[] encoded = (byte[]) codec.encode(g);
        assertThat(sameGeometry(g, codec.decode(encoded))).isTrue();
        assertThat(sameGeometry(g, codec.decode(hex(encoded)))).isTrue();
        assertThat(sameGeometry(g, codec.decode(hex(encoded).getBytes(StandardCharsets.US_ASCII)))).isTrue();
    }

    @Test
    void nonDefaultSridPropagatesToCollectionMembers() {
        Geometry g = wkt("GEOMETRYCOLLECTION (POINT (1 2), MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0))))", 3857);
        GeometryCollection decoded = (GeometryCollection) codec.decode(codec.encode(g));
        assertThat(decoded.getSRID()).isEqualTo(3857);
        assertThat(decoded.getGeometryN(0).getSRID()).isEqualTo(3857);
        MultiPolygon member = (MultiPolygon) decoded.getGeometryN(1);
        assertThat(member.getSRID()).isEqualTo(3857);
        assertThat(member.getGeometryN(0).getSRID()).isEqualTo(3857);
        assertThat(member.getGeometryN(0).buffer(1).getSRID()).isEqualTo(3857);
    }

    @Test
    void sridZeroRoundTrips() {
        GeometryFactoryProvider.setDefaultSrid(0);
        Geometry decoded = codec.decode(codec.encode(wkt("POINT (500000 4000000)")));
        assertThat(decoded.getSRID()).isZero();
    }

    @Test
    void packedSequenceType() {
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        Point p = (Point) codec.decode(codec.encode(wkt("POINT (1 2)", 4326)));
        assertThat(p.getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.class);
        assertThat(p.getX()).isEqualTo(1.0);
    }

    @Test
    void rejectsUnsupportedValueType() {
        assertThatThrownBy(() -> codec.decode(42))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("java.lang.Integer");
    }

    @Test
    void rejectsBytesThatAreNeitherInternalFormatNorHex() {
        assertThatThrownBy(() -> codec.decode(new byte[] {(byte) 0xE6, 0x10, 0, 0, 7, 1, 0, 0, 0, 0}))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("e61000000701");
    }

    @Test
    void rejectsMalformedHexAndKeepsCause() {
        assertThatThrownBy(() -> codec.decode("E6100000010100000"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("odd length");
        assertThatThrownBy(() -> codec.decode("E61000000109000000000000000000F03F0000000000000040"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("e6100000010900")
            .hasCauseInstanceOf(org.locationtech.jts.io.ParseException.class);
    }

    @Test
    void rejectsTruncatedInternalFormat() {
        assertThatThrownBy(() -> codec.decode(hex("e61000000101000000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("e6100000");
    }
}
