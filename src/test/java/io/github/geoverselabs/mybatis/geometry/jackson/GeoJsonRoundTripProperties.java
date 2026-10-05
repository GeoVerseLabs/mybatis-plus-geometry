package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests: full precision round trips are exact and the precision formatter is
 * correct for arbitrary values.
 */
class GeoJsonRoundTripProperties {

    private static final GeometryFactory FACTORY = GeometryFactoryProvider.getFactory();

    private static final ObjectMapper FAST = new ObjectMapper()
        .registerModule(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS));

    private static final ObjectMapper SLOW = new ObjectMapper()
        .registerModule(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withFastDoubleParsing(false)));

    @Provide
    Arbitrary<Double> longitudes() {
        return Arbitraries.frequencyOf(
            net.jqwik.api.Tuple.of(8, Arbitraries.doubles().between(-180, 180).ofScale(17)),
            net.jqwik.api.Tuple.of(1, Arbitraries.of(-180.0, 180.0, 0.0, -0.0, Double.MIN_VALUE, -Double.MIN_VALUE,
                Math.nextDown(180.0), Math.nextUp(-180.0), 0.1, 1e-300)));
    }

    @Provide
    Arbitrary<Double> latitudes() {
        return Arbitraries.frequencyOf(
            net.jqwik.api.Tuple.of(8, Arbitraries.doubles().between(-90, 90).ofScale(17)),
            net.jqwik.api.Tuple.of(1, Arbitraries.of(-90.0, 90.0, 0.0, -0.0, Double.MIN_NORMAL, Math.nextDown(90.0))));
    }

    @Provide
    Arbitrary<Coordinate> positions() {
        Arbitrary<Double> altitudes = Arbitraries.doubles().between(-1e7, 1e7).ofScale(12);
        Arbitrary<Coordinate> xy = Combinators.combine(longitudes(), latitudes()).as(CoordinateXY::new);
        Arbitrary<Coordinate> xyz = Combinators.combine(longitudes(), latitudes(), altitudes).as(Coordinate::new);
        return Arbitraries.frequencyOf(net.jqwik.api.Tuple.of(3, xy), net.jqwik.api.Tuple.of(1, xyz));
    }

    @Provide
    Arbitrary<Polygon> rectangles() {
        return Combinators.combine(longitudes(), longitudes(), latitudes(), latitudes())
            .filter((x1, x2, y1, y2) -> x1.doubleValue() != x2.doubleValue() && y1.doubleValue() != y2.doubleValue())
            .as((x1, x2, y1, y2) -> {
                double minX = Math.min(x1, x2);
                double maxX = Math.max(x1, x2);
                double minY = Math.min(y1, y2);
                double maxY = Math.max(y1, y2);
                // counter-clockwise, so the normalised orientation equals the input
                return FACTORY.createPolygon(new Coordinate[] {
                    new CoordinateXY(minX, minY), new CoordinateXY(maxX, minY), new CoordinateXY(maxX, maxY),
                    new CoordinateXY(minX, maxY), new CoordinateXY(minX, minY)});
            });
    }

    @Provide
    Arbitrary<Double> formattable() {
        return Arbitraries.oneOf(
            Arbitraries.doubles().between(-1000, 1000).ofScale(17),
            Arbitraries.doubles().between(-1e9, 1e9).ofScale(8),
            Arbitraries.doubles().between(-1e22, 1e22).ofScale(2),
            Arbitraries.of(0.0, -0.0, 0.5, -0.5, 1.5, 2.5, -2.5, 1e-15, -1e-15, 4503599627370497.0, 9007199254740991.0,
                Double.MAX_VALUE, -Double.MAX_VALUE, Double.MIN_VALUE));
    }

    @Property(tries = 300)
    void pointsRoundTripExactly(@ForAll("positions") Coordinate position) throws IOException {
        Point point = FACTORY.createPoint(position);
        assertExactRoundTrip(point, Point.class);
    }

    @Property(tries = 200)
    void lineStringsRoundTripExactly(@ForAll("positions") Coordinate first, @ForAll("positions") Coordinate second,
                                     @ForAll("positions") Coordinate third) throws IOException {
        LineString line = FACTORY.createLineString(new Coordinate[] {first, second, third});
        assertExactRoundTrip(line, LineString.class);
    }

    @Property(tries = 200)
    void multiPointsRoundTripExactly(@ForAll("positions") Coordinate first, @ForAll("positions") Coordinate second)
            throws IOException {
        MultiPoint multiPoint = FACTORY.createMultiPointFromCoords(new Coordinate[] {first, second});
        assertExactRoundTrip(multiPoint, MultiPoint.class);
    }

    @Property(tries = 200)
    void polygonsRoundTripExactly(@ForAll("rectangles") Polygon polygon) throws IOException {
        assertExactRoundTrip(polygon, Polygon.class);
        assertExactRoundTrip(FACTORY.createGeometryCollection(new Geometry[] {polygon, polygon.getCentroid()}),
            Geometry.class);
    }

    @Property(tries = 3000)
    void formatterRoundsToTheNearestUnit(@ForAll("formattable") double value,
                                         @ForAll @IntRange(min = 0, max = 15) int precision) {
        char[] buffer = new char[40];
        int length = GeoJsonGeometryWriter.formatFixed(value, precision, buffer);
        String text = length < 0 ? GeoJsonGeometryWriter.formatBig(value, precision) : new String(buffer, 0, length);

        assertThat(text).doesNotContain("E").isNotEqualTo("-0").doesNotStartWith("+");
        int dot = text.indexOf('.');
        if (dot >= 0) {
            assertThat(text).doesNotEndWith("0").doesNotEndWith(".");
            assertThat(text.length() - dot - 1).isLessThanOrEqualTo(precision);
        }
        BigDecimal printed = new BigDecimal(text);
        if (printed.signum() != 0) {
            assertThat(printed.signum()).isEqualTo((int) Math.signum(value));
        }
        // within half a unit in the last place, plus the error of scaling a double by 10^p
        BigDecimal halfUnit = BigDecimal.ONE.movePointLeft(precision).divide(BigDecimal.valueOf(2));
        BigDecimal tolerance = halfUnit.add(new BigDecimal(Math.ulp(value)));
        assertThat(printed.subtract(new BigDecimal(value)).abs()).isLessThanOrEqualTo(tolerance);
    }

    private static <T extends Geometry> void assertExactRoundTrip(Geometry geometry, Class<T> type) throws IOException {
        String json = FAST.writeValueAsString(geometry);
        T fast = FAST.readValue(json, type);
        T slow = SLOW.readValue(json, type);
        assertSameOrdinates(fast, geometry);
        assertSameOrdinates(slow, geometry);
        assertThat(FAST.writeValueAsString(fast)).isEqualTo(json);
    }

    private static void assertSameOrdinates(Geometry actual, Geometry expected) {
        assertThat(actual.getGeometryType()).isEqualTo(expected.getGeometryType());
        List<double[]> actualOrdinates = ordinates(actual);
        List<double[]> expectedOrdinates = ordinates(expected);
        assertThat(actualOrdinates).hasSameSizeAs(expectedOrdinates);
        for (int i = 0; i < actualOrdinates.size(); i++) {
            double[] a = actualOrdinates.get(i);
            double[] e = expectedOrdinates.get(i);
            // bitwise comparison: distinguishes -0.0 from 0.0
            assertThat(Double.doubleToLongBits(a[0])).as("x of %d", i).isEqualTo(Double.doubleToLongBits(e[0]));
            assertThat(Double.doubleToLongBits(a[1])).as("y of %d", i).isEqualTo(Double.doubleToLongBits(e[1]));
            if (Double.isNaN(e[2])) {
                assertThat(a[2]).isNaN();
            } else {
                assertThat(Double.doubleToLongBits(a[2])).as("z of %d", i).isEqualTo(Double.doubleToLongBits(e[2]));
            }
        }
    }

    private static List<double[]> ordinates(Geometry geometry) {
        List<double[]> result = new ArrayList<>();
        geometry.apply(new org.locationtech.jts.geom.CoordinateSequenceFilter() {
            @Override
            public void filter(CoordinateSequence seq, int i) {
                result.add(new double[] {seq.getX(i), seq.getY(i), seq.getZ(i)});
            }

            @Override
            public boolean isDone() {
                return false;
            }

            @Override
            public boolean isGeometryChanged() {
                return false;
            }
        });
        return result;
    }
}
