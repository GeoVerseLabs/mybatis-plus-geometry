package io.github.geoverselabs.mybatis.geometry.stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MappingJsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.github.geoverselabs.mybatis.geometry.jackson.GeoJsonOptions;
import io.github.geoverselabs.mybatis.geometry.jackson.GeometryJacksonModule;
import org.apache.ibatis.cursor.Cursor;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class GeoJsonStreamsTest {

    private static final String RS = "\u001E";

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private static final String POINT_1_2 = "{\"type\":\"Point\",\"coordinates\":[1.0,2.0]}";

    private static final String POINT_3_4 = "{\"type\":\"Point\",\"coordinates\":[3.5,-4.25]}";

    private static final List<Shop> SHOPS = List.of(
        new Shop(1L, "a", point(1, 2)),
        new Shop("b-2", "b", point(3.5, -4.25)));

    private static final String SHOP_FEATURE_1 =
        "{\"type\":\"Feature\",\"id\":1,\"geometry\":" + POINT_1_2 + ",\"properties\":{\"name\":\"a\"}}";

    private static final String SHOP_FEATURE_2 =
        "{\"type\":\"Feature\",\"id\":\"b-2\",\"geometry\":" + POINT_3_4 + ",\"properties\":{\"name\":\"b\"}}";

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new GeometryJacksonModule());

    // ------------------------------------------------------------------------------------------
    // Behaviour shared by every method

    @FunctionalInterface
    interface PointWriter {
        long write(ObjectMapper mapper, Iterable<Point> points, OutputStream out) throws IOException;
    }

    static Stream<Arguments> writers() {
        Function<Point, Object> properties = p -> Map.of("x", p.getX());
        return Stream.of(
            writer("writeArray", GeoJsonStreams::writeArray),
            writer("writeSequence", GeoJsonStreams::writeSequence),
            writer("writeFeatureCollection",
                (m, items, out) -> GeoJsonStreams.writeFeatureCollection(m, items, p -> p, properties, out)),
            writer("writeFeatureCollection with id",
                (m, items, out) -> GeoJsonStreams.writeFeatureCollection(m, items, p -> 7, p -> p, properties, out)),
            writer("writeFeatureSequence",
                (m, items, out) -> GeoJsonStreams.writeFeatureSequence(m, items, p -> p, properties, out)),
            writer("writeFeatureSequence with id",
                (m, items, out) -> GeoJsonStreams.writeFeatureSequence(m, items, p -> 7, p -> p, properties, out)));
    }

    private static Arguments writer(String name, PointWriter writer) {
        return arguments(named(name, writer));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void returnsTheNumberOfItemsFlushesOnceAtTheEndAndNeverCloses(PointWriter writer) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        long count = writer.write(mapper, List.of(point(1, 2), point(3, 4), point(5, 6)), out);

        assertThat(count).isEqualTo(3);
        assertThat(out.closed).isFalse();
        assertThat(out.flushes).isEqualTo(1);
        assertThat(out.sizeAtFirstFlush).isPositive().isEqualTo(out.size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void emptyInputCountsZeroAndStillFlushes(PointWriter writer) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();

        long count = writer.write(mapper, Collections.emptyList(), out);

        assertThat(count).isZero();
        assertThat(out.closed).isFalse();
        assertThat(out.flushes).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void writesToTheStreamInLargeChunksRatherThanPerItem(PointWriter writer) throws IOException {
        int total = 10_000;
        List<Point> points = IntStream.range(0, total).mapToObj(i -> point(i, i)).toList();
        RecordingOutputStream out = new RecordingOutputStream();

        assertThat(writer.write(mapper, points, out)).isEqualTo(total);

        assertThat(out.size()).isGreaterThan(total * 30);
        assertThat(out.writes).as("write calls for %s bytes", out.size()).isLessThan(out.size() / 1_000);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void consumesItemsLazilyThroughASingleIterator(PointWriter writer) throws IOException {
        List<String> log = new ArrayList<>();
        ObjectMapper logging = new ObjectMapper()
            .registerModule(new SimpleModule().addSerializer(Point.class, new LoggingPointSerializer(log)));
        LoggingIterable<Point> items = new LoggingIterable<>(List.of(point(1, 1), point(2, 2), point(3, 3)), log);

        long count = writer.write(logging, items, new RecordingOutputStream());

        // each item is written before the next one is requested: nothing is materialised
        assertThat(log).containsExactly("next 1", "write 1", "next 2", "write 2", "next 3", "write 3");
        assertThat(items.iterators).isEqualTo(1);
        assertThat(count).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void consumesAMyBatisCursorWithoutClosingIt(PointWriter writer) throws IOException {
        RecordingOutputStream out = new RecordingOutputStream();
        try (FakeCursor<Point> cursor = new FakeCursor<>(List.of(point(1, 2), point(3, 4)))) {
            long count = writer.write(mapper, cursor, out);

            assertThat(count).isEqualTo(2);
            assertThat(cursor.isConsumed()).isTrue();
            assertThat(cursor.isOpen()).as("the caller closes the cursor").isTrue();
        }
        assertThat(out.closed).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void serialisesGeometriesWithTheMappersSerializer(PointWriter writer) throws IOException {
        ObjectMapper rounding = new ObjectMapper()
            .registerModule(new SimpleModule().addSerializer(Point.class, new RoundingPointSerializer(3)));
        RecordingOutputStream out = new RecordingOutputStream();

        writer.write(rounding, List.of(point(1.23456789, 2.98765)), out);

        assertThat(out.text()).contains("\"coordinates\":[1.235,2.988]");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void appliesTheGeometryModuleCoordinatePrecision(PointWriter writer) throws Exception {
        Constructor<GeometryJacksonModule> withOptions = moduleConstructorWithOptions();
        assumeTrue(withOptions != null, "GeometryJacksonModule(GeoJsonOptions) is not available in this build");
        Point point = point(1.23456789, 2.98765);

        ObjectMapper explicit = new ObjectMapper()
            .registerModule(withOptions.newInstance(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(3)));
        RecordingOutputStream out = new RecordingOutputStream();
        writer.write(explicit, List.of(point), out);
        assertThat(coordinates(out.text())).containsExactly(1.235, 2.988);

        GeoJsonOptions.setGlobal(GeoJsonOptions.DEFAULTS.withCoordinatePrecision(2));
        try {
            ObjectMapper global = new ObjectMapper().registerModule(new GeometryJacksonModule());
            out = new RecordingOutputStream();
            writer.write(global, List.of(point), out);
            assertThat(coordinates(out.text())).containsExactly(1.23, 2.99);
        } finally {
            GeoJsonOptions.resetGlobal();
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void propagatesAnIterationFailureUnchangedAndDiscardsBufferedOutput(PointWriter writer) {
        IllegalStateException failure = new IllegalStateException("cursor failed");
        Iterable<Point> failing = () -> new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public Point next() {
                if (++index == 3) {
                    throw failure;
                }
                return point(index, index);
            }
        };
        RecordingOutputStream out = new RecordingOutputStream();

        assertThatThrownBy(() -> writer.write(mapper, failing, out)).isSameAs(failure);

        assertThat(out.size()).as("buffered output is discarded").isZero();
        assertThat(out.flushes).isZero();
        assertThat(out.closed).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void propagatesAStreamFailureUnchangedWithoutClosingTheStream(PointWriter writer) {
        IOException failure = new IOException("broken pipe");
        BrokenOutputStream out = new BrokenOutputStream(failure);

        assertThatThrownBy(() -> writer.write(mapper, List.of(point(1, 2)), out)).isSameAs(failure);

        assertThat(out.closed).isFalse();
        assertThat(out.flushes).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void rejectsAMapperThatDoesNotWriteJsonBeforeConsumingItems(PointWriter writer) {
        ObjectMapper yaml = new ObjectMapper(new JsonFactory() {
            @Override
            public String getFormatName() {
                return "YAML";
            }
        });
        LoggingIterable<Point> items = new LoggingIterable<>(List.of(point(1, 2)), new ArrayList<>());
        RecordingOutputStream out = new RecordingOutputStream();

        assertThatIllegalArgumentException()
            .isThrownBy(() -> writer.write(yaml, items, out))
            .withMessageContaining("YAML");

        assertThat(items.iterators).isZero();
        assertThat(out.size()).isZero();
        assertThat(out.flushes).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void acceptsJsonFactorySubclasses(PointWriter writer) throws IOException {
        // A JsonFactory subclass that does not override getFormatName() reports null
        ObjectMapper custom = new ObjectMapper(new JsonFactory() { }).registerModule(new GeometryJacksonModule());
        ObjectMapper mapping = new ObjectMapper(new MappingJsonFactory()).registerModule(new GeometryJacksonModule());

        assertThat(writer.write(custom, List.of(point(1, 2)), new RecordingOutputStream())).isEqualTo(1);
        assertThat(writer.write(mapping, List.of(point(1, 2)), new RecordingOutputStream())).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writers")
    void rejectsNullArguments(PointWriter writer) {
        List<Point> items = List.of(point(1, 2));
        OutputStream out = new RecordingOutputStream();

        assertThatNullPointerException().isThrownBy(() -> writer.write(null, items, out)).withMessage("mapper");
        assertThatNullPointerException().isThrownBy(() -> writer.write(mapper, null, out)).withMessage("items");
        assertThatNullPointerException().isThrownBy(() -> writer.write(mapper, items, null)).withMessage("out");
    }

    @Test
    void featureMethodsRejectNullFunctions() {
        List<Shop> items = SHOPS;
        OutputStream out = new RecordingOutputStream();
        Function<Shop, Object> id = Shop::id;
        Function<Shop, Point> geometry = Shop::location;
        Function<Shop, Object> properties = Shop::name;

        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureCollection(mapper, items, null, properties, out))
            .withMessage("geometry");
        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureCollection(mapper, items, geometry, null, out))
            .withMessage("properties");
        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureCollection(mapper, items, null, geometry, properties, out))
            .withMessage("id");
        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureSequence(mapper, items, null, properties, out))
            .withMessage("geometry");
        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureSequence(mapper, items, geometry, null, out))
            .withMessage("properties");
        assertThatNullPointerException()
            .isThrownBy(() -> GeoJsonStreams.writeFeatureSequence(mapper, items, null, geometry, properties, out))
            .withMessage("id");
    }

    // ------------------------------------------------------------------------------------------

    @Nested
    class WriteArray {

        @Test
        void writesAJsonArray() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeArray(mapper, List.of(point(1, 2), point(3.5, -4.25)), out);

            assertThat(count).isEqualTo(2);
            assertThat(out.text()).isEqualTo("[" + POINT_1_2 + "," + POINT_3_4 + "]");
        }

        @Test
        void writesAnEmptyArrayForNoItems() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            assertThat(GeoJsonStreams.writeArray(mapper, List.of(), out)).isZero();

            assertThat(out.text()).isEqualTo("[]");
        }

        @Test
        void serialisesEachItemAsWriteValueWould() throws IOException {
            List<Object> items = Arrays.asList("text", 42, null, Map.of("k", true), new Label("x"), point(1, 2));
            RecordingOutputStream out = new RecordingOutputStream();

            assertThat(GeoJsonStreams.writeArray(mapper, items, out)).isEqualTo(6);

            assertThat(out.text()).isEqualTo(mapper.writeValueAsString(items));
        }

        @Test
        void indentsWhenTheMapperIndents() throws IOException {
            ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            List<Object> items = Arrays.asList(Map.of("k", 1), null, point(1, 2));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeArray(pretty, items, out);

            assertThat(out.text()).contains("\n").isEqualTo(pretty.writeValueAsString(items));
        }

        @Test
        void appliesDefaultTypingToItemsAsWriteValueWould() throws IOException {
            ObjectMapper typed = withDefaultTyping(new ObjectMapper());
            List<Label> items = List.of(new Label("a"), new Label("b"));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeArray(typed, items, out);

            String label = typed.writeValueAsString(new Label("a"));
            assertThat(label).contains("@class");
            assertThat(out.text()).isEqualTo("[" + label + "," + typed.writeValueAsString(new Label("b")) + "]");
        }

        @Test
        void writesToTheStreamWhileItemsAreStillBeingProduced() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();
            int total = 100_000;
            int[] sizeHalfway = {-1};
            Iterable<Point> items = () -> IntStream.range(0, total).mapToObj(i -> {
                if (i == total / 2) {
                    sizeHalfway[0] = out.size();
                }
                return point(i, -i);
            }).iterator();

            assertThat(GeoJsonStreams.writeArray(mapper, items, out)).isEqualTo(total);

            assertThat(sizeHalfway[0]).as("bytes written before the last item was produced").isPositive();
            assertThat(out.flushes).isEqualTo(1);
            assertThat(mapper.readTree(out.toByteArray()).size()).isEqualTo(total);
        }
    }

    @Nested
    class WriteSequence {

        @Test
        void writesOneRecordPerItem() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeSequence(mapper, Arrays.asList(point(1, 2), "text", null), out);

            assertThat(count).isEqualTo(3);
            assertThat(out.toByteArray()).isEqualTo(
                (RS + POINT_1_2 + "\n" + RS + "\"text\"\n" + RS + "null\n").getBytes(StandardCharsets.UTF_8));
            assertThat(out.toByteArray()[0]).isEqualTo((byte) 0x1E);
            assertThat(out.toByteArray()[out.size() - 1]).isEqualTo((byte) 0x0A);
        }

        @Test
        void writesNothingForNoItems() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            assertThat(GeoJsonStreams.writeSequence(mapper, List.of(), out)).isZero();

            assertThat(out.toByteArray()).isEmpty();
            assertThat(out.flushes).isEqualTo(1);
        }

        @Test
        void serialisesEachRecordAsWriteValueWould() throws IOException {
            ObjectMapper typed = withDefaultTyping(new ObjectMapper());
            List<Object> items = Arrays.asList(new Label("a"), Map.of("k", 1), "text", 1.5, null);
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeSequence(typed, items, out);

            StringBuilder expected = new StringBuilder();
            for (Object item : items) {
                expected.append(RS).append(typed.writeValueAsString(item)).append('\n');
            }
            assertThat(out.text()).isEqualTo(expected.toString()).contains("@class");
        }

        @Test
        void neverIndentsRecords() throws IOException {
            ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            List<Object> items = List.of(Map.of("k", List.of(1, 2)), point(1, 2));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeSequence(pretty, items, out);

            assertThat(out.text()).isEqualTo(
                RS + "{\"k\":[1,2]}\n" + RS + POINT_1_2 + "\n");
        }

        @Test
        void escapesSeparatorsInsideStrings() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeSequence(mapper, List.of("a" + RS + "b\nc"), out);

            assertThat(out.text()).isEqualTo(RS + "\"a\\u001Eb\\nc\"\n");
        }
    }

    @Nested
    class WriteFeatureCollection {

        @Test
        void writesAFeatureCollectionWithIds() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeFeatureCollection(mapper, SHOPS,
                Shop::id, Shop::location, shop -> Map.of("name", shop.name()), out);

            assertThat(count).isEqualTo(2);
            assertThat(out.text()).isEqualTo(
                "{\"type\":\"FeatureCollection\",\"features\":[" + SHOP_FEATURE_1 + "," + SHOP_FEATURE_2 + "]}");
        }

        @Test
        void writesFeaturesWithoutIds() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeFeatureCollection(mapper, SHOPS.subList(0, 1),
                Shop::location, shop -> Map.of("name", shop.name()), out);

            assertThat(count).isEqualTo(1);
            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":" + POINT_1_2 + ",\"properties\":{\"name\":\"a\"}}]}");
        }

        @Test
        void writesAnEmptyFeatureCollectionForNoItems() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            assertThat(GeoJsonStreams.writeFeatureCollection(mapper, List.<Shop>of(),
                Shop::id, Shop::location, Shop::name, out)).isZero();

            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":[]}");
        }

        @Test
        void omitsNullIdsAndWritesNullGeometryAndProperties() throws IOException {
            List<Shop> shops = List.of(
                new Shop(null, "no id", point(1, 2)),
                new Shop(2, null, null));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, shops, Shop::id, Shop::location,
                shop -> shop.name() == null ? null : Map.of("name", shop.name()), out);

            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":" + POINT_1_2 + ",\"properties\":{\"name\":\"no id\"}},"
                + "{\"type\":\"Feature\",\"id\":2,\"geometry\":null,\"properties\":null}]}");
        }

        @Test
        void passesNullItemsToTheFunctions() throws IOException {
            List<String> seen = new ArrayList<>();
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeFeatureCollection(mapper, Collections.<Shop>singletonList(null),
                shop -> {
                    seen.add("id " + shop);
                    return null;
                },
                shop -> {
                    seen.add("geometry " + shop);
                    return null;
                },
                shop -> {
                    seen.add("properties " + shop);
                    return null;
                }, out);

            assertThat(count).isEqualTo(1);
            assertThat(seen).containsExactly("id null", "geometry null", "properties null");
            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":null}]}");
        }

        @Test
        void callsEachFunctionOncePerItemInOrderWhileIterating() throws IOException {
            List<String> log = new ArrayList<>();
            LoggingIterable<Shop> items = new LoggingIterable<>(SHOPS, log);

            GeoJsonStreams.writeFeatureCollection(mapper, items,
                shop -> {
                    log.add("id " + shop.name());
                    return shop.id();
                },
                shop -> {
                    log.add("geometry " + shop.name());
                    return shop.location();
                },
                shop -> {
                    log.add("properties " + shop.name());
                    return Map.of("name", shop.name());
                }, new RecordingOutputStream());

            assertThat(log).containsExactly(
                "next " + SHOPS.get(0), "id a", "geometry a", "properties a",
                "next " + SHOPS.get(1), "id b", "geometry b", "properties b");
        }

        @Test
        void propagatesAFunctionFailureUnchanged() {
            IllegalStateException failure = new IllegalStateException("lazy load failed");
            RecordingOutputStream out = new RecordingOutputStream();

            assertThatThrownBy(() -> GeoJsonStreams.writeFeatureCollection(mapper, SHOPS,
                Shop::location, shop -> {
                    throw failure;
                }, out)).isSameAs(failure);

            assertThat(out.size()).isZero();
            assertThat(out.flushes).isZero();
            assertThat(out.closed).isFalse();
        }

        @Test
        void writesFeatureMembersRegardlessOfTheMapperConfiguration() throws IOException {
            ObjectMapper configured = mapper.copy()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(configured, List.of(new Shop(1, "a", null)),
                Shop::id, Shop::location, shop -> new ShopProperties(shop.name(), null), out);

            // the mapper configuration applies to the member values only
            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"id\":1,\"geometry\":null,\"properties\":{\"shop_name\":\"a\"}}]}");
        }

        @Test
        void writesPlainGeoJsonWhenTheMapperUsesDefaultTyping() throws IOException {
            ObjectMapper typed = withDefaultTyping(new ObjectMapper().registerModule(new GeometryJacksonModule()));
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("name", "a");
            properties.put("rank", 3);
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(typed, List.of(SHOPS.get(0)),
                Shop::id, Shop::location, shop -> properties, out);

            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"id\":1,\"geometry\":" + POINT_1_2
                + ",\"properties\":{\"name\":\"a\",\"rank\":3}}]}");
        }

        @Test
        void acceptsTreeAndBeanProperties() throws IOException {
            ObjectNode tree = JsonNodeFactory.instance.objectNode().put("name", "tree");
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, List.<Object>of(tree, new ShopProperties("bean", "n")),
                item -> null, item -> item, out);

            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{\"name\":\"tree\"}},"
                + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{\"shopName\":\"bean\",\"note\":\"n\"}}]}");
        }

        @Test
        void acceptsAnyGeometryType() throws IOException {
            Polygon square = GEOMETRY_FACTORY.createPolygon(new Coordinate[] {
                new Coordinate(0, 0), new Coordinate(1, 0), new Coordinate(1, 1), new Coordinate(0, 0)});
            List<Geometry> geometries = List.of(square, point(1, 2));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, geometries, g -> g, g -> null, out);

            JsonNode features = mapper.readTree(out.toByteArray()).get("features");
            assertThat(features.get(0).get("geometry").get("type").asText()).isEqualTo("Polygon");
            assertThat(features.get(1).get("geometry").toString()).isEqualTo(POINT_1_2);
        }

        @Test
        void failsClearlyWhenTheMapperHasNoGeoJsonSerializer() {
            ObjectMapper plain = new ObjectMapper();
            RecordingOutputStream out = new RecordingOutputStream();

            assertThatThrownBy(() -> GeoJsonStreams.writeFeatureCollection(plain, SHOPS,
                Shop::location, shop -> Map.of("name", shop.name()), out))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("No GeoJSON serializer registered for org.locationtech.jts.geom.Point")
                .hasMessageContaining("GeometryJacksonModule");

            assertThat(out.size()).isZero();
            assertThat(out.flushes).isZero();
        }

        @Test
        void doesNotNeedTheGeometryModuleWithoutGeometries() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(new ObjectMapper(), List.of("a"), s -> null, s -> Map.of("n", s), out);

            assertThat(out.text()).isEqualTo("{\"type\":\"FeatureCollection\",\"features\":["
                + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":{\"n\":\"a\"}}]}");
        }

        @Test
        void indentsWhenTheMapperIndents() throws IOException {
            ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            RecordingOutputStream compact = new RecordingOutputStream();
            RecordingOutputStream indented = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, SHOPS, Shop::id, Shop::location,
                shop -> Map.of("name", shop.name()), compact);
            GeoJsonStreams.writeFeatureCollection(pretty, SHOPS, Shop::id, Shop::location,
                shop -> Map.of("name", shop.name()), indented);

            assertThat(indented.text()).contains("\n  \"features\" : [");
            assertThat(mapper.readTree(indented.toByteArray())).isEqualTo(mapper.readTree(compact.toByteArray()));
        }

        @Test
        void writesUtf8() throws IOException {
            String name = "Zürich 北京";
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, List.of(name), s -> null, s -> Map.of("name", s), out);

            assertThat(out.text()).contains("\"name\":\"" + name + "\"");
            assertThat(out.toByteArray()).containsSequence(name.getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void leavesATruncatedDocumentWhenFailingAfterOutputReachedTheStream() {
            RuntimeException failure = new RuntimeException("connection lost");
            Iterable<Point> items = () -> IntStream.range(0, 100_000).mapToObj(i -> {
                if (i == 50_000) {
                    throw failure;
                }
                return point(i, i);
            }).iterator();
            RecordingOutputStream out = new RecordingOutputStream();

            assertThatThrownBy(() -> GeoJsonStreams.writeFeatureCollection(mapper, items,
                p -> p, p -> null, out)).isSameAs(failure);

            assertThat(out.size()).as("earlier output reached the stream").isPositive();
            assertThat(out.flushes).isZero();
            assertThat(out.closed).isFalse();
            // the open array and object are not closed, so the failure cannot pass for a complete result
            assertThatThrownBy(() -> mapper.readTree(out.toByteArray()))
                .isInstanceOf(JsonProcessingException.class);
        }
    }

    @Nested
    class WriteFeatureSequence {

        @Test
        void writesOneFeatureRecordPerItem() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            long count = GeoJsonStreams.writeFeatureSequence(mapper, SHOPS,
                Shop::id, Shop::location, shop -> Map.of("name", shop.name()), out);

            assertThat(count).isEqualTo(2);
            assertThat(out.toByteArray()).isEqualTo(
                (RS + SHOP_FEATURE_1 + "\n" + RS + SHOP_FEATURE_2 + "\n").getBytes(StandardCharsets.UTF_8));
        }

        @Test
        void writesFeaturesWithoutIds() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureSequence(mapper, Arrays.asList(SHOPS.get(0), null),
                shop -> shop == null ? null : shop.location(), shop -> null, out);

            assertThat(out.text()).isEqualTo(
                RS + "{\"type\":\"Feature\",\"geometry\":" + POINT_1_2 + ",\"properties\":null}\n"
                    + RS + "{\"type\":\"Feature\",\"geometry\":null,\"properties\":null}\n");
        }

        @Test
        void writesNothingForNoItems() throws IOException {
            RecordingOutputStream out = new RecordingOutputStream();

            assertThat(GeoJsonStreams.writeFeatureSequence(mapper, List.<Shop>of(),
                Shop::id, Shop::location, Shop::name, out)).isZero();

            assertThat(out.toByteArray()).isEmpty();
            assertThat(out.flushes).isEqualTo(1);
        }

        @Test
        void writesTheSameFeaturesAsTheFeatureCollection() throws IOException {
            List<Shop> shops = List.of(SHOPS.get(0), new Shop(null, "x", null), SHOPS.get(1));
            Function<Shop, Object> properties = shop -> Map.of("name", shop.name());
            RecordingOutputStream collection = new RecordingOutputStream();
            RecordingOutputStream sequence = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureCollection(mapper, shops, Shop::id, Shop::location, properties, collection);
            GeoJsonStreams.writeFeatureSequence(mapper, shops, Shop::id, Shop::location, properties, sequence);

            JsonNode features = mapper.readTree(collection.toByteArray()).get("features");
            List<String> records = records(sequence.toByteArray());
            assertThat(records).hasSize(features.size());
            for (int i = 0; i < records.size(); i++) {
                assertThat(records.get(i)).isEqualTo(features.get(i).toString());
            }
        }

        @Test
        void neverIndentsRecords() throws IOException {
            ObjectMapper pretty = mapper.copy().enable(SerializationFeature.INDENT_OUTPUT);
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureSequence(pretty, SHOPS,
                Shop::id, Shop::location, shop -> Map.of("name", shop.name()), out);

            assertThat(out.text()).isEqualTo(RS + SHOP_FEATURE_1 + "\n" + RS + SHOP_FEATURE_2 + "\n");
        }

        @Test
        void writesPlainGeoJsonWhenTheMapperUsesDefaultTyping() throws IOException {
            ObjectMapper typed = withDefaultTyping(new ObjectMapper().registerModule(new GeometryJacksonModule()));
            RecordingOutputStream out = new RecordingOutputStream();

            GeoJsonStreams.writeFeatureSequence(typed, SHOPS,
                Shop::id, Shop::location, shop -> Collections.singletonMap("name", shop.name()), out);

            assertThat(out.text()).isEqualTo(RS + SHOP_FEATURE_1 + "\n" + RS + SHOP_FEATURE_2 + "\n");
        }

        @Test
        void failsClearlyWhenTheMapperHasNoGeoJsonSerializer() {
            RecordingOutputStream out = new RecordingOutputStream();

            assertThatThrownBy(() -> GeoJsonStreams.writeFeatureSequence(new ObjectMapper(), SHOPS,
                Shop::location, shop -> null, out))
                .isInstanceOf(InvalidDefinitionException.class)
                .hasMessageContaining("GeometryJacksonModule");

            assertThat(out.size()).isZero();
        }
    }

    // ------------------------------------------------------------------------------------------
    // Helpers

    private static Point point(double x, double y) {
        return GEOMETRY_FACTORY.createPoint(new Coordinate(x, y));
    }

    private static ObjectMapper withDefaultTyping(ObjectMapper mapper) {
        return mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
            ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    }

    /** Split an RFC 7464 text sequence into its JSON texts, checking the framing. */
    static List<String> records(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        List<String> records = new ArrayList<>();
        if (text.isEmpty()) {
            return records;
        }
        assertThat(text).startsWith(RS).endsWith("\n");
        for (String record : text.substring(1).split(RS, -1)) {
            assertThat(record).endsWith("\n");
            records.add(record.substring(0, record.length() - 1));
        }
        return records;
    }

    private static final Pattern COORDINATES = Pattern.compile("\"coordinates\":\\[([^\\]]*)]");

    private static List<Double> coordinates(String json) {
        Matcher matcher = COORDINATES.matcher(json);
        assertThat(matcher.find()).as("coordinates in %s", json).isTrue();
        List<Double> ordinates = new ArrayList<>();
        for (String ordinate : matcher.group(1).split(",")) {
            ordinates.add(Double.parseDouble(ordinate));
        }
        return ordinates;
    }

    /**
     * The {@code GeometryJacksonModule(GeoJsonOptions)} constructor, which the GeoJSON slice adds;
     * null while it is not part of the build.
     */
    private static Constructor<GeometryJacksonModule> moduleConstructorWithOptions() {
        try {
            return GeometryJacksonModule.class.getConstructor(GeoJsonOptions.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    record Shop(Object id, String name, Point location) {
    }

    /** A non-final bean, so that default typing adds type ids to it. */
    public static class Label {

        private final String text;

        public Label(String text) {
            this.text = text;
        }

        public String getText() {
            return text;
        }
    }

    public static class ShopProperties {

        private final String shopName;

        private final String note;

        public ShopProperties(String shopName, String note) {
            this.shopName = shopName;
            this.note = note;
        }

        public String getShopName() {
            return shopName;
        }

        public String getNote() {
            return note;
        }
    }

    /** Records how the caller's stream is used. */
    static final class RecordingOutputStream extends ByteArrayOutputStream {

        int writes;

        int flushes;

        int sizeAtFirstFlush = -1;

        boolean closed;

        @Override
        public synchronized void write(int b) {
            writes++;
            super.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            writes++;
            super.write(b, off, len);
        }

        @Override
        public void flush() {
            if (flushes++ == 0) {
                sizeAtFirstFlush = size();
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        String text() {
            return toString(StandardCharsets.UTF_8);
        }
    }

    static final class BrokenOutputStream extends OutputStream {

        private final IOException failure;

        int flushes;

        boolean closed;

        BrokenOutputStream(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void write(int b) throws IOException {
            throw failure;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            throw failure;
        }

        @Override
        public void flush() {
            flushes++;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** An iterable that may be iterated only once and logs each item it hands out. */
    static final class LoggingIterable<T> implements Iterable<T> {

        private final List<T> items;

        private final List<String> log;

        int iterators;

        LoggingIterable(List<T> items, List<String> log) {
            this.items = items;
            this.log = log;
        }

        @Override
        public Iterator<T> iterator() {
            if (iterators++ > 0) {
                throw new IllegalStateException("iterated twice");
            }
            Iterator<T> delegate = items.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public T next() {
                    T item = delegate.next();
                    log.add("next " + (item instanceof Point p ? String.valueOf((int) p.getX()) : item));
                    return item;
                }
            };
        }
    }

    /** Behaves like MyBatis' DefaultCursor: a single iterator, closed by its owner. */
    static final class FakeCursor<T> implements Cursor<T> {

        private final List<T> rows;

        private boolean iterated;

        private boolean consumed;

        private boolean closed;

        private int index = -1;

        FakeCursor(List<T> rows) {
            this.rows = rows;
        }

        @Override
        public boolean isOpen() {
            return !closed;
        }

        @Override
        public boolean isConsumed() {
            return consumed;
        }

        @Override
        public int getCurrentIndex() {
            return index;
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public Iterator<T> iterator() {
            if (iterated) {
                throw new IllegalStateException("Cannot open more than one iterator on a Cursor");
            }
            iterated = true;
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    if (closed || index + 1 >= rows.size()) {
                        consumed = true;
                        return false;
                    }
                    return true;
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return rows.get(++index);
                }
            };
        }
    }

    /** Writes Points like GeometryJacksonModule and logs each one. */
    static final class LoggingPointSerializer extends StdSerializer<Point> {

        private final List<String> log;

        LoggingPointSerializer(List<String> log) {
            super(Point.class);
            this.log = log;
        }

        @Override
        public void serialize(Point point, JsonGenerator gen, SerializerProvider provider) throws IOException {
            log.add("write " + (int) point.getX());
            gen.writeStartObject();
            gen.writeStringField("type", "Point");
            gen.writeArrayFieldStart("coordinates");
            gen.writeNumber(point.getX());
            gen.writeNumber(point.getY());
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }

    /** Writes Points with a fixed number of decimals, like a precision option would. */
    static final class RoundingPointSerializer extends StdSerializer<Point> {

        private final int decimals;

        RoundingPointSerializer(int decimals) {
            super(Point.class);
            this.decimals = decimals;
        }

        @Override
        public void serialize(Point point, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "Point");
            gen.writeArrayFieldStart("coordinates");
            gen.writeNumber(BigDecimal.valueOf(point.getX()).setScale(decimals, RoundingMode.HALF_UP));
            gen.writeNumber(BigDecimal.valueOf(point.getY()).setScale(decimals, RoundingMode.HALF_UP));
            gen.writeEndArray();
            gen.writeEndObject();
        }
    }
}
