package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeoJsonGeometryWriterTest {

    private static final JsonFactory JSON = new JsonFactory();
    private static final GeometryFactory FACTORY = new GeometryFactory(new PrecisionModel(), 4326);
    private static final WKTReader WKT = new WKTReader(FACTORY);

    static Geometry wkt(String wkt) {
        try {
            return WKT.read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static String write(Geometry geometry, Integer precision) throws IOException {
        StringWriter out = new StringWriter();
        try (JsonGenerator gen = JSON.createGenerator(out)) {
            GeoJsonGeometryWriter.writeObject(geometry, gen, precision);
        }
        return out.toString();
    }

    static String write(Geometry geometry) throws IOException {
        return write(geometry, null);
    }

    @Nested
    class Types {

        @Test
        void point() throws IOException {
            assertThat(write(wkt("POINT (1 2)"))).isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}");
        }

        @Test
        void lineString() throws IOException {
            assertThat(write(wkt("LINESTRING (1 2, 3 4)")))
                .isEqualTo("{\"type\":\"LineString\",\"coordinates\":[[1.0,2.0],[3.0,4.0]]}");
        }

        @Test
        void linearRingIsWrittenAsLineString() throws IOException {
            LinearRing ring = FACTORY.createLinearRing(new Coordinate[] {
                new CoordinateXY(0, 0), new CoordinateXY(1, 0), new CoordinateXY(1, 1), new CoordinateXY(0, 0)});
            assertThat(write(ring)).startsWith("{\"type\":\"LineString\",\"coordinates\":[[0.0,0.0],[1.0,0.0]");
        }

        @Test
        void polygonWithHole() throws IOException {
            assertThat(write(wkt("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))")))
                .isEqualTo("{\"type\":\"Polygon\",\"coordinates\":["
                    + "[[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,10.0],[0.0,0.0]],"
                    + "[[2.0,2.0],[2.0,4.0],[4.0,4.0],[4.0,2.0],[2.0,2.0]]]}");
        }

        @Test
        void multiPoint() throws IOException {
            assertThat(write(wkt("MULTIPOINT ((1 2), (3 4))")))
                .isEqualTo("{\"type\":\"MultiPoint\",\"coordinates\":[[1.0,2.0],[3.0,4.0]]}");
        }

        @Test
        void multiLineString() throws IOException {
            assertThat(write(wkt("MULTILINESTRING ((1 2, 3 4), (5 6, 7 8))")))
                .isEqualTo("{\"type\":\"MultiLineString\",\"coordinates\":"
                    + "[[[1.0,2.0],[3.0,4.0]],[[5.0,6.0],[7.0,8.0]]]}");
        }

        @Test
        void multiPolygon() throws IOException {
            assertThat(write(wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)), ((5 5, 6 5, 6 6, 5 5)))")))
                .isEqualTo("{\"type\":\"MultiPolygon\",\"coordinates\":["
                    + "[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]],"
                    + "[[[5.0,5.0],[6.0,5.0],[6.0,6.0],[5.0,5.0]]]]}");
        }

        @Test
        void geometryCollectionIncludingNestedCollectionAndMultiGeometry() throws IOException {
            assertThat(write(wkt("GEOMETRYCOLLECTION (POINT (1 2), GEOMETRYCOLLECTION (LINESTRING (0 0, 1 1)), "
                    + "MULTIPOINT ((3 4)))")))
                .isEqualTo("{\"type\":\"GeometryCollection\",\"geometries\":["
                    + "{\"type\":\"Point\",\"coordinates\":[1.0,2.0]},"
                    + "{\"type\":\"GeometryCollection\",\"geometries\":["
                    + "{\"type\":\"LineString\",\"coordinates\":[[0.0,0.0],[1.0,1.0]]}]},"
                    + "{\"type\":\"MultiPoint\",\"coordinates\":[[3.0,4.0]]}]}");
        }

        @Test
        void packedSequencesAreWritten() throws IOException, ParseException {
            GeometryFactory packed = new GeometryFactory(new PrecisionModel(), 4326,
                PackedCoordinateSequenceFactory.DOUBLE_FACTORY);
            Geometry line = new WKTReader(packed).read("LINESTRING Z (1 2 3, 4 5 6)");
            assertThat(write(line))
                .isEqualTo("{\"type\":\"LineString\",\"coordinates\":[[1.0,2.0,3.0],[4.0,5.0,6.0]]}");
        }

        @Test
        void writeMembersIntoAnExistingObject() throws IOException {
            StringWriter out = new StringWriter();
            try (JsonGenerator gen = JSON.createGenerator(out)) {
                gen.writeStartObject();
                gen.writeStringField("@class", "x");
                GeoJsonGeometryWriter.writeMembers(wkt("POINT (1 2)"), gen, 1);
                gen.writeEndObject();
            }
            assertThat(out).hasToString("{\"@class\":\"x\",\"type\":\"Point\",\"coordinates\":[1,2]}");
        }
    }

    @Nested
    class Empties {

        @Test
        void emptyGeometriesAreWrittenWithEmptyArrays() throws IOException {
            assertThat(write(wkt("POINT EMPTY"))).isEqualTo("{\"type\":\"Point\",\"coordinates\":[]}");
            assertThat(write(wkt("LINESTRING EMPTY"))).isEqualTo("{\"type\":\"LineString\",\"coordinates\":[]}");
            assertThat(write(wkt("POLYGON EMPTY"))).isEqualTo("{\"type\":\"Polygon\",\"coordinates\":[]}");
            assertThat(write(wkt("MULTIPOINT EMPTY"))).isEqualTo("{\"type\":\"MultiPoint\",\"coordinates\":[]}");
            assertThat(write(wkt("MULTILINESTRING EMPTY")))
                .isEqualTo("{\"type\":\"MultiLineString\",\"coordinates\":[]}");
            assertThat(write(wkt("MULTIPOLYGON EMPTY"))).isEqualTo("{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
            assertThat(write(wkt("GEOMETRYCOLLECTION EMPTY")))
                .isEqualTo("{\"type\":\"GeometryCollection\",\"geometries\":[]}");
        }

        @Test
        void emptyResultsOfJtsOperations() throws IOException {
            Geometry disjointPoints = wkt("POINT (1 1)").intersection(wkt("POINT (2 2)"));
            assertThat(write(disjointPoints)).isEqualTo("{\"type\":\"Point\",\"coordinates\":[]}");
            Geometry disjointPolygons = wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))")
                .intersection(wkt("POLYGON ((5 5, 6 5, 6 6, 5 5))"));
            assertThat(write(disjointPolygons)).isEqualTo("{\"type\":\"Polygon\",\"coordinates\":[]}");
        }

        @Test
        void emptyMembersOfMultiGeometriesAreSkipped() throws IOException {
            Point empty = FACTORY.createPoint();
            Point point = FACTORY.createPoint(new CoordinateXY(1, 2));
            assertThat(write(FACTORY.createMultiPoint(new Point[] {empty, point})))
                .isEqualTo("{\"type\":\"MultiPoint\",\"coordinates\":[[1.0,2.0]]}");
            assertThat(write(wkt("MULTIPOLYGON (EMPTY, ((0 0, 1 0, 1 1, 0 0)))")))
                .isEqualTo("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]]}");
            assertThat(write(wkt("MULTIPOLYGON (EMPTY)")))
                .isEqualTo("{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
            assertThat(write(wkt("MULTILINESTRING (EMPTY, (1 2, 3 4))")))
                .isEqualTo("{\"type\":\"MultiLineString\",\"coordinates\":[[[1.0,2.0],[3.0,4.0]]]}");
        }

        @Test
        void emptyMembersOfCollectionsAreKeptAsTypedEmpties() throws IOException {
            assertThat(write(wkt("GEOMETRYCOLLECTION (POINT EMPTY, POINT (1 2))")))
                .isEqualTo("{\"type\":\"GeometryCollection\",\"geometries\":["
                    + "{\"type\":\"Point\",\"coordinates\":[]},{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}]}");
        }
    }

    @Nested
    class Orientation {

        @Test
        void clockwiseExteriorIsWrittenCounterClockwise() throws IOException {
            assertThat(write(wkt("POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))")))
                .isEqualTo("{\"type\":\"Polygon\",\"coordinates\":["
                    + "[[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,10.0],[0.0,0.0]]]}");
        }

        @Test
        void counterClockwiseHoleIsWrittenClockwise() throws IOException {
            assertThat(write(wkt("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 4 2, 4 4, 2 4, 2 2))")))
                .isEqualTo("{\"type\":\"Polygon\",\"coordinates\":["
                    + "[[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,10.0],[0.0,0.0]],"
                    + "[[2.0,2.0],[2.0,4.0],[4.0,4.0],[4.0,2.0],[2.0,2.0]]]}");
        }

        @Test
        void multiPolygonRingsAreOriented() throws IOException {
            assertThat(write(wkt("MULTIPOLYGON (((0 0, 0 1, 1 1, 0 0)))")))
                .isEqualTo("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,0.0],[1.0,1.0],[0.0,1.0],[0.0,0.0]]]]}");
        }

        @Test
        void storedGeometryIsNotModified() throws IOException {
            Polygon polygon = (Polygon) wkt("POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))");
            Geometry copy = polygon.copy();
            write(polygon);
            assertThat(polygon.equalsExact(copy)).isTrue();
        }
    }

    @Nested
    class Ordinates {

        @Test
        void zIsWrittenWhenPresentAndFinite() throws IOException {
            assertThat(write(wkt("POINT Z (1 2 3)"))).isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.0,2.0,3.0]}");
            Point noZ = FACTORY.createPoint(new Coordinate(1, 2)); // dimension 3, z NaN
            assertThat(write(noZ)).isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}");
            Geometry partialZ = FACTORY.createLineString(new Coordinate[] {
                new Coordinate(1, 2, 3), new Coordinate(4, 5, Double.NaN)});
            assertThat(write(partialZ))
                .isEqualTo("{\"type\":\"LineString\",\"coordinates\":[[1.0,2.0,3.0],[4.0,5.0]]}");
        }

        @Test
        void precisionAppliesToZ() throws IOException {
            assertThat(write(wkt("POINT Z (1.23456 2.34567 3.45678)"), 2))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.23,2.35,3.46]}");
        }

        @Test
        void nonFiniteOrdinatesAreRejected() {
            Point nanX = FACTORY.createPoint(new CoordinateXY(Double.NaN, 1));
            assertThatThrownBy(() -> write(nanX))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("non-finite x ordinate NaN")
                .hasMessageContaining("Point");
            Geometry infiniteY = FACTORY.createLineString(new Coordinate[] {
                new CoordinateXY(0, 0), new CoordinateXY(1, Double.POSITIVE_INFINITY)});
            assertThatThrownBy(() -> write(infiniteY, 3))
                .isInstanceOf(JsonGenerationException.class)
                .hasMessageContaining("non-finite y ordinate Infinity of a LineString");
        }

        @Test
        void fullPrecisionMatchesJacksonDoubleOutput() throws IOException {
            assertThat(write(FACTORY.createPoint(new CoordinateXY(0.1 + 0.2, -0.0))))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[0.30000000000000004,-0.0]}");
        }

        @Test
        void precisionTrimsZerosAndNormalisesNegativeZero() throws IOException {
            assertThat(write(FACTORY.createPoint(new CoordinateXY(-0.0, 10.5)), 6))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[0,10.5]}");
            assertThat(write(FACTORY.createPoint(new CoordinateXY(-0.0000001, 120)), 6))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[0,120]}");
        }

        @Test
        void hugeValuesUseTheBigDecimalPath() throws IOException {
            assertThat(write(FACTORY.createPoint(new CoordinateXY(1e20, -123456789012.25)), 6))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[100000000000000000000,-123456789012.25]}");
        }
    }

    @Nested
    class Formatter {

        private String format(double value, int precision) {
            char[] buffer = new char[40];
            int length = GeoJsonGeometryWriter.formatFixed(value, precision, buffer);
            return length < 0 ? GeoJsonGeometryWriter.formatBig(value, precision) : new String(buffer, 0, length);
        }

        @ParameterizedTest
        @CsvSource({
            "1.23456789012345, 0, 1",
            "1.23456789012345, 1, 1.2",
            "1.23456789012345, 2, 1.23",
            "1.23456789012345, 3, 1.235",
            "1.23456789012345, 4, 1.2346",
            "1.23456789012345, 5, 1.23457",
            "1.23456789012345, 6, 1.234568",
            "1.23456789012345, 7, 1.2345679",
            "1.23456789012345, 8, 1.23456789",
            "1.23456789012345, 9, 1.23456789",
            "1.23456789012345, 10, 1.2345678901",
            "1.23456789012345, 11, 1.23456789012",
            "1.23456789012345, 12, 1.234567890123",
            "0.1234567890123456, 13, 0.1234567890123",
            "1.23456789012345, 14, 1.23456789012345",
            "1.23456789012345, 15, 1.23456789012345",
            "-179.987654321, 3, -179.988",
            "-0.5, 0, -1",
            "0.5, 0, 1",
            "2.5, 0, 3",
            "1.25, 1, 1.3",
            "-1.25, 1, -1.3",
            "0.4, 0, 0",
            "-0.4, 0, 0",
            "-0.0, 6, 0",
            "0.0, 0, 0",
            "-0.00000049, 6, 0",
            "-0.0000006, 6, -0.000001",
            "0.000001, 6, 0.000001",
            "0.0000012346, 9, 0.000001235",
            "10.0, 6, 10",
            "100.10, 3, 100.1",
            "9.9999999, 6, 10",
            "-9.9999996, 6, -10",
            "123456789.123456789, 6, 123456789.123457",
            "180, 15, 180",
            "1e20, 6, 100000000000000000000",
            "-1e20, 0, -100000000000000000000",
            "123456.123456789, 15, 123456.123456789"
        })
        void formats(double value, int precision, String expected) {
            assertThat(format(value, precision)).isEqualTo(expected);
        }

        @Test
        void largestDoubleUsesPlainNotation() {
            assertThat(format(Double.MAX_VALUE, 2))
                .isEqualTo(new BigDecimal("1.7976931348623157E308").toPlainString())
                .doesNotContain("E")
                .hasSize(309);
            assertThat(format(-Double.MAX_VALUE, 0)).startsWith("-17976931348623157000");
        }

        @Test
        void fallbackThreshold() {
            char[] buffer = new char[40];
            // |v| * 10^p below 2^53 stays on the fast path, at or above it uses BigDecimal
            int length = GeoJsonGeometryWriter.formatFixed(9007199254740991.0, 0, buffer);
            assertThat(new String(buffer, 0, length)).isEqualTo("9007199254740991");
            assertThat(GeoJsonGeometryWriter.formatFixed(-9007199254740991.0, 0, buffer)).isEqualTo(17);
            assertThat(GeoJsonGeometryWriter.formatFixed(9007199254740992.0, 0, buffer)).isEqualTo(-1);
            assertThat(GeoJsonGeometryWriter.formatFixed(9007199.254740992, 15, buffer)).isEqualTo(-1);
            assertThat(GeoJsonGeometryWriter.formatBig(9007199254740992.0, 0)).isEqualTo("9007199254740992");
            assertThat(GeoJsonGeometryWriter.formatBig(-0.0, 3)).isEqualTo("0");
            assertThat(GeoJsonGeometryWriter.formatBig(1e17, 6)).isEqualTo("100000000000000000");
        }

        @Test
        void worstCaseFastPathOutputFitsTheBuffer() {
            char[] buffer = new char[40];
            assertThat(GeoJsonGeometryWriter.formatFixed(-8.123456789012345, 15, buffer)).isLessThanOrEqualTo(18);
            assertThat(GeoJsonGeometryWriter.formatFixed(-9007199254740991.0, 0, buffer)).isEqualTo(17);
        }

        @Test
        void largeIntegersNearTheThresholdAreNotRoundedUp() {
            // 2^52 + 1 is exactly representable; adding 0.5 would round to 2^52 + 2 with doubles
            double value = 4503599627370497.0;
            assertThat(format(value, 0)).isEqualTo("4503599627370497");
            assertThat(format(-value, 0)).isEqualTo("-4503599627370497");
        }

        @Test
        void agreesWithBigDecimalOnTheScaledValue() {
            double[] samples = {0.1, 0.7, 1.005, 2.675, 116.397128, -33.8688197, 151.2092955, 89.999999999};
            for (double sample : samples) {
                for (int p = 0; p <= GeoJsonOptions.MAX_PRECISION; p++) {
                    double scaled = Math.abs(sample) * Math.pow(10, p);
                    if (scaled >= 9007199254740992.0) {
                        continue;
                    }
                    BigDecimal expected = new BigDecimal(scaled).setScale(0, RoundingMode.HALF_UP)
                        .movePointLeft(p);
                    if (sample < 0) {
                        expected = expected.negate();
                    }
                    assertThat(new BigDecimal(format(sample, p)))
                        .as("%s at %d decimals", sample, p)
                        .isEqualByComparingTo(expected);
                }
            }
        }
    }
}
