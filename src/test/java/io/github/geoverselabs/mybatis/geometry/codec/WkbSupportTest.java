package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.support.CoordinateSequenceType;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXYM;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBWriter;

import java.util.Arrays;
import java.util.stream.Stream;

import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.hex;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.sameGeometry;
import static io.github.geoverselabs.mybatis.geometry.codec.TestGeometries.wkt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WkbSupportTest {

    @AfterEach
    void reset() {
        GeometryFactoryProvider.reset();
    }

    static Stream<String> samples() {
        return TestGeometries.SAMPLE_WKT.stream();
    }

    // ==================== sizes and byte-level compatibility ====================

    @ParameterizedTest
    @MethodSource("samples")
    void sridPrefixedMatchesPrefixPlusJtsWkb(String text) {
        Geometry g = wkt(text);
        byte[] expectedWkb = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(g);

        byte[] actual = WkbSupport.writeSridPrefixed(g, 3857);

        assertThat(actual).hasSize(4 + expectedWkb.length);
        assertThat(Arrays.copyOfRange(actual, 0, 4)).containsExactly(0x11, 0x0F, 0, 0);
        assertThat(Arrays.copyOfRange(actual, 4, actual.length)).isEqualTo(expectedWkb);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void ewkbMatchesJtsEwkbWriter(String text) {
        Geometry copy = wkt(text);
        copy.setSRID(4326);
        byte[] expected = new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN, true).write(copy);

        assertThat(WkbSupport.writeEwkb(wkt(text), 4326, false)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void ewkbWithoutSridIsPlainWkb(String text) {
        Geometry g = wkt(text);
        assertThat(WkbSupport.writeEwkb(g, 0, false))
            .isEqualTo(new WKBWriter(2, ByteOrderValues.LITTLE_ENDIAN).write(g));
    }

    @ParameterizedTest
    @MethodSource("samples")
    void ewkbZMatchesJtsWriterWithZ(String text) {
        Geometry copy = wkt(text);
        copy.setSRID(31467);
        byte[] expected = new WKBWriter(3, ByteOrderValues.LITTLE_ENDIAN, true).write(copy);

        assertThat(WkbSupport.writeEwkb(wkt(text), 31467, true)).isEqualTo(expected);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void wkbSizeIsExact(String text) {
        Geometry g = wkt(text);
        assertThat(WkbSupport.wkbSize(g, 2)).isEqualTo(new WKBWriter(2).write(g).length);
        assertThat(WkbSupport.wkbSize(g, 3)).isEqualTo(new WKBWriter(3).write(g).length);
    }

    @Test
    void wkbSizeOfLinearRingIsLineStringSize() {
        GeometryFactory f = new GeometryFactory();
        Geometry ring = f.createLinearRing(new Coordinate[] {
            new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(1, 1), new Coordinate(0, 0)});
        byte[] encoded = WkbSupport.writeSridPrefixed(ring, 0);
        assertThat(encoded).hasSize(4 + 9 + 4 * 16);
        assertThat(WkbSupport.readSridPrefixed(encoded)).isInstanceOf(LineString.class);
    }

    // ==================== decoding ====================

    @ParameterizedTest
    @MethodSource("samples")
    void sridPrefixedRoundTrip(String text) {
        Geometry g = wkt(text, 4326);
        Geometry decoded = WkbSupport.readSridPrefixed(WkbSupport.writeSridPrefixed(g, 4326));
        assertThat(sameGeometry(g, decoded)).as("%s -> %s", g, decoded).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void ewkbRoundTrip(String text) {
        Geometry g = wkt(text, 2154);
        Geometry decoded = WkbSupport.readEwkb(WkbSupport.writeEwkb(g, 2154, false));
        assertThat(sameGeometry(g, decoded)).as("%s -> %s", g, decoded).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(2154);
    }

    @Test
    void decodedComponentsShareSridAndFactory() {
        Geometry g = wkt("MULTIPOLYGON (((0 0, 10 0, 10 10, 0 10, 0 0)), ((20 0, 30 0, 30 10, 20 0)))");
        Geometry decoded = WkbSupport.readEwkb(WkbSupport.writeEwkb(g, 3857, false));

        assertThat(decoded.getSRID()).isEqualTo(3857);
        assertThat(decoded.getFactory()).isSameAs(GeometryFactoryProvider.getFactory(3857));
        for (int i = 0; i < decoded.getNumGeometries(); i++) {
            assertThat(decoded.getGeometryN(i).getSRID()).isEqualTo(3857);
        }
        assertThat(decoded.buffer(1).getSRID()).isEqualTo(3857);
        assertThat(decoded.getCentroid().getSRID()).isEqualTo(3857);
        assertThat(decoded.union().getSRID()).isEqualTo(3857);
    }

    @Test
    void ewkbWithoutSridFlagDecodesToSridZeroRegardlessOfConfiguredDefault() {
        GeometryFactoryProvider.setDefaultSrid(3857);
        Geometry decoded = WkbSupport.readEwkb(hex("0101000000000000000000f03f0000000000000040"));
        assertThat(decoded.getSRID()).isZero();
        assertThat(decoded.getFactory().getSRID()).isZero();
    }

    @Test
    void readsBigEndianEwkb() {
        // 00 | 20000001 | 000010E6 | 1.0 | 2.0
        Geometry decoded = WkbSupport.readEwkb(hex("0020000001000010e63ff00000000000004000000000000000"));
        assertThat(decoded).isInstanceOf(Point.class);
        assertThat(decoded.getCoordinate().x).isEqualTo(1.0);
        assertThat(decoded.getCoordinate().y).isEqualTo(2.0);
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @Test
    void readsIsoWkbWithZ() {
        // ISO type 1001 (Point Z), little-endian
        Geometry decoded = WkbSupport.readEwkb(hex("01e9030000" + "000000000000f03f" + "0000000000000040"
            + "0000000000000840"));
        assertThat(decoded.getCoordinate().getZ()).isEqualTo(3.0);
        assertThat(decoded.getSRID()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        // EWKB M flag
        "0101000040000000000000f03f00000000000000400000000000000840",
        // ISO 2001 (Point M)
        "01d1070000000000000000f03f00000000000000400000000000000840",
        // ISO 3001 (Point ZM)
        "01b90b0000000000000000f03f000000000000004000000000000008400000000000001040"
    })
    void rejectsMeasures(String hexValue) {
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex(hexValue)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("M ordinates");
    }

    @Test
    void sridPrefixedWithEmbeddedEwkbSridPrefersEmbedded() {
        byte[] ewkb = WkbSupport.writeEwkb(wkt("POINT (1 2)"), 3857, false);
        byte[] prefixed = new byte[ewkb.length + 4];
        prefixed[0] = (byte) 0xE6;
        prefixed[1] = 0x10;
        System.arraycopy(ewkb, 0, prefixed, 4, ewkb.length);
        assertThat(WkbSupport.readSridPrefixed(prefixed).getSRID()).isEqualTo(3857);
    }

    @Test
    void packedSequenceTypeProducesPackedSequences() {
        GeometryFactoryProvider.setCoordinateSequenceType(CoordinateSequenceType.PACKED);
        LineString decoded = (LineString) WkbSupport.readSridPrefixed(
            WkbSupport.writeSridPrefixed(wkt("LINESTRING (1 2, 3 4)"), 4326));
        assertThat(decoded.getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.Double.class);
        assertThat(decoded.getFactory().getCoordinateSequenceFactory())
            .isSameAs(PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
        assertThat(decoded.getCoordinateN(1)).isEqualTo(new Coordinate(3, 4));
    }

    @Test
    void detectsLegacySridPrefixedValueWithSridZero() {
        Geometry g = wkt("LINESTRING (1 2, 3 4)");
        byte[] legacy = WkbSupport.writeSridPrefixed(g, 0);
        // first byte 0x00 looks like a big-endian WKB marker, but only the prefixed reading is exact
        Geometry decoded = WkbSupport.readEwkbOrSridPrefixed(legacy);
        assertThat(decoded.equalsExact(g)).isTrue();
        assertThat(decoded.getSRID()).isZero();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 256, 4326, 3857, 0x01010101})
    void detectsLegacySridPrefixedValues(int srid) {
        Geometry g = wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");
        Geometry decoded = WkbSupport.readEwkbOrSridPrefixed(WkbSupport.writeSridPrefixed(g, srid));
        assertThat(decoded.equalsExact(g)).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(srid);
    }

    @ParameterizedTest
    @MethodSource("samples")
    void detectsEwkb(String text) {
        Geometry g = wkt(text);
        Geometry decoded = WkbSupport.readEwkbOrSridPrefixed(WkbSupport.writeEwkb(g, 4326, false));
        assertThat(sameGeometry(g, decoded)).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(4326);
    }

    @Test
    void malformedInputReportsHexPrefixAndCause() {
        // truncated point: header + one ordinate
        byte[] truncated = hex("e610000001010000000000000000000000");
        assertThatThrownBy(() -> WkbSupport.readSridPrefixed(truncated))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("e6100000010100000000")
            .hasCauseInstanceOf(Exception.class);
    }

    @Test
    void rejectsUnknownGeometryType() {
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex("0109000000000000000000f03f0000000000000040")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown WKB type")
            .hasCauseInstanceOf(org.locationtech.jts.io.ParseException.class);
    }

    @Test
    void rejectsInvalidByteOrder() {
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex("0501000000000000000000f03f0000000000000040")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("byte order");
    }

    @Test
    void rejectsShortInput() {
        assertThatThrownBy(() -> WkbSupport.readSridPrefixed(hex("e6100000")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex("0101")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too short");
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex("0101000020e610")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SRID");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "MULTILINESTRING (EMPTY, EMPTY, EMPTY)",
        "MULTIPOLYGON (EMPTY, EMPTY)",
        "GEOMETRYCOLLECTION (GEOMETRYCOLLECTION EMPTY, GEOMETRYCOLLECTION EMPTY, MULTIPOINT EMPTY)",
        "MULTIPOINT (EMPTY, EMPTY, (1 2))"
    })
    void collectionsOfEmptyMembersDecode(String text) {
        // JTS 1.19 WKBReader.read(byte[]) capped counts at length/16 and rejected these
        Geometry g = wkt(text);
        assertThat(sameGeometry(g, WkbSupport.readEwkb(WkbSupport.writeEwkb(g, 4326, false)))).isTrue();
        assertThat(sameGeometry(g, WkbSupport.readSridPrefixed(WkbSupport.writeSridPrefixed(g, 4326)))).isTrue();
    }

    @Test
    void deeplyNestedCollectionsDecode() {
        StringBuilder text = new StringBuilder("POINT (1 2)");
        for (int i = 0; i < 80; i++) {
            text.insert(0, "GEOMETRYCOLLECTION (").append(')');
        }
        Geometry g = wkt(text.toString());
        byte[] encoded = WkbSupport.writeSridPrefixed(g, 3857);
        assertThat(WkbSupport.wkbEnd(encoded, 4, 0)).isEqualTo(encoded.length);
        Geometry decoded = WkbSupport.readSridPrefixed(encoded);
        assertThat(decoded.equalsExact(g)).isTrue();
        assertThat(decoded.getSRID()).isEqualTo(3857);
    }

    @Test
    void hugeCountsDoNotAllocate() {
        // LineString claiming 0x7FFFFFFF points in a 13-byte input
        assertThatThrownBy(() -> WkbSupport.readEwkb(hex("0102000000ffffff7f00000000")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ==================== structure scan ====================

    @ParameterizedTest
    @MethodSource("samples")
    void wkbEndMatchesLength(String text) {
        byte[] wkb = WkbSupport.writeEwkb(wkt(text), 4326, false);
        assertThat(WkbSupport.wkbEnd(wkb, 0, 0)).isEqualTo(wkb.length);
        byte[] prefixed = WkbSupport.writeSridPrefixed(wkt(text), 4326);
        assertThat(WkbSupport.wkbEnd(prefixed, 4, 0)).isEqualTo(prefixed.length);
    }

    @Test
    void wkbEndRejectsMalformedStructures() {
        assertThat(WkbSupport.wkbEnd(new byte[0], 0, 0)).isEqualTo(-1);
        assertThat(WkbSupport.wkbEnd(hex("0101000000"), 0, 0)).isEqualTo(-1); // missing ordinates
        assertThat(WkbSupport.wkbEnd(hex("0108000000"), 0, 0)).isEqualTo(-1); // unknown type
        assertThat(WkbSupport.wkbEnd(hex("0101000010000000000000f03f0000000000000040"), 0, 0))
            .isEqualTo(-1); // unsupported flag
        assertThat(WkbSupport.wkbEnd(hex("0104000000ffffffff"), 0, 0)).isEqualTo(-1); // negative count
        assertThat(WkbSupport.wkbEnd(hex("0103000000020000000000000000"), 0, 0)).isEqualTo(-1);
        assertThat(WkbSupport.wkbEnd(hex("0102000000ffffff7f"), 0, 0)).isEqualTo(-1);
        // EWKB flag combined with ISO dimension code is invalid
        assertThat(WkbSupport.wkbEnd(hex("01e9030080000000000000f03f00000000000000400000000000000840"), 0, 0))
            .isEqualTo(-1);
    }

    @Test
    void wkbEndHandlesZAndBigEndian() {
        byte[] z = WkbSupport.writeEwkb(wkt("LINESTRING Z (1 2 3, 4 5 6)"), 4326, true);
        assertThat(WkbSupport.wkbEnd(z, 0, 0)).isEqualTo(z.length);
        byte[] be = new WKBWriter(2, ByteOrderValues.BIG_ENDIAN).write(wkt("MULTIPOINT ((1 2), (3 4))"));
        assertThat(WkbSupport.wkbEnd(be, 0, 0)).isEqualTo(be.length);
    }

    // ==================== Z detection ====================

    @Test
    void hasZDetectsNonNaNZOnly() {
        assertThat(WkbSupport.hasZ(wkt("POINT (1 2)"))).isFalse();
        assertThat(WkbSupport.hasZ(wkt("POINT Z (1 2 3)"))).isTrue();
        assertThat(WkbSupport.hasZ(wkt("POINT EMPTY"))).isFalse();
        assertThat(WkbSupport.hasZ(wkt("POLYGON ((0 0, 1 0, 1 1, 0 0), (0.1 0.1, 0.2 0.1, 0.2 0.2, 0.1 0.1))")))
            .isFalse();
        assertThat(WkbSupport.hasZ(wkt("POLYGON Z ((0 0 0, 1 0 0, 1 1 0, 0 0 0))"))).isTrue();
        assertThat(WkbSupport.hasZ(wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING Z (0 0 1, 1 1 1))")))
            .isTrue();
        GeometryFactory f = new GeometryFactory();
        Point withNaN = f.createPoint(new Coordinate(1, 2, Double.NaN));
        assertThat(WkbSupport.hasZ(withNaN)).isFalse();
        Point xym = f.createPoint(new CoordinateXYM(1, 2, 3));
        assertThat(WkbSupport.hasZ(xym)).isFalse();
        Geometry packed2d = new GeometryFactory(PackedCoordinateSequenceFactory.DOUBLE_FACTORY)
            .createPoint(PackedCoordinateSequenceFactory.DOUBLE_FACTORY.create(new double[] {1, 2}, 2));
        assertThat(WkbSupport.hasZ(packed2d)).isFalse();
    }

    @Test
    void firstCoordinateProbeForWarnings() {
        assertThat(WkbSupport.firstCoordinateHasZ(wkt("POINT Z (1 2 3)"))).isTrue();
        assertThat(WkbSupport.firstCoordinateHasZ(wkt("LINESTRING Z (1 2 3, 4 5 6)"))).isTrue();
        assertThat(WkbSupport.firstCoordinateHasZ(wkt("POINT (1 2)"))).isFalse();
        assertThat(WkbSupport.firstCoordinateHasZ(wkt("POINT EMPTY"))).isFalse();
        assertThat(WkbSupport.firstCoordinateHasZ(wkt("GEOMETRYCOLLECTION EMPTY"))).isFalse();
        assertThat(WkbSupport.firstCoordinateHasZ(new GeometryFactory().createPoint(new CoordinateXYM(1, 2, 3))))
            .isFalse();
    }

    @Test
    void holeWithZIsDetected() {
        assertThat(WkbSupport.hasZ(wkt(
            "POLYGON Z ((0 0 NaN, 4 0 NaN, 4 4 NaN, 0 0 NaN), (1 1 5, 2 1 5, 2 2 5, 1 1 5))"))).isTrue();
    }

    @Test
    void zRoundTripThroughEwkb() {
        Geometry g = wkt("LINESTRING Z (1 2 3, 4 5 6)");
        Geometry decoded = WkbSupport.readEwkb(WkbSupport.writeEwkb(g, 4326, true));
        assertThat(decoded.getCoordinates()[1].getZ()).isEqualTo(6.0);
        Geometry flat = WkbSupport.readEwkb(WkbSupport.writeEwkb(g, 4326, false));
        assertThat(flat.getCoordinates()[1].getZ()).isNaN();
    }

    // ==================== SRID ====================

    @Test
    void effectiveSridUsesConfiguredDefaultOnlyForZero() {
        Geometry g = wkt("POINT (1 2)");
        assertThat(WkbSupport.effectiveSrid(g)).isEqualTo(4326);
        GeometryFactoryProvider.setDefaultSrid(0);
        assertThat(WkbSupport.effectiveSrid(g)).isZero();
        GeometryFactoryProvider.setDefaultSrid(3857);
        assertThat(WkbSupport.effectiveSrid(g)).isEqualTo(3857);
        assertThat(WkbSupport.effectiveSrid(wkt("POINT (1 2)", 2154))).isEqualTo(2154);
    }

    @Test
    void encodingNeverModifiesTheGeometry() {
        Geometry g = wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1))");
        Object userData = new Object();
        g.setUserData(userData);
        WkbSupport.writeEwkb(g, 4326, true);
        WkbSupport.writeSridPrefixed(g, 3857);
        assertThat(g.getSRID()).isZero();
        assertThat(g.getGeometryN(0).getSRID()).isZero();
        assertThat(g.getUserData()).isSameAs(userData);
        assertThat(g.equalsExact(wkt("GEOMETRYCOLLECTION (POINT (1 2), LINESTRING (0 0, 1 1))"))).isTrue();
    }

    // ==================== hex ====================

    @Test
    void hexDecodingIsCaseInsensitiveAndAcceptsByteaPrefix() {
        assertThat(WkbSupport.hexToBytes("0aFf")).containsExactly(0x0A, 0xFF);
        assertThat(WkbSupport.hexToBytes("\\x0aff")).containsExactly(0x0A, 0xFF);
        assertThat(WkbSupport.hexToBytes("\\X0AFF")).containsExactly(0x0A, 0xFF);
        assertThat(WkbSupport.hexToBytes("")).isEmpty();
    }

    @Test
    void hexDecodingRejectsMalformedText() {
        assertThatThrownBy(() -> WkbSupport.hexToBytes("abc"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("odd length");
        assertThatThrownBy(() -> WkbSupport.hexToBytes("0g"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("index 1");
        assertThatThrownBy(() -> WkbSupport.hexToBytes("é0"))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("index 0");
    }

    @Test
    void asciiHexDecoding() {
        assertThat(WkbSupport.tryDecodeAsciiHex("0aFF".getBytes())).containsExactly(0x0A, 0xFF);
        assertThat(WkbSupport.tryDecodeAsciiHex("\\x0aff".getBytes())).containsExactly(0x0A, 0xFF);
        assertThat(WkbSupport.tryDecodeAsciiHex("0aF".getBytes())).isNull();
        assertThat(WkbSupport.tryDecodeAsciiHex("0z".getBytes())).isNull();
        assertThat(WkbSupport.tryDecodeAsciiHex(new byte[] {(byte) 0xE6, 0x10})).isNull();
        assertThat(WkbSupport.tryDecodeAsciiHex(new byte[0])).isNull();
        assertThat(WkbSupport.tryDecodeAsciiHex("\\x".getBytes())).isNull();
    }

    @Test
    void hexEncodingAndPrefixes() {
        byte[] bytes = new byte[20];
        bytes[0] = (byte) 0xAB;
        assertThat(WkbSupport.toHex(new byte[] {(byte) 0xAB}, true)).isEqualTo("AB");
        assertThat(WkbSupport.toHex(new byte[] {(byte) 0xAB}, false)).isEqualTo("ab");
        assertThat(WkbSupport.hexPrefix(bytes)).isEqualTo("ab" + "00".repeat(15) + "...");
        assertThat(WkbSupport.hexPrefix(new byte[] {1, 2})).isEqualTo("0102");
        assertThat(WkbSupport.textPrefix("x".repeat(40))).isEqualTo("x".repeat(32) + "...");
        assertThat(WkbSupport.textPrefix("abc")).isEqualTo("abc");
    }

    @Test
    void writeEwkbHeaderLayout() {
        assertThat(hex(WkbSupport.writeEwkb(wkt("POINT (1 2)"), 4326, false)))
            .isEqualTo("0101000020e6100000000000000000f03f0000000000000040");
        assertThat(hex(WkbSupport.writeEwkb(wkt("POINT Z (1 2 3)"), 4326, true)))
            .isEqualTo("01010000a0e6100000000000000000f03f00000000000000400000000000000840");
    }
}
