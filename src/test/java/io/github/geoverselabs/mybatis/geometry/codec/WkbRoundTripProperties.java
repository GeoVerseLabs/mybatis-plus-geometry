package io.github.geoverselabs.mybatis.geometry.codec;

import io.github.geoverselabs.mybatis.geometry.util.WkbUtil;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ByteOrderValues;
import org.locationtech.jts.io.WKBWriter;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property tests: every geometry survives every encoding bit-exactly with its SRID, and the PostGIS
 * decoder tells EWKB and the legacy SRID-prefixed format apart.
 */
class WkbRoundTripProperties {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    private final MySQLWkbCodec mysql = new MySQLWkbCodec();
    private final PostGISWkbCodec postgis = new PostGISWkbCodec();

    @Property(tries = 300)
    void mysqlRoundTripIsExact(@ForAll("geometries") Geometry geometry, @ForAll("srids") int srid) {
        Geometry decoded = mysql.decode(mysql.encode(geometry, srid));
        assertSame(geometry, decoded, srid);
    }

    @Property(tries = 300)
    void postgisRoundTripIsExact(@ForAll("geometries") Geometry geometry, @ForAll("srids") int srid) {
        Geometry decoded = postgis.decode(postgis.encode(geometry, srid));
        assertSame(geometry, decoded, srid);
    }

    @Property(tries = 300)
    void postgisReadsLegacySridPrefixedValues(@ForAll("geometries") Geometry geometry, @ForAll("srids") int srid) {
        Geometry decoded = postgis.decode(WkbSupport.writeSridPrefixed(geometry, srid));
        assertSame(geometry, decoded, srid);
    }

    @Property(tries = 200)
    void postgisReadsBigEndianEwkb(@ForAll("geometries") Geometry geometry, @ForAll("srids") int srid) {
        Geometry copy = geometry.copy();
        copy.setSRID(srid);
        byte[] be = new WKBWriter(2, ByteOrderValues.BIG_ENDIAN, srid != 0).write(copy);
        assertSame(geometry, postgis.decode(be), srid);
    }

    @Property(tries = 200)
    void wkbUtilRoundTripIsExact(@ForAll("geometries") Geometry geometry, @ForAll("srids") int srid) {
        Geometry tagged = geometry.copy();
        tagged.setSRID(srid);
        // SRID 0 is replaced by the configured default (4326) on write
        int expected = srid == 0 ? 4326 : srid;
        assertSame(geometry, WkbUtil.fromWkb(WkbUtil.toWkb(tagged)), expected);
    }

    @Property(tries = 200)
    void encodedSizeIsExact(@ForAll("geometries") Geometry geometry) {
        assertThat(WkbSupport.wkbSize(geometry, 2)).isEqualTo(new WKBWriter(2).write(geometry).length);
    }

    private static void assertSame(Geometry expected, Geometry actual, int srid) {
        assertThat(TestGeometries.sameGeometry(expected, actual)).as("%s vs %s", expected, actual).isTrue();
        assertThat(actual.getSRID()).isEqualTo(srid);
        for (int i = 0; i < actual.getNumGeometries(); i++) {
            assertThat(actual.getGeometryN(i).getSRID()).isEqualTo(srid);
        }
    }

    @Provide
    Arbitrary<Integer> srids() {
        return Arbitraries.oneOf(
            Arbitraries.of(0, 1, 256, 4326, 3857, 8192, 16384, 32768, 0x2000_0000, -1, Integer.MAX_VALUE),
            Arbitraries.integers());
    }

    @Provide
    Arbitrary<Geometry> geometries() {
        return Arbitraries.lazyOf(
            this::points,
            this::lineStrings,
            this::polygons,
            () -> points().list().ofMaxSize(4).map(l -> FACTORY.createMultiPoint(l.toArray(new Point[0]))),
            () -> lineStrings().list().ofMaxSize(4)
                .map(l -> FACTORY.createMultiLineString(l.toArray(new LineString[0]))),
            () -> polygons().list().ofMaxSize(3).map(l -> FACTORY.createMultiPolygon(l.toArray(new Polygon[0]))),
            () -> geometries().list().ofMaxSize(3)
                .map(l -> FACTORY.createGeometryCollection(l.toArray(new Geometry[0]))));
    }

    private Arbitrary<Geometry> points() {
        return Arbitraries.oneOf(
            coordinates().map(c -> (Geometry) FACTORY.createPoint(c)),
            Arbitraries.just((Geometry) FACTORY.createPoint()));
    }

    private Arbitrary<Geometry> lineStrings() {
        return Arbitraries.oneOf(
            coordinates().list().ofMinSize(2).ofMaxSize(12)
                .map(l -> (Geometry) FACTORY.createLineString(l.toArray(new Coordinate[0]))),
            Arbitraries.just((Geometry) FACTORY.createLineString()));
    }

    private Arbitrary<Geometry> polygons() {
        Arbitrary<LinearRing> rings = coordinates().list().ofMinSize(3).ofMaxSize(8).map(l -> {
            List<Coordinate> closed = new ArrayList<>(l);
            closed.add(new Coordinate(l.get(0)));
            return FACTORY.createLinearRing(closed.toArray(new Coordinate[0]));
        });
        return Arbitraries.oneOf(
            Combinators.combine(rings, rings.list().ofMaxSize(2))
                .as((shell, holes) -> (Geometry) FACTORY.createPolygon(shell, holes.toArray(new LinearRing[0]))),
            Arbitraries.just((Geometry) FACTORY.createPolygon()));
    }

    private Arbitrary<Coordinate> coordinates() {
        Arbitrary<Double> ordinates = Arbitraries.oneOf(
            Arbitraries.longs().map(Double::longBitsToDouble).filter(Double::isFinite),
            Arbitraries.doubles().between(-180, 180).ofScale(8),
            Arbitraries.of(0.0, -0.0, Double.MIN_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE));
        return Combinators.combine(ordinates, ordinates).as(Coordinate::new);
    }
}
