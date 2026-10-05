package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.JsonFactoryBuilder;
import com.fasterxml.jackson.core.util.JsonParserDelegate;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.CoordinateSequence;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.geom.impl.CoordinateArraySequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequence;
import org.locationtech.jts.geom.impl.PackedCoordinateSequenceFactory;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GeoJsonGeometryReaderTest {

    private static final JsonFactory JSON = new JsonFactory();

    private static final GeoJsonOptions FULL = GeoJsonOptions.DEFAULTS;
    private static final GeoJsonOptions BASIC = GeoJsonOptions.DEFAULTS.withValidation(GeometryValidation.BASIC);
    private static final GeoJsonOptions NO_RANGE = GeoJsonOptions.DEFAULTS.withCoordinateRangeValidation(false);

    private static final String SQUARE_CCW = "[[0,0],[10,0],[10,10],[0,10],[0,0]]";
    private static final String SQUARE_CW = "[[0,0],[0,10],[10,10],[10,0],[0,0]]";
    private static final String HOLE_CW = "[[2,2],[2,4],[4,4],[4,2],[2,2]]";
    private static final String HOLE_CCW = "[[2,2],[4,2],[4,4],[2,4],[2,2]]";
    private static final String BOWTIE = "[[0,0],[10,10],[10,0],[0,10],[0,0]]";

    static <T extends Geometry> T read(String json, Class<T> target) throws IOException {
        return read(json, target, FULL);
    }

    static <T extends Geometry> T read(String json, Class<T> target, GeoJsonOptions options) throws IOException {
        try (JsonParser parser = JSON.createParser(json)) {
            parser.nextToken();
            T result = GeoJsonGeometryReader.read(parser, target, options);
            if (result != null) {
                assertThat(parser.currentToken()).as("parser left at the object's end").isEqualTo(JsonToken.END_OBJECT);
                assertThat(parser.nextToken()).as("nothing consumed beyond the geometry").isNull();
            }
            return result;
        }
    }

    static String geo(String type, String coordinates) {
        return "{\"type\":\"" + type + "\",\"coordinates\":" + coordinates + "}";
    }

    static double[][] coords(Geometry geometry) {
        return java.util.Arrays.stream(geometry.getCoordinates())
            .map(c -> new double[] {c.getX(), c.getY()})
            .toArray(double[][]::new);
    }

    @Nested
    class ValidInput {

        @Test
        void readsPoint() throws IOException {
            Point point = read(geo("Point", "[100.5, -45.25]"), Point.class);
            assertThat(point.getX()).isEqualTo(100.5);
            assertThat(point.getY()).isEqualTo(-45.25);
            assertThat(point.getSRID()).isEqualTo(GeometryFactoryProvider.getFactory().getSRID());
            assertThat(point.getCoordinateSequence().getDimension()).isEqualTo(2);
        }

        @Test
        void readsIntegerAndExponentNumbers() throws IOException {
            Point point = read(geo("Point", "[1, 2.5e1]"), Point.class);
            assertThat(point.getX()).isEqualTo(1.0);
            assertThat(point.getY()).isEqualTo(25.0);
        }

        @Test
        void readsLineString() throws IOException {
            LineString line = read(geo("LineString", "[[1,2],[3,4],[5,6]]"), LineString.class);
            assertThat(coords(line)).isDeepEqualTo(new double[][] {{1, 2}, {3, 4}, {5, 6}});
        }

        @Test
        void readsPolygonWithHole() throws IOException {
            Polygon polygon = read(geo("Polygon", "[" + SQUARE_CCW + "," + HOLE_CW + "]"), Polygon.class);
            assertThat(polygon.getNumInteriorRing()).isEqualTo(1);
            assertThat(coords(polygon.getExteriorRing()))
                .isDeepEqualTo(new double[][] {{0, 0}, {10, 0}, {10, 10}, {0, 10}, {0, 0}});
            assertThat(coords(polygon.getInteriorRingN(0)))
                .isDeepEqualTo(new double[][] {{2, 2}, {2, 4}, {4, 4}, {4, 2}, {2, 2}});
        }

        @Test
        void readsMultiPoint() throws IOException {
            MultiPoint multiPoint = read(geo("MultiPoint", "[[1,2],[3,4]]"), MultiPoint.class);
            assertThat(multiPoint.getNumGeometries()).isEqualTo(2);
            assertThat(((Point) multiPoint.getGeometryN(1)).getX()).isEqualTo(3);
        }

        @Test
        void readsMultiLineString() throws IOException {
            MultiLineString lines = read(geo("MultiLineString", "[[[1,2],[3,4]],[[5,6],[7,8],[9,10]]]"),
                MultiLineString.class);
            assertThat(lines.getNumGeometries()).isEqualTo(2);
            assertThat(lines.getGeometryN(1).getNumPoints()).isEqualTo(3);
        }

        @Test
        void readsMultiPolygon() throws IOException {
            MultiPolygon multiPolygon = read(geo("MultiPolygon",
                "[[" + SQUARE_CCW + "," + HOLE_CW + "],[[[20,20],[30,20],[30,30],[20,20]]]]"), MultiPolygon.class);
            assertThat(multiPolygon.getNumGeometries()).isEqualTo(2);
            assertThat(((Polygon) multiPolygon.getGeometryN(0)).getNumInteriorRing()).isEqualTo(1);
        }

        @Test
        void readsGeometryCollection() throws IOException {
            GeometryCollection collection = read("{\"type\":\"GeometryCollection\",\"geometries\":["
                + geo("Point", "[1,2]") + "," + geo("LineString", "[[1,2],[3,4]]") + "]}", GeometryCollection.class);
            assertThat(collection.getNumGeometries()).isEqualTo(2);
            assertThat(collection.getGeometryN(0)).isInstanceOf(Point.class);
            assertThat(collection.getGeometryN(1)).isInstanceOf(LineString.class);
        }

        @Test
        void readsNestedGeometryCollection() throws IOException {
            String inner = "{\"type\":\"GeometryCollection\",\"geometries\":[" + geo("Point", "[1,2]") + "]}";
            GeometryCollection outer = read("{\"type\":\"GeometryCollection\",\"geometries\":[" + inner + ","
                + geo("MultiPoint", "[[3,4]]") + "]}", GeometryCollection.class);
            assertThat(outer.getNumGeometries()).isEqualTo(2);
            assertThat(outer.getGeometryN(0).getGeometryType()).isEqualTo("GeometryCollection");
            assertThat(outer.getGeometryN(0).getGeometryN(0)).isInstanceOf(Point.class);
            assertThat(outer.getGeometryN(1)).isInstanceOf(MultiPoint.class);
        }

        @Test
        void genericTargetProducesRuntimeType() throws IOException {
            assertThat(read(geo("Point", "[1,2]"), Geometry.class)).isInstanceOf(Point.class);
            assertThat(read(geo("MultiPolygon", "[[" + SQUARE_CCW + "]]"), Geometry.class))
                .isInstanceOf(MultiPolygon.class);
        }

        @ParameterizedTest
        @ValueSource(strings = {"MultiPoint", "MultiLineString", "MultiPolygon"})
        void geometryCollectionTargetAcceptsMultiGeometries(String type) throws IOException {
            String coordinates = switch (type) {
                case "MultiPoint" -> "[[1,2]]";
                case "MultiLineString" -> "[[[1,2],[3,4]]]";
                default -> "[[" + SQUARE_CCW + "]]";
            };
            GeometryCollection result = read(geo(type, coordinates), GeometryCollection.class);
            assertThat(result.getGeometryType()).isEqualTo(type);
        }

        @Test
        void typeAfterCoordinates() throws IOException {
            Polygon polygon = read("{\"coordinates\":[" + SQUARE_CCW + "],\"type\":\"Polygon\"}", Polygon.class);
            assertThat(polygon.getNumPoints()).isEqualTo(5);
            Point point = read("{\"coordinates\":[1,2],\"bbox\":[1,2,1,2],\"type\":\"Point\"}", Point.class);
            assertThat(point.getX()).isEqualTo(1);
            GeometryCollection collection = read("{\"geometries\":[" + geo("Point", "[1,2]")
                + "],\"type\":\"GeometryCollection\"}", GeometryCollection.class);
            assertThat(collection.getNumGeometries()).isEqualTo(1);
        }

        @Test
        void typeAfterCoordinatesForEveryCoordinateType() throws IOException {
            assertThat(read("{\"coordinates\":[[1,2],[3,4]],\"type\":\"LineString\"}", Geometry.class))
                .isInstanceOf(LineString.class);
            assertThat(read("{\"coordinates\":[[1,2],[3,4]],\"type\":\"MultiPoint\"}", Geometry.class))
                .isInstanceOf(MultiPoint.class);
            assertThat(read("{\"coordinates\":[[[1,2],[3,4]]],\"type\":\"MultiLineString\"}", Geometry.class))
                .isInstanceOf(MultiLineString.class);
            assertThat(read("{\"coordinates\":[[" + SQUARE_CCW + "]],\"type\":\"MultiPolygon\"}", Geometry.class))
                .isInstanceOf(MultiPolygon.class);
        }

        @Test
        void unknownMembersAreSkipped() throws IOException {
            String json = "{\"bbox\":[0,0,1,1],\"crs\":{\"type\":\"name\",\"properties\":{\"name\":\"x\"}},"
                + "\"type\":\"Point\",\"id\":7,\"properties\":{\"a\":[1,{\"b\":[2]}],\"coordinates\":\"no\"},"
                + "\"coordinates\":[1,2],\"@class\":\"org.locationtech.jts.geom.Point\",\"extra\":null}";
            Point point = read(json, Point.class);
            assertThat(point.getX()).isEqualTo(1);
            assertThat(point.getY()).isEqualTo(2);
        }

        @Test
        void foreignMembersOfOtherTypeAreSkipped() throws IOException {
            // "geometries" on a Point and "coordinates" on a GeometryCollection are foreign members
            Point point = read("{\"type\":\"Point\",\"geometries\":[\"junk\"],\"coordinates\":[1,2]}", Point.class);
            assertThat(point.getX()).isEqualTo(1);
            GeometryCollection collection = read(
                "{\"type\":\"GeometryCollection\",\"coordinates\":[\"junk\"],\"geometries\":[]}",
                GeometryCollection.class);
            assertThat(collection.isEmpty()).isTrue();

            // ... also when they come before "type"
            Point early = read("{\"geometries\":7,\"coordinates\":[1,2],\"type\":\"Point\"}", Point.class);
            assertThat(early.getY()).isEqualTo(2);
            GeometryCollection earlyCollection = read(
                "{\"coordinates\":\"x\",\"geometries\":[],\"type\":\"GeometryCollection\"}", GeometryCollection.class);
            assertThat(earlyCollection.isEmpty()).isTrue();
            assertThatThrownBy(() -> read("{\"coordinates\":\"x\",\"type\":\"Point\"}", Point.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Missing or invalid 'coordinates' field");
            assertThatThrownBy(() -> read("{\"geometries\":{},\"type\":\"GeometryCollection\"}", Geometry.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Missing or invalid 'geometries' field");
        }

        @Test
        void jsonNullGivesNull() throws IOException {
            assertThat(read("null", Point.class)).isNull();
        }

        @Test
        void parserWithoutCurrentTokenIsAdvanced() throws IOException {
            try (JsonParser parser = JSON.createParser(geo("Point", "[1,2]"))) {
                assertThat(parser.currentToken()).isNull();
                assertThat(GeoJsonGeometryReader.read(parser, Point.class, FULL).getY()).isEqualTo(2);
            }
            try (JsonParser parser = JSON.createParser("null")) {
                assertThat(GeoJsonGeometryReader.read(parser, Point.class, FULL)).isNull();
            }
            try (JsonParser parser = JSON.createParser("")) {
                assertThatThrownBy(() -> GeoJsonGeometryReader.read(parser, Point.class, FULL))
                    .isInstanceOf(GeoJsonParseException.class)
                    .hasMessageContaining("expected a JSON object but got end of input");
            }
        }

        @Test
        void entryAtFieldNameMidObject() throws IOException {
            try (JsonParser parser = JSON.createParser(
                    "{\"@class\":\"x\",\"type\":\"Point\",\"coordinates\":[1,2]} [3]")) {
                parser.nextToken(); // START_OBJECT
                parser.nextToken(); // FIELD_NAME @class
                parser.nextToken(); // VALUE_STRING
                assertThat(parser.nextToken()).isEqualTo(JsonToken.FIELD_NAME);
                Point point = GeoJsonGeometryReader.read(parser, Point.class, FULL);
                assertThat(point.getX()).isEqualTo(1);
                assertThat(parser.currentToken()).isEqualTo(JsonToken.END_OBJECT);
                assertThat(parser.nextToken()).isEqualTo(JsonToken.START_ARRAY);
            }
        }

        @Test
        void entryAtEndObjectMeansMissingType() throws IOException {
            try (JsonParser parser = JSON.createParser("{\"@class\":\"x\"}")) {
                parser.nextToken();
                parser.nextToken();
                parser.nextToken();
                assertThat(parser.nextToken()).isEqualTo(JsonToken.END_OBJECT);
                assertThatThrownBy(() -> GeoJsonGeometryReader.read(parser, Point.class, FULL))
                    .isInstanceOf(GeoJsonParseException.class)
                    .hasMessageContaining("Missing 'type' field");
            }
        }

        @Test
        void readsConsecutiveGeometriesFromOneParser() throws IOException {
            String json = "[" + geo("Point", "[1,2]") + "," + geo("Point", "[3,4]") + "]";
            try (JsonParser parser = JSON.createParser(json)) {
                parser.nextToken();
                List<Point> points = new ArrayList<>();
                while (parser.nextToken() == JsonToken.START_OBJECT) {
                    points.add(GeoJsonGeometryReader.read(parser, Point.class, FULL));
                }
                assertThat(parser.currentToken()).isEqualTo(JsonToken.END_ARRAY);
                assertThat(points).extracting(Point::getX).containsExactly(1.0, 3.0);
            }
        }
    }

    @Nested
    class Empties {

        static Stream<Arguments> emptyCoordinates() {
            return Stream.of(
                Arguments.of("Point", Point.class),
                Arguments.of("LineString", LineString.class),
                Arguments.of("Polygon", Polygon.class),
                Arguments.of("MultiPoint", MultiPoint.class),
                Arguments.of("MultiLineString", MultiLineString.class),
                Arguments.of("MultiPolygon", MultiPolygon.class));
        }

        @ParameterizedTest
        @MethodSource("emptyCoordinates")
        void emptyCoordinatesGiveEmptyGeometryOfRequestedType(String type, Class<? extends Geometry> jtsClass)
                throws IOException {
            Geometry geometry = read(geo(type, "[]"), jtsClass);
            assertThat(geometry).isInstanceOf(jtsClass);
            assertThat(geometry.isEmpty()).isTrue();
            assertThat(geometry.getGeometryType()).isEqualTo(type);
            assertThat(read(geo(type, "[]"), Geometry.class).getGeometryType()).isEqualTo(type);
        }

        @Test
        void emptyGeometriesGiveEmptyCollection() throws IOException {
            GeometryCollection collection = read("{\"type\":\"GeometryCollection\",\"geometries\":[]}",
                GeometryCollection.class);
            assertThat(collection.isEmpty()).isTrue();
            assertThat(collection.getGeometryType()).isEqualTo("GeometryCollection");
        }

        @Test
        void emptyMembersInsideCollection() throws IOException {
            GeometryCollection collection = read("{\"type\":\"GeometryCollection\",\"geometries\":["
                + geo("Point", "[]") + "," + geo("Polygon", "[]") + "]}", GeometryCollection.class);
            assertThat(collection.getNumGeometries()).isEqualTo(2);
            assertThat(collection.getGeometryN(0).isEmpty()).isTrue();
            assertThat(collection.getGeometryN(1)).isInstanceOf(Polygon.class);
        }

        @Test
        void emptyMembersOfMultiGeometriesAreRejected() {
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[]]"), MultiPolygon.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid polygon coordinate array at index 0");
            assertThatThrownBy(() -> read(geo("MultiLineString", "[[]]"), MultiLineString.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("LineString at index 0 must have at least 2 points");
            assertThatThrownBy(() -> read(geo("MultiPoint", "[[]]"), MultiPoint.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid 'coordinates' for MultiPoint");
        }
    }

    @Nested
    class Altitude {

        @Test
        void thirdNumberIsKeptAsZ() throws IOException {
            Point point = read(geo("Point", "[1,2,300.5]"), Point.class);
            assertThat(point.getCoordinateSequence().getDimension()).isEqualTo(3);
            assertThat(point.getCoordinate().getZ()).isEqualTo(300.5);
        }

        @Test
        void fourthAndFurtherNumbersAreIgnored() throws IOException {
            Point point = read(geo("Point", "[1,2,3,4,5]"), Point.class);
            assertThat(point.getCoordinate().getZ()).isEqualTo(3);
            assertThat(point.getCoordinateSequence().getDimension()).isEqualTo(3);
        }

        @Test
        void fourthElementMustStillBeANumber() {
            assertThatThrownBy(() -> read(geo("Point", "[1,2,3,\"m\"]"), Point.class))
                .isInstanceOf(GeoJsonParseException.class);
        }

        @Test
        void positionsWithoutAltitudeInA3dGeometryGetNaN() throws IOException {
            LineString line = read(geo("LineString", "[[1,2],[3,4,5],[6,7]]"), LineString.class);
            CoordinateSequence sequence = line.getCoordinateSequence();
            assertThat(sequence.getDimension()).isEqualTo(3);
            assertThat(sequence.getZ(0)).isNaN();
            assertThat(sequence.getZ(1)).isEqualTo(5);
            assertThat(sequence.getZ(2)).isNaN();
        }

        @Test
        void dimensionIsDecidedPerGeometry() throws IOException {
            Polygon polygon = read(geo("Polygon",
                "[[[0,0,1],[10,0,1],[10,10,1],[0,10,1],[0,0,1]]," + HOLE_CW + "]"), Polygon.class);
            assertThat(polygon.getExteriorRing().getCoordinateSequence().getDimension()).isEqualTo(3);
            assertThat(polygon.getInteriorRingN(0).getCoordinateSequence().getDimension()).isEqualTo(3);
            assertThat(polygon.getInteriorRingN(0).getCoordinateSequence().getZ(0)).isNaN();

            GeometryCollection collection = read("{\"type\":\"GeometryCollection\",\"geometries\":["
                + geo("Point", "[1,2,3]") + "," + geo("Point", "[1,2]") + "]}", GeometryCollection.class);
            assertThat(((Point) collection.getGeometryN(0)).getCoordinateSequence().getDimension()).isEqualTo(3);
            assertThat(((Point) collection.getGeometryN(1)).getCoordinateSequence().getDimension()).isEqualTo(2);
        }

        @Test
        void nonFiniteAltitudeIsRejected() throws IOException {
            assertThatThrownBy(() -> readLenient(geo("Point", "[1,2,NaN]"), FULL))
                .isInstanceOf(InvalidCoordinateException.class)
                .hasMessageContaining("altitude");
        }

        @Test
        void altitudeIsNotRangeChecked() throws IOException {
            assertThat(read(geo("Point", "[1,2,8848.86]"), Point.class).getCoordinate().getZ()).isEqualTo(8848.86);
            assertThat(read(geo("Point", "[1,2,-11000]"), Point.class).getCoordinate().getZ()).isEqualTo(-11000);
        }
    }

    static Geometry readLenient(String json, GeoJsonOptions options) throws IOException {
        JsonFactory lenient = new JsonFactoryBuilder().enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS).build();
        try (JsonParser parser = lenient.createParser(json)) {
            parser.nextToken();
            return GeoJsonGeometryReader.read(parser, Geometry.class, options);
        }
    }

    @Nested
    class OrdinateValidation {

        @ParameterizedTest
        @ValueSource(strings = {
            "[\"abc\",\"xyz\"]", "[null,null]", "[true,false]", "[\"1\",\"2\"]", "[1,\"2\"]", "[1,null]",
            "[1,{\"a\":1}]", "[{\"x\":1},2]", "[1,[2]]", "[1]", "[[]]", "[[1,2]]"
        })
        void malformedPointPositionsAreRejected(String coordinates) {
            assertThatThrownBy(() -> read(geo("Point", coordinates), Point.class))
                .isInstanceOf(GeoJsonParseException.class);
        }

        @Test
        void stringOrdinatesAreNotSilentlyConverted() {
            assertThatThrownBy(() -> read(geo("Point", "[\"abc\",\"xyz\"]"), Point.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("expected a number or an array but got a string");
            assertThatThrownBy(() -> read(geo("Point", "[1,\"2\"]"), Point.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("expected a number but got a string");
            assertThatThrownBy(() -> read(geo("Point", "[true,false]"), Point.class))
                .hasMessageContaining("a boolean");
            assertThatThrownBy(() -> read(geo("Point", "[null,null]"), Point.class))
                .hasMessageContaining("got null");
        }

        @Test
        void tooFewOrdinates() {
            assertThatThrownBy(() -> read(geo("Point", "[1]"), Point.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Coordinates array must have at least 2 elements");
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2],[3]]"), LineString.class))
                .hasMessageContaining("Invalid coordinate pair at index 1: a position must have at least 2 numbers");
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2],[]]"), LineString.class))
                .hasMessageContaining("Invalid coordinate pair at index 1");
        }

        @Test
        void wrongNestingIsRejected() {
            assertThatThrownBy(() -> read(geo("Point", "[[10,20],[30,40]]"), Point.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid 'coordinates' for Point");
            assertThatThrownBy(() -> read(geo("LineString", "[[[0,0],[1,1]],[[2,2],[3,3]]]"), LineString.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid 'coordinates' for LineString");
            assertThatThrownBy(() -> read(geo("LineString", "[1,2]"), LineString.class))
                .hasMessageContaining("Invalid 'coordinates' for LineString");
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2],[[3,4]]]"), LineString.class))
                .hasMessageContaining("Invalid coordinate pair at index 1: expected a number but got an array");
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2],\"x\"]"), LineString.class))
                .hasMessageContaining("Invalid coordinate pair at index 1: expected an array of numbers");
            assertThatThrownBy(() -> read(geo("Polygon", SQUARE_CCW), Polygon.class))
                .hasMessageContaining("Invalid 'coordinates' for Polygon");
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[" + SQUARE_CCW + "]"), MultiPolygon.class))
                .hasMessageContaining("Invalid polygon coordinate array at index 0");
            assertThatThrownBy(() -> read(geo("MultiLineString", "[[1,2],[3,4]]"), MultiLineString.class))
                .hasMessageContaining("Invalid 'coordinates' for MultiLineString");
            assertThatThrownBy(() -> read(geo("MultiLineString", "[[[1,2],[3,4]],[5,6]]"), MultiLineString.class))
                .hasMessageContaining("Invalid coordinate array at index 1");
            assertThatThrownBy(() -> read(geo("Polygon", "[" + SQUARE_CCW + ",[1,2]]"), Polygon.class))
                .hasMessageContaining("a polygon ring must be an array of positions");
            assertThatThrownBy(() -> read(geo("MultiPoint", "[[[1,2]]]"), MultiPoint.class))
                .hasMessageContaining("Invalid 'coordinates' for MultiPoint");
            assertThatThrownBy(() -> read(geo("Polygon", "[[[0,0],[1,0]],\"x\"]"), Polygon.class))
                .hasMessageContaining("Invalid coordinate array: expected an array but got a string");
        }

        @Test
        void coordinatesNestedTooDeep() {
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[[[[1,2]]]]]"), MultiPolygon.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("nested deeper than 4 levels");
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[[[[[[[[[1,2]]]]]]]]]]"), MultiPolygon.class))
                .hasMessageContaining("nested deeper than 4 levels");
        }

        @Test
        void outOfRangeOrdinatesAreRejectedWithRangeValidation() {
            assertThatThrownBy(() -> read(geo("Point", "[181,0]"), Point.class))
                .isInstanceOfSatisfying(InvalidCoordinateException.class, e -> {
                    assertThat(e.getCoordinateType()).isEqualTo("longitude");
                    assertThat(e.getValue()).isEqualTo(181);
                    assertThat(e.getMinValue()).isEqualTo(-180);
                    assertThat(e.getMaxValue()).isEqualTo(180);
                    assertThat(e.getOriginalMessage())
                        .isEqualTo("Coordinate out of range: longitude 181.000000 not in [-180.000000, 180.000000]");
                    assertThat(e.getLocation()).isNotNull();
                    assertThat(e.getLocation().getColumnNr()).isGreaterThan(1);
                });
            assertThatThrownBy(() -> read(geo("LineString", "[[0,0],[0,-90.5]]"), LineString.class))
                .isInstanceOfSatisfying(InvalidCoordinateException.class,
                    e -> assertThat(e.getCoordinateType()).isEqualTo("latitude"));
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[[[0,0],[200,0],[10,10],[0,0]]]]"), MultiPolygon.class))
                .isInstanceOf(InvalidCoordinateException.class);
        }

        @Test
        void outOfRangeOrdinatesAreAcceptedWithoutRangeValidation() throws IOException {
            Point point = read(geo("Point", "[13523000.5,3662000.2]"), Point.class, NO_RANGE);
            assertThat(point.getX()).isEqualTo(13523000.5);
        }

        @Test
        void infinityIsRejectedInBothModes() {
            assertThatThrownBy(() -> read(geo("Point", "[1e400,0]"), Point.class, NO_RANGE))
                .isInstanceOfSatisfying(InvalidCoordinateException.class, e -> {
                    assertThat(e.getCoordinateType()).isEqualTo("longitude");
                    assertThat(e.getValue()).isInfinite();
                });
            assertThatThrownBy(() -> read(geo("Point", "[0,-1e400]"), Point.class, FULL))
                .isInstanceOf(InvalidCoordinateException.class);
        }

        static Stream<Arguments> nanInEveryType() {
            return Stream.of(
                Arguments.of(geo("Point", "[NaN,1]")),
                Arguments.of(geo("Point", "[1,NaN]")),
                Arguments.of(geo("LineString", "[[0,0],[NaN,NaN]]")),
                Arguments.of(geo("MultiPoint", "[[0,0],[NaN,1]]")),
                Arguments.of(geo("MultiLineString", "[[[0,0],[1,NaN]]]")),
                Arguments.of(geo("Polygon", "[[[0,0],[1,0],[NaN,1],[0,0]]]")),
                Arguments.of(geo("MultiPolygon", "[[[[0,0],[1,0],[NaN,1],[0,0]]]]")),
                Arguments.of("{\"type\":\"GeometryCollection\",\"geometries\":["
                    + geo("Point", "[Infinity,0]") + "]}"));
        }

        @ParameterizedTest
        @MethodSource("nanInEveryType")
        void nanIsRejectedWithRangeValidation(String json) {
            assertThatThrownBy(() -> readLenient(json, FULL)).isInstanceOf(InvalidCoordinateException.class);
        }

        @ParameterizedTest
        @MethodSource("nanInEveryType")
        void nanIsRejectedWithoutRangeValidation(String json) {
            assertThatThrownBy(() -> readLenient(json, NO_RANGE.withValidation(GeometryValidation.NONE)))
                .isInstanceOf(InvalidCoordinateException.class);
        }

        @Test
        void boundaryValuesAreAccepted() throws IOException {
            assertThat(read(geo("Point", "[-180,-90]"), Point.class).getX()).isEqualTo(-180);
            assertThat(read(geo("Point", "[180,90]"), Point.class).getY()).isEqualTo(90);
        }
    }

    @Nested
    class Structure {

        @Test
        void lineStringNeedsTwoPoints() {
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2]]"), LineString.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("LineString must have at least 2 points");
            assertThatThrownBy(() -> read(geo("MultiLineString", "[[[1,2],[3,4]],[[1,2]]]"), MultiLineString.class))
                .hasMessageContaining("LineString at index 1 must have at least 2 points");
        }

        @Test
        void ringsNeedFourPoints() {
            assertThatThrownBy(() -> read(geo("Polygon", "[[[0,0],[1,0],[0,0]]]"), Polygon.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("a polygon ring must have at least 4 points");
            assertThatThrownBy(() -> read(geo("Polygon", "[[]]"), Polygon.class))
                .hasMessageContaining("a polygon ring must have at least 4 points");
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[[[0,0],[1,0],[0,0]]]]"), MultiPolygon.class))
                .hasMessageContaining("a polygon ring must have at least 4 points");
        }

        @Test
        void ringsMustBeClosed() {
            assertThatThrownBy(() -> read(geo("Polygon", "[[[0,0],[1,0],[1,1],[0,1]]]"), Polygon.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class, e -> {
                    assertThat(e.getOriginalMessage()).isEqualTo(
                        "Invalid ring: first point (0.000000,0.000000) != last point (0.000000,1.000000) "
                            + "[field=coordinates]");
                    assertThat(e.getInvalidField()).isEqualTo("coordinates");
                });
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[[[0,0],[1,0],[1,1],[0,1]]]]"), MultiPolygon.class))
                .hasMessageContaining("Invalid ring");
        }

        @Test
        void polygonOrientationIsNormalised() throws IOException {
            Polygon polygon = read(geo("Polygon", "[" + SQUARE_CW + "," + HOLE_CCW + "]"), Polygon.class);
            assertThat(Orientation.isCCW(polygon.getExteriorRing().getCoordinateSequence())).isTrue();
            assertThat(Orientation.isCCW(polygon.getInteriorRingN(0).getCoordinateSequence())).isFalse();
            assertThat(coords(polygon.getExteriorRing()))
                .isDeepEqualTo(new double[][] {{0, 0}, {10, 0}, {10, 10}, {0, 10}, {0, 0}});
        }

        @Test
        void multiPolygonOrientationIsNormalised() throws IOException {
            MultiPolygon multiPolygon = read(geo("MultiPolygon",
                "[[" + SQUARE_CW + "," + HOLE_CCW + "],[[[20,20],[20,30],[30,30],[30,20],[20,20]]]]"),
                MultiPolygon.class);
            for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
                Polygon polygon = (Polygon) multiPolygon.getGeometryN(i);
                assertThat(Orientation.isCCW(polygon.getExteriorRing().getCoordinateSequence())).isTrue();
                for (int h = 0; h < polygon.getNumInteriorRing(); h++) {
                    assertThat(Orientation.isCCW(polygon.getInteriorRingN(h).getCoordinateSequence())).isFalse();
                }
            }
        }

        @Test
        void fullValidationRejectsInvalidPolygons() {
            assertThatThrownBy(() -> read(geo("Polygon", "[" + BOWTIE + "]"), Polygon.class, FULL))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid polygon geometry: not valid according to OGC rules")
                .hasMessageContaining("Self-intersection");
            assertThatThrownBy(() -> read(geo("MultiPolygon", "[[" + BOWTIE + "]]"), MultiPolygon.class, FULL))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Invalid multipolygon geometry: not valid according to OGC rules");
        }

        @Test
        void fullValidationRejectsOverlappingMultiPolygonMembersAndHolesOutsideShell() {
            String overlapping = "[[" + SQUARE_CCW + "],[[[5,5],[15,5],[15,15],[5,15],[5,5]]]]";
            assertThatThrownBy(() -> read(geo("MultiPolygon", overlapping), MultiPolygon.class, FULL))
                .isInstanceOf(GeoJsonParseException.class);
            String holeOutside = "[" + SQUARE_CCW + ",[[20,20],[20,22],[22,22],[22,20],[20,20]]]";
            assertThatThrownBy(() -> read(geo("Polygon", holeOutside), Polygon.class, FULL))
                .isInstanceOf(GeoJsonParseException.class);
        }

        @Test
        void fullValidationAppliesToPolygonalMembersOfCollections() {
            String json = "{\"type\":\"GeometryCollection\",\"geometries\":[" + geo("Point", "[1,2]") + ","
                + geo("Polygon", "[" + BOWTIE + "]") + "]}";
            assertThatThrownBy(() -> read(json, GeometryCollection.class, FULL))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("not valid according to OGC rules");
        }

        @ParameterizedTest
        @ValueSource(strings = {"BASIC", "NONE"})
        void basicAndNoneSkipOgcValidation(GeometryValidation validation) throws IOException {
            GeoJsonOptions options = FULL.withValidation(validation);
            Polygon polygon = read(geo("Polygon", "[" + BOWTIE + "]"), Polygon.class, options);
            assertThat(polygon.isValid()).isFalse();
            MultiPolygon multiPolygon = read(geo("MultiPolygon", "[[" + BOWTIE + "]]"), MultiPolygon.class, options);
            assertThat(multiPolygon.isValid()).isFalse();
            // structural checks remain
            assertThatThrownBy(() -> read(geo("Polygon", "[[[0,0],[1,0],[1,1],[0,1]]]"), Polygon.class, options))
                .hasMessageContaining("Invalid ring");
        }
    }

    @Nested
    class TypeHandling {

        @Test
        void typeMismatchPopulatesTypes() {
            assertThatThrownBy(() -> read(geo("LineString", "[[1,2],[3,4]]"), Point.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class, e -> {
                    assertThat(e.getExpectedType()).isEqualTo("Point");
                    assertThat(e.getActualType()).isEqualTo("LineString");
                    assertThat(e.getInvalidField()).isEqualTo("type");
                    assertThat(e.getOriginalMessage())
                        .isEqualTo("Invalid GeoJSON type: expected Point, got LineString [field=type]");
                });
        }

        @Test
        void typeMismatchIsDetectedBeforeCoordinatesAreParsed() {
            // the invalid coordinates after "type" are never looked at
            assertThatThrownBy(() -> read("{\"type\":\"Polygon\",\"coordinates\":[\"junk\"]}", Point.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class,
                    e -> assertThat(e.getActualType()).isEqualTo("Polygon"));
        }

        @Test
        void geometryCollectionTargetRejectsSingleGeometries() {
            assertThatThrownBy(() -> read(geo("Point", "[1,2]"), GeometryCollection.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class, e -> {
                    assertThat(e.getExpectedType()).isEqualTo("GeometryCollection");
                    assertThat(e.getActualType()).isEqualTo("Point");
                });
        }

        @Test
        void multiPolygonTargetRejectsGeometryCollection() {
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\",\"geometries\":[]}", MultiPolygon.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("expected MultiPolygon, got GeometryCollection");
        }

        @Test
        void unsupportedTypes() {
            assertThatThrownBy(() -> read("{\"type\":\"Feature\",\"geometry\":null}", Geometry.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("Unsupported GeoJSON type: Feature");
            assertThatThrownBy(() -> read("{\"type\":\"Feature\"}", Point.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class,
                    e -> assertThat(e.getActualType()).isEqualTo("Feature"));
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\",\"geometries\":[{\"type\":\"Circle\"}]}",
                    Geometry.class))
                .hasMessageContaining("Unsupported geometry type in GeometryCollection: Circle");
        }

        @Test
        void missingOrInvalidMembers() {
            assertThatThrownBy(() -> read("{\"coordinates\":[1,2]}", Point.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class,
                    e -> assertThat(e.getOriginalMessage()).isEqualTo("Missing 'type' field [field=type]"));
            assertThatThrownBy(() -> read("{\"type\":7,\"coordinates\":[1,2]}", Point.class))
                .hasMessageContaining("Invalid 'type' field");
            assertThatThrownBy(() -> read("{\"type\":\"Point\"}", Point.class))
                .hasMessageContaining("Missing or invalid 'coordinates' field");
            assertThatThrownBy(() -> read("{\"type\":\"Point\",\"coordinates\":null}", Point.class))
                .hasMessageContaining("Missing or invalid 'coordinates' field");
            assertThatThrownBy(() -> read("{\"type\":\"Point\",\"coordinates\":\"1,2\"}", Point.class))
                .hasMessageContaining("Missing or invalid 'coordinates' field");
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\"}", GeometryCollection.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class,
                    e -> assertThat(e.getInvalidField()).isEqualTo("geometries"));
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\",\"geometries\":{}}",
                    GeometryCollection.class))
                .hasMessageContaining("Missing or invalid 'geometries' field");
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\",\"geometries\":[{\"coordinates\":[1,2]}]}",
                    GeometryCollection.class))
                .hasMessageContaining("Missing 'type' field in geometry at index 0");
            assertThatThrownBy(() -> read("{\"type\":\"GeometryCollection\",\"geometries\":[" + geo("Point", "[1,2]")
                    + ",null]}", GeometryCollection.class))
                .hasMessageContaining("Invalid geometry at index 1 in 'geometries'");
        }

        @ParameterizedTest
        @ValueSource(strings = {"[1,2]", "\"Point\"", "42", "true"})
        void nonObjectInputIsRejected(String json) {
            assertThatThrownBy(() -> read(json, Geometry.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("expected a JSON object");
        }

        @Test
        void collectionNestingIsLimited() {
            StringBuilder json = new StringBuilder();
            int depth = GeoJsonGeometryReader.MAX_COLLECTION_DEPTH + 1;
            for (int i = 0; i < depth; i++) {
                json.append("{\"type\":\"GeometryCollection\",\"geometries\":[");
            }
            json.append("]}".repeat(depth));
            assertThatThrownBy(() -> read(json.toString(), Geometry.class))
                .isInstanceOf(GeoJsonParseException.class)
                .hasMessageContaining("maximum depth");

            StringBuilder ok = new StringBuilder();
            for (int i = 0; i < GeoJsonGeometryReader.MAX_COLLECTION_DEPTH; i++) {
                ok.append("{\"type\":\"GeometryCollection\",\"geometries\":[");
            }
            ok.append("]}".repeat(GeoJsonGeometryReader.MAX_COLLECTION_DEPTH));
            assertThatCode(() -> read(ok.toString(), Geometry.class));
        }

        private void assertThatCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable callable) {
            org.assertj.core.api.Assertions.assertThatCode(callable).doesNotThrowAnyException();
        }
    }

    @Nested
    class Factories {

        private final GeometryFactory packed = new GeometryFactory(new PrecisionModel(), 3857,
            PackedCoordinateSequenceFactory.DOUBLE_FACTORY);

        private Geometry readWith(String json, GeometryFactory factory) throws IOException {
            try (JsonParser parser = JSON.createParser(json)) {
                parser.nextToken();
                return GeoJsonGeometryReader.read(parser, Geometry.class, NO_RANGE, factory,
                    GeoJsonGeometryReader.FAST_DOUBLE_PARSER);
            }
        }

        static Stream<String> everyType() {
            return Stream.of(
                geo("Point", "[1,2]"),
                geo("Point", "[1,2,3]"),
                geo("LineString", "[[1,2],[3,4,5]]"),
                geo("Polygon", "[" + SQUARE_CW + "," + HOLE_CCW + "]"),
                geo("MultiPoint", "[[1,2],[3,4]]"),
                geo("MultiLineString", "[[[1,2],[3,4]],[[5,6],[7,8]]]"),
                geo("MultiPolygon", "[[" + SQUARE_CCW + "],[[[20,20],[30,20],[30,30],[20,20]]]]"),
                "{\"type\":\"GeometryCollection\",\"geometries\":[" + geo("Point", "[1,2]") + ","
                    + geo("Polygon", "[" + SQUARE_CCW + "]") + "]}");
        }

        @ParameterizedTest
        @MethodSource("everyType")
        void packedFactoryProducesPackedSequences(String json) throws IOException {
            Geometry geometry = readWith(json, packed);
            assertThat(geometry.getSRID()).isEqualTo(3857);
            List<CoordinateSequence> sequences = new ArrayList<>();
            geometry.apply(new org.locationtech.jts.geom.CoordinateSequenceFilter() {
                @Override
                public void filter(CoordinateSequence seq, int i) {
                    if (i == 0) {
                        sequences.add(seq);
                    }
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
            assertThat(sequences).isNotEmpty()
                .allSatisfy(seq -> assertThat(seq).isInstanceOf(PackedCoordinateSequence.Double.class));
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                assertThat(geometry.getGeometryN(i).getSRID()).isEqualTo(3857);
            }
        }

        @Test
        void packedSequencesKeepValuesAndOrientation() throws IOException {
            Polygon polygon = (Polygon) readWith(geo("Polygon", "[" + SQUARE_CW + "," + HOLE_CCW + "]"), packed);
            assertThat(coords(polygon.getExteriorRing()))
                .isDeepEqualTo(new double[][] {{0, 0}, {10, 0}, {10, 10}, {0, 10}, {0, 0}});
            assertThat(Orientation.isCCW(polygon.getInteriorRingN(0).getCoordinateSequence())).isFalse();
            LineString line = (LineString) readWith(geo("LineString", "[[1,2],[3,4,5]]"), packed);
            assertThat(line.getCoordinateSequence().getDimension()).isEqualTo(3);
            assertThat(line.getCoordinateSequence().getZ(0)).isNaN();
            assertThat(line.getCoordinateSequence().getZ(1)).isEqualTo(5);
            MultiPoint multiPoint = (MultiPoint) readWith(geo("MultiPoint", "[[1,2],[3,4]]"), packed);
            assertThat(coords(multiPoint)).isDeepEqualTo(new double[][] {{1, 2}, {3, 4}});
        }

        @Test
        void largePositionListsGrow() throws IOException {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < 1000; i++) {
                json.append(i == 0 ? "" : ",").append('[').append(i * 0.1).append(',').append(i * 0.05);
                if (i == 500) {
                    json.append(",7");
                }
                json.append(']');
            }
            json.append(']');
            for (GeometryFactory factory : List.of(packed, new GeometryFactory())) {
                LineString line = (LineString) readWith(geo("LineString", json.toString()), factory);
                assertThat(line.getNumPoints()).isEqualTo(1000);
                assertThat(line.getCoordinateSequence().getX(999)).isEqualTo(999 * 0.1);
                assertThat(line.getCoordinateSequence().getZ(500)).isEqualTo(7);
                assertThat(line.getCoordinateSequence().getZ(499)).isNaN();
                assertThat(line.getCoordinateSequence().getZ(999)).isNaN();
            }
        }

        @Test
        void arrayFactoryProducesCoordinateArraySequences() throws IOException {
            Geometry geometry = readWith(geo("LineString", "[[1,2],[3,4]]"), new GeometryFactory());
            assertThat(((LineString) geometry).getCoordinateSequence()).isInstanceOf(CoordinateArraySequence.class);
        }

        @Test
        void customSequenceFactoryIsUsed() throws IOException {
            GeometryFactory floatFactory = new GeometryFactory(new PrecisionModel(), 0,
                PackedCoordinateSequenceFactory.FLOAT_FACTORY);
            LineString line = (LineString) readWith(geo("LineString", "[[1.5,2],[3,4,5]]"), floatFactory);
            assertThat(line.getCoordinateSequence()).isInstanceOf(PackedCoordinateSequence.Float.class);
            assertThat(line.getCoordinateSequence().getX(0)).isEqualTo(1.5);
            assertThat(line.getCoordinateSequence().getZ(1)).isEqualTo(5);

            GeometryFactory generic = new GeometryFactory(new PrecisionModel(), 0,
                new org.locationtech.jts.geom.CoordinateSequenceFactory() {
                    @Override
                    public CoordinateSequence create(org.locationtech.jts.geom.Coordinate[] coordinates) {
                        return new CoordinateArraySequence(coordinates);
                    }

                    @Override
                    public CoordinateSequence create(CoordinateSequence coordSeq) {
                        return new CoordinateArraySequence(coordSeq);
                    }

                    @Override
                    public CoordinateSequence create(int size, int dimension) {
                        return new CoordinateArraySequence(size, dimension);
                    }
                });
            LineString generic3d = (LineString) readWith(geo("LineString", "[[1,2,3],[4,5,6]]"), generic);
            assertThat(generic3d.getCoordinateSequence().getZ(1)).isEqualTo(6);
            assertThat(generic3d.getCoordinateSequence().getX(1)).isEqualTo(4);
        }
    }

    @Nested
    class FastDoubleParsing {

        private final JsonParser.Feature feature = GeoJsonGeometryReader.FAST_DOUBLE_PARSER;

        @Test
        void featureIsResolvedByName() {
            assertThat(feature).isNotNull();
            assertThat(feature.name()).isEqualTo("USE_FAST_DOUBLE_PARSER");
            assertThat(GeoJsonGeometryReader.resolveParserFeature("NO_SUCH_FEATURE_IN_THIS_JACKSON")).isNull();
        }

        @Test
        void enabledWhileReadingAndRestoredAfterwards() throws IOException {
            JsonParser parser = Mockito.spy(JSON.createParser(geo("LineString", "[[1.25,2.5],[3.75,4.125]]")));
            parser.nextToken();
            assertThat(parser.isEnabled(feature)).isFalse();
            List<Boolean> stateWhileParsingNumbers = new ArrayList<>();
            doAnswer(invocation -> {
                stateWhileParsingNumbers.add(parser.isEnabled(feature));
                return invocation.callRealMethod();
            }).when(parser).getDoubleValue();

            LineString line = GeoJsonGeometryReader.read(parser, LineString.class, FULL,
                GeometryFactoryProvider.getFactory(), feature);

            assertThat(line.getCoordinateN(1).getY()).isEqualTo(4.125);
            assertThat(stateWhileParsingNumbers).hasSize(4).containsOnly(true);
            assertThat(parser.isEnabled(feature)).isFalse();
            InOrder order = Mockito.inOrder(parser);
            order.verify(parser).enable(feature);
            order.verify(parser).disable(feature);
        }

        @Test
        void restoredAfterAFailure() throws IOException {
            JsonParser parser = JSON.createParser(geo("Point", "[1,\"x\"]"));
            parser.nextToken();
            assertThatThrownBy(() -> GeoJsonGeometryReader.read(parser, Point.class, FULL,
                GeometryFactoryProvider.getFactory(), feature)).isInstanceOf(GeoJsonParseException.class);
            assertThat(parser.isEnabled(feature)).isFalse();
        }

        @Test
        void alreadyEnabledStaysEnabled() throws IOException {
            JsonParser parser = Mockito.spy(JSON.createParser(geo("Point", "[1,2]")));
            parser.enable(feature);
            parser.nextToken();
            GeoJsonGeometryReader.read(parser, Point.class, FULL, GeometryFactoryProvider.getFactory(), feature);
            assertThat(parser.isEnabled(feature)).isTrue();
            verify(parser, never()).disable(any(JsonParser.Feature.class));
        }

        @Test
        void notTouchedWhenOptionIsOff() throws IOException {
            JsonParser parser = Mockito.spy(JSON.createParser(geo("Point", "[1,2]")));
            parser.nextToken();
            GeoJsonGeometryReader.read(parser, Point.class, FULL.withFastDoubleParsing(false),
                GeometryFactoryProvider.getFactory(), feature);
            verify(parser, never()).enable(any(JsonParser.Feature.class));
            assertThat(parser.isEnabled(feature)).isFalse();
        }

        @Test
        void absentFeatureStillReads() throws IOException {
            JsonParser parser = Mockito.spy(JSON.createParser(geo("Point", "[1.5,2.5]")));
            parser.nextToken();
            Point point = GeoJsonGeometryReader.read(parser, Point.class, FULL,
                GeometryFactoryProvider.getFactory(), null);
            assertThat(point.getX()).isEqualTo(1.5);
            verify(parser, never()).enable(any(JsonParser.Feature.class));
        }

        @Test
        void delegatingParsersAreNotToggled() throws IOException {
            JsonParser inner = JSON.createParser(geo("Point", "[1,2]"));
            JsonParser delegate = Mockito.spy(new JsonParserDelegate(inner));
            delegate.nextToken();
            Point point = GeoJsonGeometryReader.read(delegate, Point.class, FULL,
                GeometryFactoryProvider.getFactory(), feature);
            assertThat(point.getY()).isEqualTo(2);
            verify(delegate, never()).enable(any(JsonParser.Feature.class));
            assertThat(inner.isEnabled(feature)).isFalse();
        }

        @Test
        void fastAndSlowParsingGiveIdenticalValues() throws IOException {
            String json = geo("LineString", "[[0.1,0.2],[179.99999999999997,-89.12345678901234],"
                + "[1.7976931348623157e2,4.9e-324]]");
            LineString fast = read(json, LineString.class, FULL);
            LineString slow = read(json, LineString.class, FULL.withFastDoubleParsing(false));
            assertThat(fast.equalsExact(slow)).isTrue();
            assertThat(fast.getCoordinateN(1).getX()).isEqualTo(179.99999999999997);
        }
    }
}
