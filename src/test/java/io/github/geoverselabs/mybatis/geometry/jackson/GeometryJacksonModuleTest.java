package io.github.geoverselabs.mybatis.geometry.jackson;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import io.github.geoverselabs.mybatis.geometry.exception.GeoJsonParseException;
import io.github.geoverselabs.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.geoverselabs.mybatis.geometry.support.GeometryValidation;
import io.github.geoverselabs.mybatis.geometry.util.GeometryFactoryProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.locationtech.jts.algorithm.Orientation;
import org.locationtech.jts.geom.CoordinateXY;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.MultiLineString;
import org.locationtech.jts.geom.MultiPoint;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeometryJacksonModuleTest {

    private static final GeometryFactory FACTORY = GeometryFactoryProvider.getFactory();

    @AfterEach
    void resetGlobalOptions() {
        GeoJsonOptions.resetGlobal();
    }

    static Geometry wkt(String wkt) {
        try {
            return new WKTReader(FACTORY).read(wkt);
        } catch (ParseException e) {
            throw new IllegalArgumentException(e);
        }
    }

    static ObjectMapper mapper() {
        return new ObjectMapper().registerModule(new GeometryJacksonModule());
    }

    static ObjectMapper mapper(GeometryJacksonModule module) {
        return new ObjectMapper().registerModule(module);
    }

    // ---------------------------------------------------------------------------------------------
    // DTOs
    // ---------------------------------------------------------------------------------------------

    public static class AllTypes {
        public Point point;
        public LineString lineString;
        public Polygon polygon;
        public MultiPoint multiPoint;
        public MultiLineString multiLineString;
        public MultiPolygon multiPolygon;
        public GeometryCollection collection;
        public Geometry geometry;
        public String name;
    }

    public static class PointDto {
        public Point location;
        public String name;
    }

    public static class Wrapper {
        public PointDto inner;
    }

    public static class ListDto {
        public List<Point> points;
    }

    public static class CollectionDto {
        public GeometryCollection gc;
    }

    public static class PrecisionDto {
        @GeoJsonPrecision(2)
        public Point location;

        @GeoJsonPrecision(1)
        public List<Point> points;

        @GeoJsonPrecision(3)
        public Geometry shape;

        @GeoJsonPrecision(GeoJsonPrecision.FULL)
        public Point full;

        public Point plain;
    }

    public static class GetterPrecisionDto {
        private Point location;

        @GeoJsonPrecision(1)
        public Point getLocation() {
            return location;
        }

        public void setLocation(Point location) {
            this.location = location;
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @JacksonAnnotationsInside
    @GeoJsonPrecision(0)
    public @interface WholeUnits {
    }

    public static class BundleDto {
        @WholeUnits
        public Point location;
    }

    public static class BadPrecisionDto {
        @GeoJsonPrecision(16)
        public Point location;
    }

    public static class AnnotatedDto {
        @JsonSerialize(using = GenericGeometrySerializer.class)
        @JsonDeserialize(using = GenericGeometryDeserializer.class)
        public Geometry geometry;

        @JsonSerialize(using = GeometryCollectionSerializer.class)
        @JsonDeserialize(using = GeometryCollectionDeserializer.class)
        public GeometryCollection collection;

        @JsonSerialize(using = PointSerializer.class)
        @JsonDeserialize(using = PointDeserializer.class)
        public Point point;
    }

    public static class ProjectedDto {
        @JsonDeserialize(using = PointDeserializer.class)
        public Point location;
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    class RoundTrip {

        @Test
        void everyTypeRoundTrips() throws IOException {
            AllTypes dto = new AllTypes();
            dto.point = (Point) wkt("POINT (116.397128 39.916527)");
            dto.lineString = (LineString) wkt("LINESTRING (0 0, 1 1, 2 0.5)");
            dto.polygon = (Polygon) wkt("POLYGON ((0 0, 10 0, 10 10, 0 10, 0 0), (2 2, 2 4, 4 4, 4 2, 2 2))");
            dto.multiPoint = (MultiPoint) wkt("MULTIPOINT ((1 2), (3 4))");
            dto.multiLineString = (MultiLineString) wkt("MULTILINESTRING ((1 2, 3 4), (5 6, 7 8))");
            dto.multiPolygon = (MultiPolygon) wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)), "
                + "((5 5, 9 5, 9 9, 5 9, 5 5), (6 6, 6 7, 7 7, 7 6, 6 6)))");
            dto.collection = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2), "
                + "GEOMETRYCOLLECTION (LINESTRING (0 0, 1 1), POINT EMPTY), POLYGON EMPTY)");
            dto.geometry = wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))");
            dto.name = "all";

            ObjectMapper mapper = mapper();
            String json = mapper.writeValueAsString(dto);
            AllTypes back = mapper.readValue(json, AllTypes.class);

            assertThat(back.point.equalsExact(dto.point)).isTrue();
            assertThat(back.lineString.equalsExact(dto.lineString)).isTrue();
            assertThat(back.polygon.equalsExact(dto.polygon)).isTrue();
            assertThat(back.multiPoint.equalsExact(dto.multiPoint)).isTrue();
            assertThat(back.multiLineString.equalsExact(dto.multiLineString)).isTrue();
            assertThat(back.multiPolygon.equalsExact(dto.multiPolygon)).isTrue();
            assertThat(back.collection.equalsExact(dto.collection)).isTrue();
            assertThat(back.collection.getGeometryN(1).getGeometryN(1).isEmpty()).isTrue();
            assertThat(back.geometry).isInstanceOf(MultiPolygon.class);
            assertThat(back.geometry.equalsExact(dto.geometry)).isTrue();
            assertThat(back.name).isEqualTo("all");
            assertThat(back.point.getSRID()).isEqualTo(FACTORY.getSRID());
            assertThat(mapper.writeValueAsString(back)).isEqualTo(json);
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "POINT EMPTY", "LINESTRING EMPTY", "POLYGON EMPTY", "MULTIPOINT EMPTY", "MULTILINESTRING EMPTY",
            "MULTIPOLYGON EMPTY", "GEOMETRYCOLLECTION EMPTY"
        })
        void emptiesRoundTripThroughGeometry(String wkt) throws IOException {
            Geometry empty = wkt(wkt);
            ObjectMapper mapper = mapper();
            Geometry back = mapper.readValue(mapper.writeValueAsString(empty), Geometry.class);
            assertThat(back.isEmpty()).isTrue();
            assertThat(back.getGeometryType()).isEqualTo(empty.getGeometryType());
        }

        @Test
        void altitudeIsKeptAndWritten() throws IOException {
            ObjectMapper mapper = mapper();
            Point point = mapper.readValue("{\"type\":\"Point\",\"coordinates\":[1,2,300]}", Point.class);
            assertThat(point.getCoordinate().getZ()).isEqualTo(300);
            assertThat(mapper.writeValueAsString(point))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.0,2.0,300.0]}");
        }

        @Test
        void clockwisePolygonIsNormalisedOnBothSides() throws IOException {
            ObjectMapper mapper = mapper();
            Polygon stored = (Polygon) wkt("POLYGON ((0 0, 0 10, 10 10, 10 0, 0 0))");
            String json = mapper.writeValueAsString(stored);
            assertThat(json).isEqualTo("{\"type\":\"Polygon\",\"coordinates\":"
                + "[[[0.0,0.0],[10.0,0.0],[10.0,10.0],[0.0,10.0],[0.0,0.0]]]}");
            MultiPolygon multi = mapper.readValue(
                "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0,0],[0,10],[10,10],[10,0],[0,0]]]]}",
                MultiPolygon.class);
            assertThat(Orientation.isCCW(((Polygon) multi.getGeometryN(0)).getExteriorRing().getCoordinateSequence()))
                .isTrue();
        }

        @Test
        void geometryCollectionFieldAcceptsMultiGeometries() throws IOException {
            ObjectMapper mapper = mapper();
            CollectionDto dto = new CollectionDto();
            dto.gc = (GeometryCollection) wkt("MULTIPOINT ((1 2))");
            String json = mapper.writeValueAsString(dto);
            assertThat(json).isEqualTo("{\"gc\":{\"type\":\"MultiPoint\",\"coordinates\":[[1.0,2.0]]}}");
            assertThat(mapper.readValue(json, CollectionDto.class).gc).isInstanceOf(MultiPoint.class);

            CollectionDto polygons = mapper.readValue("{\"gc\":{\"type\":\"MultiPolygon\",\"coordinates\":"
                + "[[[[0,0],[1,0],[1,1],[0,0]]]]}}", CollectionDto.class);
            assertThat(polygons.gc).isInstanceOf(MultiPolygon.class);
        }

        @Test
        void typeMemberMayComeLast() throws IOException {
            AllTypes dto = mapper().readValue("{\"geometry\":{\"coordinates\":[[1,2],[3,4]],\"type\":\"LineString\"},"
                + "\"name\":\"x\"}", AllTypes.class);
            assertThat(dto.geometry).isInstanceOf(LineString.class);
            assertThat(dto.name).isEqualTo("x");
        }

        @Test
        void nullValues() throws IOException {
            ObjectMapper mapper = mapper();
            PointDto dto = mapper.readValue("{\"location\":null,\"name\":\"n\"}", PointDto.class);
            assertThat(dto.location).isNull();
            assertThat(mapper.writeValueAsString(dto)).isEqualTo("{\"location\":null,\"name\":\"n\"}");
            assertThat(mapper.readValue("null", Point.class)).isNull();
        }

        @Test
        void listOfGeometries() throws IOException {
            ObjectMapper mapper = mapper();
            List<Geometry> geometries = List.of(wkt("POINT (1 2)"), wkt("LINESTRING (0 0, 1 1)"));
            String json = mapper.writeValueAsString(geometries);
            List<Geometry> back = mapper.readValue(json, new TypeReference<List<Geometry>>() { });
            assertThat(back).hasSize(2);
            assertThat(back.get(1)).isInstanceOf(LineString.class);
        }
    }

    @Nested
    class JacksonIntegration {

        @Test
        void failOnTrailingTokensDoesNotBreakNestedGeometries() throws IOException {
            ObjectMapper mapper = JsonMapper.builder()
                .addModule(new GeometryJacksonModule())
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
            PointDto first = mapper.readValue(
                "{\"location\":{\"type\":\"Point\",\"coordinates\":[1,2]},\"name\":\"a\"}", PointDto.class);
            assertThat(first.location.getX()).isEqualTo(1);
            assertThat(first.name).isEqualTo("a");
            PointDto last = mapper.readValue(
                "{\"name\":\"a\",\"location\":{\"type\":\"Point\",\"coordinates\":[1,2]}}", PointDto.class);
            assertThat(last.location.getY()).isEqualTo(2);
            ListDto list = mapper.readValue("{\"points\":[{\"type\":\"Point\",\"coordinates\":[1,2]},"
                + "{\"type\":\"Point\",\"coordinates\":[3,4]}]}", ListDto.class);
            assertThat(list.points).hasSize(2);
        }

        @Test
        void parserWithoutCodecWorks() throws IOException {
            try (JsonParser parser = new JsonFactory().createParser("{\"type\":\"Point\",\"coordinates\":[1,2]}")) {
                assertThat(parser.getCodec()).isNull();
                parser.nextToken();
                Point point = new PointDeserializer().deserialize(parser, null);
                assertThat(point.getX()).isEqualTo(1);
            }
            ObjectMapper mapper = mapper();
            Geometry geometry = mapper.readValue(
                mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[1,0],[1,1],[0,0]]]}").traverse(),
                Geometry.class);
            assertThat(geometry).isInstanceOf(Polygon.class);
        }

        @Test
        void exceptionsCarryTheJsonPath() {
            ObjectMapper mapper = mapper();
            assertThatThrownBy(() -> mapper.readValue(
                    "{\"inner\":{\"location\":{\"type\":\"Point\",\"coordinates\":[500,1]}}}", Wrapper.class))
                .isInstanceOfSatisfying(InvalidCoordinateException.class, e -> {
                    assertThat(e.getPath()).extracting(JsonMappingException.Reference::getFieldName)
                        .containsExactly("inner", "location");
                    assertThat(e.getMessage()).contains("through reference chain")
                        .contains("Wrapper[\"inner\"]").contains("PointDto[\"location\"]");
                });
            assertThatThrownBy(() -> mapper.readValue(
                    "{\"inner\":{\"location\":{\"type\":\"LineString\",\"coordinates\":[[1,2],[3,4]]}}}",
                    Wrapper.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class, e -> {
                    assertThat(e.getMessage()).contains("PointDto[\"location\"]");
                    assertThat(e.getExpectedType()).isEqualTo("Point");
                });
            assertThatThrownBy(() -> mapper.readValue("{\"points\":[{\"type\":\"Point\",\"coordinates\":[1,2]},"
                    + "{\"type\":\"Point\",\"coordinates\":[\"a\",\"b\"]}]}", ListDto.class))
                .isInstanceOfSatisfying(GeoJsonParseException.class,
                    e -> assertThat(e.getMessage()).contains("ListDto[\"points\"]->java.util.ArrayList[1]"));
        }

        @Test
        void exceptionsAreJsonProcessingExceptionsFromStreams() {
            ObjectMapper mapper = mapper();
            byte[] json = "{\"location\":{\"type\":\"Point\",\"coordinates\":[500,1]}}"
                .getBytes(StandardCharsets.UTF_8);
            assertThatThrownBy(() -> mapper.readerFor(PointDto.class).readValue(new ByteArrayInputStream(json)))
                .isInstanceOf(JsonProcessingException.class)
                .isInstanceOf(InvalidCoordinateException.class)
                .hasMessageContaining("PointDto[\"location\"]")
                .hasMessageContaining("line: 1");
        }

        @Test
        void serializersWorkWithoutTheModule() throws IOException {
            ObjectMapper plain = new ObjectMapper();
            AnnotatedDto dto = new AnnotatedDto();
            dto.geometry = wkt("POINT (1 2)");
            dto.collection = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2), "
                + "GEOMETRYCOLLECTION (LINESTRING (0 0, 1 1)), MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0))))");
            dto.point = (Point) wkt("POINT (3 4)");
            String json = plain.writeValueAsString(dto);
            assertThat(json).isEqualTo("{\"geometry\":{\"type\":\"Point\",\"coordinates\":[1.0,2.0]},"
                + "\"collection\":{\"type\":\"GeometryCollection\",\"geometries\":["
                + "{\"type\":\"Point\",\"coordinates\":[1.0,2.0]},"
                + "{\"type\":\"GeometryCollection\",\"geometries\":["
                + "{\"type\":\"LineString\",\"coordinates\":[[0.0,0.0],[1.0,1.0]]}]},"
                + "{\"type\":\"MultiPolygon\",\"coordinates\":[[[[0.0,0.0],[1.0,0.0],[1.0,1.0],[0.0,0.0]]]]}]},"
                + "\"point\":{\"type\":\"Point\",\"coordinates\":[3.0,4.0]}}");
            AnnotatedDto back = plain.readValue(json, AnnotatedDto.class);
            assertThat(back.geometry.equalsExact(dto.geometry)).isTrue();
            assertThat(back.collection.equalsExact(dto.collection)).isTrue();
            assertThat(back.point.equalsExact(dto.point)).isTrue();
        }

        @Test
        void deserializerAnnotationUsesGlobalOptionsAtCallTime() throws IOException {
            ObjectMapper plain = new ObjectMapper();
            String projected = "{\"location\":{\"type\":\"Point\",\"coordinates\":[13523000.5,3662000.2]}}";
            assertThatThrownBy(() -> plain.readValue(projected, ProjectedDto.class))
                .isInstanceOf(InvalidCoordinateException.class);
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinateRangeValidation(false));
            assertThat(plain.readValue(projected, ProjectedDto.class).location.getX()).isEqualTo(13523000.5);
        }
    }

    @Nested
    class Options {

        private final Point point = FACTORY.createPoint(new CoordinateXY(1.23456789, -4.56789012));

        @Test
        void globalPrecisionIsReadAtCallTime() throws IOException {
            ObjectMapper mapper = mapper();
            assertThat(mapper.writeValueAsString(point))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.23456789,-4.56789012]}");
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(2));
            assertThat(mapper.writeValueAsString(point)).isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.23,-4.57]}");
            assertThat(new ObjectMapper().registerModule(new GeometryJacksonModule(false)).writeValueAsString(point))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.23,-4.57]}");
        }

        @Test
        void explicitOptionsIgnoreTheGlobalOnes() throws IOException {
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(2));
            ObjectMapper mapper = mapper(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(4)));
            assertThat(mapper.writeValueAsString(point))
                .isEqualTo("{\"type\":\"Point\",\"coordinates\":[1.2346,-4.5679]}");

            ObjectMapper strict = mapper(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS));
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinateRangeValidation(false));
            assertThatThrownBy(() -> strict.readValue("{\"type\":\"Point\",\"coordinates\":[500,1]}", Point.class))
                .isInstanceOf(InvalidCoordinateException.class);
        }

        @Test
        void globalValidationIsReadAtCallTime() throws IOException {
            ObjectMapper mapper = mapper();
            String bowtie = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[10,10],[10,0],[0,10],[0,0]]]}";
            assertThatThrownBy(() -> mapper.readValue(bowtie, Polygon.class)).isInstanceOf(GeoJsonParseException.class);
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withValidation(GeometryValidation.BASIC));
            assertThat(mapper.readValue(bowtie, Polygon.class).isValid()).isFalse();
        }

        @Test
        void booleanConstructorOverridesOnlyRangeValidation() throws IOException {
            String projected = "{\"type\":\"Point\",\"coordinates\":[13523000.5,3662000.2]}";
            ObjectMapper lenient = mapper(new GeometryJacksonModule(false));
            assertThat(lenient.readValue(projected, Point.class).getX()).isEqualTo(13523000.5);
            ObjectMapper strict = mapper(new GeometryJacksonModule(true));
            assertThatThrownBy(() -> strict.readValue(projected, Point.class))
                .isInstanceOf(InvalidCoordinateException.class);

            // a global range-validation change does not affect the explicit override ...
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinateRangeValidation(false));
            assertThatThrownBy(() -> strict.readValue(projected, Point.class))
                .isInstanceOf(InvalidCoordinateException.class);
            // ... but the other global options still apply
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withValidation(GeometryValidation.BASIC));
            String bowtie = "{\"type\":\"Polygon\",\"coordinates\":[[[0,0],[10,10],[10,0],[0,10],[0,0]]]}";
            assertThat(lenient.readValue(bowtie, Polygon.class)).isNotNull();
        }

        @Test
        void deserializerConstructors() {
            assertThat(new PointDeserializer().effectiveOptions()).isSameAs(GeoJsonOptions.getGlobal());
            assertThat(new PointDeserializer(true).effectiveOptions()).isSameAs(GeoJsonOptions.getGlobal());
            assertThat(new PointDeserializer(false).effectiveOptions().coordinateRangeValidation()).isFalse();
            GeoJsonOptions fixed = GeoJsonOptions.DEFAULTS.withValidation(GeometryValidation.NONE);
            assertThat(new PolygonDeserializer(fixed).effectiveOptions()).isSameAs(fixed);
            assertThat(new PolygonDeserializer((GeoJsonOptions) null).effectiveOptions())
                .isSameAs(GeoJsonOptions.getGlobal());
            assertThat(new GenericGeometryDeserializer(fixed).handledType()).isEqualTo(Geometry.class);
            assertThat(new LineStringDeserializer().handledType()).isEqualTo(LineString.class);
            assertThat(new MultiPointDeserializer(false).handledType()).isEqualTo(MultiPoint.class);
            assertThat(new MultiLineStringDeserializer(fixed).handledType()).isEqualTo(MultiLineString.class);
            assertThat(new MultiPolygonDeserializer(true).handledType()).isEqualTo(MultiPolygon.class);
            assertThat(new GeometryCollectionDeserializer(fixed).handledType()).isEqualTo(GeometryCollection.class);
        }

        @Test
        void serializerConstructors() {
            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(3));
            assertThat(new PointSerializer().effectivePrecision()).isEqualTo(3);
            assertThat(new PointSerializer(GeoJsonOptions.DEFAULTS).effectivePrecision()).isNull();
            assertThat(new PolygonSerializer(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(5)).effectivePrecision())
                .isEqualTo(5);
            assertThat(new LineStringSerializer().handledType()).isEqualTo(LineString.class);
            assertThat(new MultiPointSerializer().handledType()).isEqualTo(MultiPoint.class);
            assertThat(new MultiLineStringSerializer().handledType()).isEqualTo(MultiLineString.class);
            assertThat(new MultiPolygonSerializer().handledType()).isEqualTo(MultiPolygon.class);
            assertThat(new GeometryCollectionSerializer().handledType()).isEqualTo(GeometryCollection.class);
            assertThat(new GenericGeometrySerializer().handledType()).isEqualTo(Geometry.class);
        }
    }

    @Nested
    class Precision {

        @Test
        void annotationOnFieldsListsAndGeometryTypedFields() throws IOException {
            PrecisionDto dto = new PrecisionDto();
            dto.location = FACTORY.createPoint(new CoordinateXY(1.23456, 4.56789));
            dto.points = List.of(FACTORY.createPoint(new CoordinateXY(1.26, 2.24)),
                FACTORY.createPoint(new CoordinateXY(3.35, 4.45)));
            dto.shape = wkt("POLYGON ((0.12345 0.12345, 1.12345 0.12345, 1.12345 1.12345, 0.12345 0.12345))");
            dto.full = FACTORY.createPoint(new CoordinateXY(1.23456, 4.56789));
            dto.plain = FACTORY.createPoint(new CoordinateXY(1.23456, 4.56789));

            GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(4));
            String json = mapper().writeValueAsString(dto);
            assertThat(json).isEqualTo("{"
                + "\"location\":{\"type\":\"Point\",\"coordinates\":[1.23,4.57]},"
                + "\"points\":[{\"type\":\"Point\",\"coordinates\":[1.3,2.2]},"
                + "{\"type\":\"Point\",\"coordinates\":[3.4,4.5]}],"
                + "\"shape\":{\"type\":\"Polygon\",\"coordinates\":[[[0.123,0.123],[1.123,0.123],[1.123,1.123],"
                + "[0.123,0.123]]]},"
                + "\"full\":{\"type\":\"Point\",\"coordinates\":[1.23456,4.56789]},"
                + "\"plain\":{\"type\":\"Point\",\"coordinates\":[1.2346,4.5679]}}");
        }

        @Test
        void annotationOverridesExplicitModuleOptions() throws IOException {
            PrecisionDto dto = new PrecisionDto();
            dto.location = FACTORY.createPoint(new CoordinateXY(1.23456, 4.56789));
            dto.plain = dto.location;
            String json = mapper(new GeometryJacksonModule(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(0)))
                .writeValueAsString(dto);
            assertThat(json).contains("\"location\":{\"type\":\"Point\",\"coordinates\":[1.23,4.57]}")
                .contains("\"plain\":{\"type\":\"Point\",\"coordinates\":[1,5]}");
        }

        @Test
        void annotationOnGetterAndInBundle() throws IOException {
            GetterPrecisionDto getter = new GetterPrecisionDto();
            getter.setLocation(FACTORY.createPoint(new CoordinateXY(1.26, 2.24)));
            assertThat(mapper().writeValueAsString(getter))
                .isEqualTo("{\"location\":{\"type\":\"Point\",\"coordinates\":[1.3,2.2]}}");

            BundleDto bundle = new BundleDto();
            bundle.location = FACTORY.createPoint(new CoordinateXY(1.6, 2.4));
            assertThat(mapper().writeValueAsString(bundle))
                .isEqualTo("{\"location\":{\"type\":\"Point\",\"coordinates\":[2,2]}}");
        }

        @Test
        void annotationWorksWithExplicitSerializerWithoutModule() throws IOException {
            class Dto {
                @JsonSerialize(using = GenericGeometrySerializer.class)
                @GeoJsonPrecision(1)
                public Geometry geometry = wkt("LINESTRING (0.06 0.04, 1.16 1.149)");
            }
            assertThat(new ObjectMapper().writeValueAsString(new Dto()))
                .isEqualTo("{\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[0.1,0],[1.2,1.1]]}}");
        }

        @Test
        void invalidPrecisionIsADefinitionError() {
            BadPrecisionDto dto = new BadPrecisionDto();
            dto.location = FACTORY.createPoint(new CoordinateXY(1, 2));
            assertThatThrownBy(() -> mapper().writeValueAsString(dto))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("@GeoJsonPrecision");
        }

        @Test
        void withoutPropertyTheSerializerIsKept() throws JsonMappingException {
            PointSerializer serializer = new PointSerializer();
            assertThat(serializer.createContextual(null, null)).isSameAs(serializer);
        }

        @Test
        void contextualisationIsPerProperty() throws Exception {
            GeoJsonPrecision two = PrecisionDto.class.getField("location").getAnnotation(GeoJsonPrecision.class);
            com.fasterxml.jackson.databind.BeanProperty annotated =
                org.mockito.Mockito.mock(com.fasterxml.jackson.databind.BeanProperty.class);
            org.mockito.Mockito.when(annotated.getAnnotation(GeoJsonPrecision.class)).thenReturn(two);
            com.fasterxml.jackson.databind.BeanProperty plain =
                org.mockito.Mockito.mock(com.fasterxml.jackson.databind.BeanProperty.class);

            GeoJsonOptions options = GeoJsonOptions.DEFAULTS.withCoordinatePrecision(5);
            PointSerializer base = new PointSerializer(options);
            assertThat(base.createContextual(null, plain)).isSameAs(base);

            PointSerializer forAnnotated = (PointSerializer) base.createContextual(null, annotated);
            assertThat(forAnnotated).isNotSameAs(base);
            assertThat(forAnnotated.effectivePrecision()).isEqualTo(2);
            assertThat(forAnnotated.options()).isSameAs(options);
            assertThat(forAnnotated.createContextual(null, annotated)).isSameAs(forAnnotated);

            PointSerializer reused = (PointSerializer) forAnnotated.createContextual(null, plain);
            assertThat(reused.precisionOverride()).isNull();
            assertThat(reused.effectivePrecision()).isEqualTo(5);
        }
    }

    @Nested
    class DefaultTyping {

        private final PolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
            .allowIfBaseType(Object.class)
            .build();

        private ObjectMapper typed(boolean module, JsonTypeInfo.As inclusion) {
            ObjectMapper mapper = new ObjectMapper();
            if (module) {
                mapper.registerModule(new GeometryJacksonModule());
            }
            if (inclusion == null) {
                mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL);
            } else {
                mapper.activateDefaultTyping(validator, ObjectMapper.DefaultTyping.NON_FINAL, inclusion);
            }
            return mapper;
        }

        private AllTypes sample() {
            AllTypes dto = new AllTypes();
            dto.point = (Point) wkt("POINT (1 2)");
            dto.lineString = (LineString) wkt("LINESTRING (0 0, 1 1)");
            dto.polygon = (Polygon) wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");
            dto.multiPoint = (MultiPoint) wkt("MULTIPOINT ((1 2))");
            dto.multiLineString = (MultiLineString) wkt("MULTILINESTRING ((1 2, 3 4))");
            dto.multiPolygon = (MultiPolygon) wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))");
            dto.collection = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2))");
            dto.geometry = wkt("MULTIPOLYGON (((0 0, 1 0, 1 1, 0 0)))");
            dto.name = "typed";
            return dto;
        }

        private void assertSame(AllTypes back, AllTypes dto) {
            assertThat(back.point.equalsExact(dto.point)).isTrue();
            assertThat(back.lineString.equalsExact(dto.lineString)).isTrue();
            assertThat(back.polygon.equalsExact(dto.polygon)).isTrue();
            assertThat(back.multiPoint.equalsExact(dto.multiPoint)).isTrue();
            assertThat(back.multiLineString.equalsExact(dto.multiLineString)).isTrue();
            assertThat(back.multiPolygon.equalsExact(dto.multiPolygon)).isTrue();
            assertThat(back.collection.equalsExact(dto.collection)).isTrue();
            assertThat(back.geometry).isInstanceOf(MultiPolygon.class);
            assertThat(back.geometry.equalsExact(dto.geometry)).isTrue();
            assertThat(back.name).isEqualTo(dto.name);
        }

        @Test
        void propertyInclusionRoundTrip() throws IOException {
            ObjectMapper mapper = typed(true, JsonTypeInfo.As.PROPERTY);
            AllTypes dto = sample();
            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("\"point\":{\"@class\":\"org.locationtech.jts.geom.Point\",\"type\":\"Point\"")
                .contains("\"geometry\":{\"@class\":\"org.locationtech.jts.geom.MultiPolygon\","
                    + "\"type\":\"MultiPolygon\"");
            assertSame(mapper.readValue(json, AllTypes.class), dto);
        }

        @Test
        void propertyInclusionRootValues() throws IOException {
            ObjectMapper mapper = typed(true, JsonTypeInfo.As.PROPERTY);
            String json = mapper.writeValueAsString(wkt("POINT (1 2)"));
            assertThat(json).isEqualTo(
                "{\"@class\":\"org.locationtech.jts.geom.Point\",\"type\":\"Point\",\"coordinates\":[1.0,2.0]}");
            assertThat(mapper.readValue(json, Point.class).getX()).isEqualTo(1);
            assertThat(mapper.readValue(json, Geometry.class)).isInstanceOf(Point.class);
            // Object target: Jackson's type deserializer consumes "@class" and hands over mid-object
            assertThat(mapper.readValue(json, Object.class)).isInstanceOf(Point.class);
        }

        @Test
        void wrapperArrayInclusionRoundTrip() throws IOException {
            ObjectMapper mapper = typed(true, null);
            AllTypes dto = sample();
            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("\"point\":[\"org.locationtech.jts.geom.Point\",{\"type\":\"Point\"");
            assertSame(mapper.readValue(json, AllTypes.class), dto);
        }

        @Test
        void listOfGeometriesWithDefaultTyping() throws IOException {
            ObjectMapper mapper = typed(true, JsonTypeInfo.As.PROPERTY);
            ListDto dto = new ListDto();
            dto.points = new java.util.ArrayList<>(List.of((Point) wkt("POINT (1 2)"), (Point) wkt("POINT EMPTY")));
            ListDto back = mapper.readValue(mapper.writeValueAsString(dto), ListDto.class);
            assertThat(back.points).hasSize(2);
            assertThat(back.points.get(1).isEmpty()).isTrue();
        }

        @Test
        void annotationsWithoutModule() throws IOException {
            ObjectMapper mapper = typed(false, JsonTypeInfo.As.PROPERTY);
            AnnotatedDto dto = new AnnotatedDto();
            dto.geometry = wkt("POLYGON ((0 0, 1 0, 1 1, 0 0))");
            dto.collection = (GeometryCollection) wkt("GEOMETRYCOLLECTION (POINT (1 2))");
            dto.point = (Point) wkt("POINT (3 4)");
            String json = mapper.writeValueAsString(dto);
            assertThat(json).contains("\"@class\":\"org.locationtech.jts.geom.Polygon\"");
            AnnotatedDto back = mapper.readValue(json, AnnotatedDto.class);
            assertThat(back.geometry.equalsExact(dto.geometry)).isTrue();
            assertThat(back.collection.equalsExact(dto.collection)).isTrue();
            assertThat(back.point.equalsExact(dto.point)).isTrue();
        }
    }
}
